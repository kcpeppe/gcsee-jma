package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;

/**
 * Feeds {@link G1RegionsAfterPercentAggregation}. Same harvest as
 * {@link G1RegionsAfterAggregator}; percent-of-total is computed at
 * emit time so the two after-views always agree on raw samples.
 */
@Aggregates(EventSource.G1GC)
public final class G1RegionsAfterPercentAggregator extends Aggregator<G1RegionsAfterPercentAggregation> {

    public G1RegionsAfterPercentAggregator(G1RegionsAfterPercentAggregation aggregation) {
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
