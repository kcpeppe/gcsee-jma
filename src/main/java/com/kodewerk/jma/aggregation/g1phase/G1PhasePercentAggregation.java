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
 * G1 per-pause phase breakout as a percentage of each pause's total —
 * the same underlying data as {@link G1PhaseAggregation} rescaled per
 * event so every bar / stacked column sums to 100%. Useful for seeing
 * the shape of where G1 is spending its time without the per-event
 * pause-length dominating the picture.
 * <p>
 * Presentation-wise this is a stacked time-series (one sample per event,
 * values summing to ~100) plotted on a linear seconds x-axis — the same
 * axis convention as the scatter views — so it scales cleanly past a
 * few thousand events.
 * <p>
 * Phase names are not baked in. The aggregator passes through whatever
 * {@code G1Young.phaseNames()} yields; first-seen order drives series
 * order and colouring.
 */
@Collates(G1PhasePercentAggregator.class)
public final class G1PhasePercentAggregation extends JmaAggregation {

    // Same palette as the absolute view so a given phase gets the same
    // colour across both charts.
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

    private record EventRow(double tSec, Map<String, Double> phaseSeconds) {}

    private final LinkedHashSet<String> phaseNamesSeen = new LinkedHashSet<>();
    private final List<EventRow> rows = new ArrayList<>();

    private long eventsWithoutPhaseData = 0;

    /** Required by the GCSee module SPI. */
    public G1PhasePercentAggregation() {}

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
                "time (s)", "% of pause", "%", series);
        for (EventRow row : rows) {
            double total = 0.0;
            for (Double v : row.phaseSeconds.values()) total += v;
            double[] values = new double[phaseNamesSeen.size()];
            if (total > 0.0) {
                int idx = 0;
                for (String name : phaseNamesSeen) {
                    Double seconds = row.phaseSeconds.get(name);
                    values[idx++] = (seconds != null ? seconds : 0.0) / total * 100.0;
                }
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
