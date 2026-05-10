package com.kodewerk.jma.aggregation.g1phase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.StackedBarData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * G1 per-pause phase breakout: how much of each G1 pause was spent in
 * each named phase the log reports. One stacked bar per pause event —
 * bar height is the sum of the phase durations, each stack segment is
 * one phase's duration in milliseconds.
 * <p>
 * Phase names are intentionally not baked into this class. The aggregator
 * walks whatever phase iterator GCSee exposes on the pause event and
 * reports {@code (name, durationSec)} tuples; this aggregation accumulates
 * the union of names seen (first-seen order) and emits one dataset per
 * distinct name at serialization time. That way a future GCSee / JDK that
 * renames {@code "Pre Evacuate Collection Set"} to something else doesn't
 * require a code change here — the new name just starts appearing in the
 * chart legend as-is.
 * <p>
 * Produced only for G1 logs — for Generational and ZGC the aggregation
 * stays empty and the view is reported as {@code EMPTY}. G1FullGC and any
 * event without phase detail are counted in a "missing phase detail"
 * warning rather than silently dropped.
 */
@Collates(G1PhaseAggregator.class)
public final class G1PhaseAggregation extends JmaAggregation {

    /**
     * Small palette used to colour phase series in the order the phases
     * were first seen. Kept short (wraps if a log reports more phases)
     * because in practice G1 emits a handful of top-level phases.
     */
    private static final String[] PALETTE = {
            "#7e57c2", // purple
            "#43a047", // green
            "#fb8c00", // orange
            "#90a4ae", // slate grey
            "#1e88e5", // blue
            "#d81b60", // pink
            "#6d4c41", // brown
            "#546e7a"  // blue-grey
    };

    /** One pause event's worth of phase durations, in seconds. */
    private record EventRow(double tSec, Map<String, Double> phaseSeconds) {}

    // LinkedHashSet so the series order matches the order phase names
    // were first seen — for a standard G1 log that's naturally
    // pre-evac → evac → post-evac → other.
    private final LinkedHashSet<String> phaseNamesSeen = new LinkedHashSet<>();
    private final List<EventRow> rows = new ArrayList<>();

    private long eventsWithoutPhaseData = 0;

    /** Required by the GCSee module SPI. */
    public G1PhaseAggregation() {}

    /**
     * Record one pause event's phase breakout. The map is {@code (phaseName →
     * durationSec)}; callers may pass it with any iteration order — this
     * class preserves the insertion order of the first time a given name
     * appears across the whole aggregation.
     */
    void recordEvent(double tSec, Map<String, Double> phaseSeconds) {
        phaseNamesSeen.addAll(phaseSeconds.keySet());
        rows.add(new EventRow(tSec, new LinkedHashMap<>(phaseSeconds)));
    }

    void noteMissingPhaseData() {
        eventsWithoutPhaseData++;
    }

    public StackedBarData getData() {
        List<StackedBarData.Series> series = new ArrayList<>(phaseNamesSeen.size());
        int i = 0;
        for (String name : phaseNamesSeen) {
            series.add(new StackedBarData.Series(name, name, PALETTE[i % PALETTE.length]));
            i++;
        }
        StackedBarData out = new StackedBarData(
                "time (s)", "duration (ms)", "ms", series);
        for (EventRow row : rows) {
            double[] values = new double[phaseNamesSeen.size()];
            int idx = 0;
            for (String name : phaseNamesSeen) {
                Double seconds = row.phaseSeconds.get(name);
                values[idx++] = (seconds != null ? seconds : 0.0) * 1000.0;
            }
            out.add(row.tSec, values);
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return eventsWithoutPhaseData > 0;
    }

    @Override
    public String getWarningMessage() {
        if (eventsWithoutPhaseData == 0) return null;
        return eventsWithoutPhaseData + " G1 pause event(s) did not carry phase "
                + "detail and were skipped.";
    }
}
