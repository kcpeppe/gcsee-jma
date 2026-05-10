package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/** Feeds the Post-Evacuate Collection Set sub-phase aggregations. */
@Aggregates(EventSource.G1GC)
public final class G1PostCollectionAggregator extends Aggregator<G1SubPhaseAggregation> {

    public G1PostCollectionAggregator(G1SubPhaseAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        Map<String, Double> subs = G1SubPhaseHarvest.postEvacuateSubPhases(event);
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
