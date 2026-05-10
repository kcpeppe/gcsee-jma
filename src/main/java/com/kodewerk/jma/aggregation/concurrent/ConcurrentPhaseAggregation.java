package com.kodewerk.jma.aggregation.concurrent;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Concurrent-phase duration scatter — one series per phase type
 * (G1ConcurrentMark, G1ConcurrentCleanup, ConcurrentSweep, …) with
 * one point per occurrence at the phase's start time. Plotted in
 * milliseconds for consistency with the pause-time chart.
 * <p>
 * Series cardinality is data-dependent (different collectors emit
 * different concurrent phases, and the set varies by JDK version),
 * so this view uses {@link XYSeriesData} rather than the
 * {@code GCCategory}-keyed {@link com.kodewerk.jma.chart.ScatterData}.
 * Series order matches first-seen insertion order, which for typical
 * G1 / CMS logs reads as a sensible cycle progression.
 */
@Collates(ConcurrentPhaseAggregator.class)
public final class ConcurrentPhaseAggregation extends JmaAggregation {

    /**
     * Small palette assigned to phase series in first-seen order. Same
     * shape as the G1 phase chart so the look is consistent across
     * "concurrent" and "in-pause phase" views.
     */
    private static final String[] PALETTE = {
            "#7e57c2", // purple
            "#43a047", // green
            "#fb8c00", // orange
            "#90a4ae", // slate grey
            "#1e88e5", // blue
            "#d81b60", // pink
            "#6d4c41", // brown
            "#546e7a", // blue-grey
            "#00897b", // dark teal
            "#fdd835"  // yellow
    };

    /** Insertion-ordered phase → points. */
    private final Map<String, List<XYSeriesData.Point>> byPhase = new LinkedHashMap<>();

    /** Required by the GCSee module SPI. */
    public ConcurrentPhaseAggregation() {}

    public void recordPhase(String phaseName, double tSec, double durationMs) {
        byPhase.computeIfAbsent(phaseName, k -> new ArrayList<>())
               .add(new XYSeriesData.Point(tSec, durationMs));
    }

    public XYSeriesData getData() {
        List<XYSeriesData.Series> series = new ArrayList<>(byPhase.size());
        int i = 0;
        for (Map.Entry<String, List<XYSeriesData.Point>> e : byPhase.entrySet()) {
            String name  = e.getKey();
            String color = PALETTE[i % PALETTE.length];
            series.add(new XYSeriesData.Series(name, name, color, e.getValue()));
            i++;
        }
        return new XYSeriesData(
                "time (s)", "duration (ms)", "ms", series);
    }

    @Override
    public boolean isEmpty() {
        for (List<XYSeriesData.Point> pts : byPhase.values()) {
            if (!pts.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
