package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/**
 * Feeds the Pre-Evacuate Collection Set sub-phase aggregations
 * (absolute ms and percent-of-phase variants). Both aggregations
 * collate with this class via {@code @Collates(...)}; GCSee
 * instantiates one aggregator per registered aggregation, so each
 * variant gets its own instance and event stream from the dispatcher.
 */
@Aggregates(EventSource.G1GC)
public final class G1PreCollectionAggregator extends Aggregator<G1SubPhaseAggregation> {

    public G1PreCollectionAggregator(G1SubPhaseAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        Map<String, Double> subs = G1SubPhaseHarvest.preEvacuateSubPhases(event);
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
