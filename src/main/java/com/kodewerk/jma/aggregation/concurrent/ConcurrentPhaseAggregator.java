package com.kodewerk.jma.aggregation.concurrent;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCConcurrentEvent;
import com.kodewerk.gcsee.event.generational.CMSConcurrentEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;

/**
 * Listens at the abstract {@code *ConcurrentEvent} parents in each
 * collector family so every concurrent subtype reaches us through one
 * handler per family. We label each sample by the event's runtime
 * simple class name — {@code G1ConcurrentMark},
 * {@code ConcurrentSweep}, etc. — which becomes the series key in
 * {@link ConcurrentPhaseAggregation}.
 * <p>
 * ZGC concurrent phases live inside a {@code ZGCCollection} (same
 * event the pause-time chart pulls sub-pauses from) and have a
 * different model — those would need a dedicated handler if we want
 * to surface them on this chart later.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL})
public final class ConcurrentPhaseAggregator extends Aggregator<ConcurrentPhaseAggregation> {

    private static final double SECONDS_TO_MILLIS = 1000.0;

    public ConcurrentPhaseAggregator(ConcurrentPhaseAggregation aggregation) {
        super(aggregation);
        register(G1GCConcurrentEvent.class, this::onConcurrent);
        register(CMSConcurrentEvent.class,  this::onConcurrent);
    }

    private void onConcurrent(JVMEvent event) {
        if (event.getDateTimeStamp() == null) return;
        double durationSec = event.getDuration();
        if (durationSec <= 0.0) return;
        String phase = event.getClass().getSimpleName();
        double tSec = event.getDateTimeStamp().toSeconds();
        aggregation().recordPhase(phase, tSec, durationSec * SECONDS_TO_MILLIS);
    }
}
