package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;

/**
 * Feeds {@link G1RegionsAfterAggregation} — region counts as they stood
 * <em>after</em> each G1 pause. Same subscription set and harvest as
 * {@link G1RegionsBeforeAggregator}; the two aggregators are kept apart
 * so the {@code @Collates} annotation pair stays 1:1, even though both
 * walk the same event stream.
 */
@Aggregates(EventSource.G1GC)
public final class G1RegionsAfterAggregator extends Aggregator<G1RegionsAfterAggregation> {

    public G1RegionsAfterAggregator(G1RegionsAfterAggregation aggregation) {
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
