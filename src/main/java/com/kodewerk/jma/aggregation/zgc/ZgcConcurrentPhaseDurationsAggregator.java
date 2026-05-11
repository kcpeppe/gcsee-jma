package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;

/**
 * Feeds {@link ZgcConcurrentPhaseDurationsAggregation}. Delegates the
 * "which fields belong to which series" decision to
 * {@link ZgcPhaseHarvest} so the same definition is reused by the
 * percent-of-cycle-time view.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcConcurrentPhaseDurationsAggregator
        extends Aggregator<ZgcConcurrentPhaseDurationsAggregation> {

    public ZgcConcurrentPhaseDurationsAggregator(
            ZgcConcurrentPhaseDurationsAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        aggregation().recordCycle(
                event.getDateTimeStamp().toSeconds(),
                ZgcPhaseHarvest.harvest(event));
    }
}
