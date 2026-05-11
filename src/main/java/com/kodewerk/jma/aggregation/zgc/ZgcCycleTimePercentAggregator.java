package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;

/**
 * Feeds {@link ZgcCycleTimePercentAggregation}. Same harvest logic as
 * the absolute-duration view; the normalisation lives in the
 * aggregation so the harvest itself stays uniform.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcCycleTimePercentAggregator
        extends Aggregator<ZgcCycleTimePercentAggregation> {

    public ZgcCycleTimePercentAggregator(ZgcCycleTimePercentAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        double cycleDurationSec = event.getDuration();
        if (cycleDurationSec <= 0.0) return;
        aggregation().recordCycle(
                event.getDateTimeStamp().toSeconds(),
                cycleDurationSec,
                ZgcPhaseHarvest.harvest(event));
    }
}
