package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;

/**
 * Feeds {@link G1RegionsBeforePercentAggregation}. Same harvest as
 * {@link G1RegionsBeforeAggregator}; percent-of-total is computed at
 * emit time inside the aggregation so the two before-views are
 * guaranteed to operate on identical raw samples.
 */
@Aggregates(EventSource.G1GC)
public final class G1RegionsBeforePercentAggregator extends Aggregator<G1RegionsBeforePercentAggregation> {

    public G1RegionsBeforePercentAggregator(G1RegionsBeforePercentAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class, this::onPause);
    }

    private void onPause(G1GCPauseEvent event) {
        G1RegionHarvest.Snapshot snap = G1RegionHarvest.snapshotOf(event);
        if (snap == null) {
            aggregation().noteMissingRegionData();
            return;
        }
        aggregation().recordSnapshot(snap);
    }
}
