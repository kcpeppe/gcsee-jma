package com.kodewerk.jma.aggregation.g1phase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/**
 * Companion to {@link G1PhaseAggregator} that feeds the percentage view.
 * Same harvest (via {@link G1PhaseHarvest}), same subscription
 * ({@link G1Young}) — only the target aggregation differs. Percent-of-
 * total is computed at emit time in {@link G1PhasePercentAggregation},
 * not here, so the aggregator stays a thin pass-through and the two
 * views are guaranteed to operate on identical raw data.
 */
@Aggregates(EventSource.G1GC)
public final class G1PhasePercentAggregator extends Aggregator<G1PhasePercentAggregation> {

    public G1PhasePercentAggregator(G1PhasePercentAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        Map<String, Double> phaseSeconds = G1PhaseHarvest.phasesOf(event);
        if (phaseSeconds.isEmpty()) {
            aggregation().noteMissingPhaseData();
            return;
        }
        double tSec = event.getDateTimeStamp().toSeconds();
        aggregation().recordEvent(tSec, phaseSeconds);
    }
}
