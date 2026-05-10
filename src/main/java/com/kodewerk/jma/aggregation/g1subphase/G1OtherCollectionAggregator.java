package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/**
 * Feeds the Other-phase sub-phase aggregations. The Other phase has no
 * generic name → duration map on the GCSee API in this version; the
 * harvest helper synthesises one from the six known "Other"-flavoured
 * fields it does expose individually (code-root fixup / migration /
 * purge, clear-card-table, expand-heap, string-deduping). Only
 * non-zero fields make it through, so a phase that didn't run on a
 * given event simply doesn't show a point.
 */
@Aggregates(EventSource.G1GC)
public final class G1OtherCollectionAggregator extends Aggregator<G1SubPhaseAggregation> {

    public G1OtherCollectionAggregator(G1SubPhaseAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        Map<String, Double> subs = G1SubPhaseHarvest.otherSubPhases(event);
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
