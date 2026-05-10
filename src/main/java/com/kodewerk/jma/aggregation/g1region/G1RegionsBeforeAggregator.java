package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;

/**
 * Feeds {@link G1RegionsBeforeAggregation} — region counts as they stood
 * <em>before</em> each G1 pause. Listens on {@link G1GCPauseEvent} so we
 * pick up Young, Mixed, InitialMark, Remark, Cleanup and Full pauses;
 * pauses without region detail are filtered out by {@link G1RegionHarvest}
 * and counted as missing samples on the aggregation.
 */
@Aggregates(EventSource.G1GC)
public final class G1RegionsBeforeAggregator extends Aggregator<G1RegionsBeforeAggregation> {

    public G1RegionsBeforeAggregator(G1RegionsBeforeAggregation aggregation) {
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
