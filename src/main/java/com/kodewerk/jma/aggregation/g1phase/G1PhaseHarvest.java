package com.kodewerk.jma.aggregation.g1phase;

import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared extractor for G1 top-level phase durations — walks
 * {@code G1Young.phaseNames()} and resolves each name to its duration
 * via {@code phaseDurationFor(name)}, returning an insertion-ordered
 * {@code (phaseName → durationSec)} map.
 * <p>
 * Kept as a single package-private helper because the aggregators for
 * the absolute and percentage views of the G1 phase breakout both do
 * the same harvest and only differ in how they present the values.
 * <p>
 * Mirrors Censum's {@code G1GCUnifiedPhaseAggregator} top-level loop —
 * same event type ({@code G1Young}), same accessor pair, same "verbatim
 * pass-through, no name interpretation" contract.
 */
final class G1PhaseHarvest {

    private G1PhaseHarvest() {}

    /**
     * Extract top-level phase durations from a G1 young collection.
     * Returns an empty map if the event carries no phase detail.
     */
    static Map<String, Double> phasesOf(G1Young event) {
        Map<String, Double> phaseSeconds = new LinkedHashMap<>();
        Iterator<String> names = event.phaseNames();
        while (names != null && names.hasNext()) {
            String name = names.next();
            if (name == null || name.isBlank()) continue;
            double durationSec = event.phaseDurationFor(name);
            if (Double.isNaN(durationSec) || durationSec < 0.0) continue;
            phaseSeconds.merge(name, durationSec, Double::sum);
        }
        return phaseSeconds;
    }
}
