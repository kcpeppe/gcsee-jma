package com.kodewerk.jma.analytics;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.GCEvent;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;

/**
 * Single aggregator backing {@link AnalyticsAggregation}. Listens on
 * every collector family that emits {@link GCEvent} so cross-collector
 * analytics (System.gc() detection works the same way for ParNew,
 * G1, ZGC) sit inside one event subscription.
 * <p>
 * The aggregator stays trivially thin — the moment an event arrives we
 * push it through to the aggregation, which decides what to keep and
 * what to drop. Per-group analytic logic lives in classes under
 * {@code com.kodewerk.jma.analytics.<group>} and runs inside
 * {@link AnalyticsAggregation#getData()}.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC, EventSource.SURVIVOR})
public final class AnalyticsAggregator extends Aggregator<AnalyticsAggregation> {

    public AnalyticsAggregator(AnalyticsAggregation aggregation) {
        super(aggregation);
        // Only two registrations on purpose. GCSee's JVMEventDispatcher
        // walks the class hierarchy and fires the most-specific matching
        // handler — registering GCEvent.class plus a more specific class
        // (e.g. G1Young) would silently route those events away from the
        // GCEvent handler. Per-subtype dispatch lives inside
        // AnalyticsAggregation.recordCollection via instanceof, where
        // every check sees every event.
        register(GCEvent.class,        this::onCollection);
        register(SurvivorRecord.class, this::onSurvivorRecord);
    }

    private void onCollection(GCEvent event) {
        if (event.getDateTimeStamp() == null) return;
        aggregation().recordCollection(event.getDateTimeStamp().toSeconds(), event);
    }

    private void onSurvivorRecord(SurvivorRecord record) {
        aggregation().recordSurvivorRecord(record);
    }
}
