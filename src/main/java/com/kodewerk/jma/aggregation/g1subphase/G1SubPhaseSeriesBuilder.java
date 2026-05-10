package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared series builder for the G1 sub-phase charts. Walks the per-event
 * rows captured by {@link G1SubPhaseAggregation} and emits one
 * {@link XYSeriesData.Series} per {@code (sub-phase × collection-type)}
 * combination, with colour assigned per sub-phase and Chart.js point
 * style assigned per collection type.
 * <p>
 * Sub-phase colours come from a fixed palette indexed by the
 * first-seen order of sub-phase names — same approach as
 * {@code G1PhaseAggregation}, scaled up to 16 entries since sub-phase
 * counts can be higher than the four top-level phases. Insertion order
 * is stable across the absolute and percent variants because both
 * variants see events in the same order.
 */
final class G1SubPhaseSeriesBuilder {

    /** Palette indexed by first-seen sub-phase. Wraps if a log emits more than 16. */
    private static final String[] PALETTE = {
            "#7e57c2", // purple
            "#43a047", // green
            "#fb8c00", // orange
            "#1e88e5", // blue
            "#d81b60", // pink
            "#6d4c41", // brown
            "#00897b", // dark teal
            "#fdd835", // yellow
            "#3949ab", // indigo
            "#7cb342", // lime
            "#039be5", // light blue
            "#827717", // olive
            "#5e35b1", // deep purple
            "#546e7a", // blue-grey
            "#e53935", // red — last so high-cardinality logs still have signal
            "#90a4ae"  // slate grey
    };

    /** Fixed iteration order so legend reads Young → Mixed → InitialMark. */
    private static final String[] COLLECTION_ORDER = {
            G1SubPhaseHarvest.COLL_YOUNG,
            G1SubPhaseHarvest.COLL_MIXED,
            G1SubPhaseHarvest.COLL_INITIAL_MARK
    };

    private G1SubPhaseSeriesBuilder() {}

    /**
     * Build the {@link XYSeriesData} for one phase view.
     *
     * @param rows         per-event raw sub-phase data
     * @param percent      {@code true} → emit
     *                     {@code (subPhaseSec / Σ subPhaseSec) × 100};
     *                     {@code false} → emit {@code subPhaseSec × 1000}
     *                     (milliseconds).
     * @param yAxisLabel   y-axis label text
     * @param yUnit        y-axis unit string ({@code "ms"} or {@code "%"})
     */
    static XYSeriesData build(List<G1SubPhaseAggregation.EventRow> rows,
                              boolean percent,
                              String yAxisLabel,
                              String yUnit) {
        // First pass — establish stable first-seen order for sub-phase
        // names; assigns the colour index.
        Map<String, Integer> subPhaseColorIdx = new LinkedHashMap<>();
        for (G1SubPhaseAggregation.EventRow r : rows) {
            for (String name : r.subPhaseSeconds().keySet()) {
                subPhaseColorIdx.computeIfAbsent(name, k -> subPhaseColorIdx.size());
            }
        }

        // (sub-phase, collection-type) → point list
        Map<String, Map<String, List<XYSeriesData.Point>>> bucket = new LinkedHashMap<>();
        for (G1SubPhaseAggregation.EventRow r : rows) {
            // Per-event total for the percent calc. Use the sum of
            // sub-phase values rather than phaseDurationFor() — the
            // sum always yields a clean 100% on the chart even when
            // the JVM-reported phase total is slightly different.
            double total = 0.0;
            for (Double v : r.subPhaseSeconds().values()) total += v;

            for (Map.Entry<String, Double> e : r.subPhaseSeconds().entrySet()) {
                double sec = e.getValue();
                double y;
                if (percent) {
                    if (total <= 0.0) continue;
                    y = sec / total * 100.0;
                } else {
                    y = sec * 1000.0; // ms
                }
                bucket.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
                      .computeIfAbsent(r.collectionType(), k -> new ArrayList<>())
                      .add(new XYSeriesData.Point(r.tSec(), y));
            }
        }

        List<XYSeriesData.Series> series = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<XYSeriesData.Point>>> phaseEntry
                : bucket.entrySet()) {
            String subPhase = phaseEntry.getKey();
            String color = PALETTE[subPhaseColorIdx.get(subPhase) % PALETTE.length];
            for (String collectionType : COLLECTION_ORDER) {
                List<XYSeriesData.Point> points = phaseEntry.getValue().get(collectionType);
                if (points == null || points.isEmpty()) continue;
                String label = subPhase + " (" + collectionType + ")";
                String pointStyle = G1SubPhaseHarvest.pointStyleFor(collectionType);
                series.add(new XYSeriesData.Series(
                        subPhase + "::" + collectionType, label, color, pointStyle, points));
            }
        }

        return new XYSeriesData("time (s)", yAxisLabel, yUnit, series);
    }
}
