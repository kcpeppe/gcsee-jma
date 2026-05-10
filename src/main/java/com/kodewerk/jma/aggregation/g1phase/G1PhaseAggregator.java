package com.kodewerk.jma.aggregation.g1phase;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

import java.util.Map;

/**
 * Walks the top-level phase iterator GCSee exposes on every G1 young
 * collection and feeds the per-phase durations into
 * {@link G1PhaseAggregation}. The top-level phases are the "big four"
 * partitions of a G1 young pause (pre-evacuate / evacuate / post-evacuate
 * / other in current JDKs, but the names come from GCSee and are not
 * hard-coded here).
 * <p>
 * Mirrors the pattern in Censum's {@code G1GCUnifiedPhaseAggregator} —
 * registers on {@link G1Young} specifically (which includes
 * young-initial-mark through class inheritance) because
 * {@code phaseNames()} / {@code phaseDurationFor()} are members of
 * {@code G1Young}, not of the broader {@code G1GCPauseEvent} base.
 * G1Mixed, G1Remark, G1Cleanup and G1FullGC therefore do not reach this
 * aggregator even though they are also G1 pause events.
 * <p>
 * Each {@code (name, durationSec)} pair is passed through to the
 * aggregation verbatim — no name interpretation, no bucketing. Downstream
 * grouping, ordering, and colouring is done at emit time based on what
 * was actually seen, so a future GCSee / JDK that renames a phase just
 * flows through the chart legend as-is.
 */
@Aggregates(EventSource.G1GC)
public final class G1PhaseAggregator extends Aggregator<G1PhaseAggregation> {

    public G1PhaseAggregator(G1PhaseAggregation aggregation) {
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
