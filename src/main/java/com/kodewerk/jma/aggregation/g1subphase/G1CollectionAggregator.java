package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/**
 * Feeds the Evacuate Collection Set sub-phase aggregations. Evacuate
 * sub-phases are reported as {@code StatisticalSummary} (parallel
 * worker stats); the harvest helper picks the slowest-worker
 * {@code max} value as the per-event reading because that's the
 * phase's wall-clock contribution to the pause.
 */
@Aggregates(EventSource.G1GC)
public final class G1CollectionAggregator extends Aggregator<G1SubPhaseAggregation> {

    public G1CollectionAggregator(G1SubPhaseAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        Map<String, Double> subs = G1SubPhaseHarvest.evacuateSubPhases(event);
        if (subs.isEmpty()) {
            aggregation().noteMissingSubPhaseData();
            return;
        }
        aggregation().recordEvent(
                event.getDateTimeStamp().toSeconds(),
                G1SubPhaseHarvest.collectionTypeOf(event),
                subs);
    }
}
