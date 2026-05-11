package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;

/**
 * Feeds {@link ZgcCycleDurationsAndIntervalsAggregation}. Tracks the
 * previous cycle's start timestamp so each new cycle can emit an
 * interval point in addition to its duration. Intervals are start-to-
 * start (matches the Linux-style cadence reading the operator
 * expects); the first cycle has no predecessor and contributes only
 * the duration.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcCycleDurationsAndIntervalsAggregator
        extends Aggregator<ZgcCycleDurationsAndIntervalsAggregation> {

    private double prevCycleStartSec = Double.NaN;

    public ZgcCycleDurationsAndIntervalsAggregator(
            ZgcCycleDurationsAndIntervalsAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        double t = event.getDateTimeStamp().toSeconds();
        double durationSec = event.getDuration();
        if (durationSec > 0.0) {
            aggregation().recordCycleDuration(t, durationSec);
        }
        if (!Double.isNaN(prevCycleStartSec)) {
            double interval = t - prevCycleStartSec;
            if (interval > 0.0) aggregation().recordCycleInterval(t, interval);
        }
        prevCycleStartSec = t;
    }
}
