package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.event.zgc.ZGCCollection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls the concurrent-phase duration fields off a single
 * {@link ZGCCollection} into a deterministic series-order keyed map.
 * Insertion order matches the visual ordering we want on the multi-series
 * charts (Mark → Mark Continue → Mark Free → Ref Processing → Reset
 * Relocation Set → Select Relocation Set → Relocate → Remap Roots),
 * which matches the order the phases fire inside a cycle.
 * <p>
 * Returns durations in <em>seconds</em> — callers convert to ms or
 * percentage as needed. Phases that didn't fire in the cycle (e.g.
 * Mark Continue on a Young cycle) come back as {@code 0.0}; the
 * aggregations decide whether to emit a zero or skip the point.
 */
final class ZgcPhaseHarvest {

    /** Series id → label. Ordering is preserved by the returned map. */
    static final java.util.SequencedMap<String, String> SERIES_LABELS = new LinkedHashMap<>();
    /** Series id → suggested Chart.js colour. */
    static final java.util.SequencedMap<String, String> SERIES_COLORS = new LinkedHashMap<>();

    static {
        SERIES_LABELS.put("mark",            "Mark");
        SERIES_LABELS.put("mark-continue",   "Mark Continue");
        SERIES_LABELS.put("mark-free",       "Mark Free");
        SERIES_LABELS.put("ref-processing",  "Ref Processing");
        SERIES_LABELS.put("reset-reloc",     "Reset Reloc Set");
        SERIES_LABELS.put("select-reloc",    "Select Reloc Set");
        SERIES_LABELS.put("relocate",        "Relocate");
        SERIES_LABELS.put("remap-roots",     "Remap Roots");

        // Eight visually distinct hues — same palette family the
        // pause-time / cycle-duration charts pull from so the ZGC
        // section reads as one coherent look.
        SERIES_COLORS.put("mark",            "#1e88e5");
        SERIES_COLORS.put("mark-continue",   "#5e35b1");
        SERIES_COLORS.put("mark-free",       "#00897b");
        SERIES_COLORS.put("ref-processing",  "#43a047");
        SERIES_COLORS.put("reset-reloc",     "#c0ca33");
        SERIES_COLORS.put("select-reloc",    "#fb8c00");
        SERIES_COLORS.put("relocate",        "#e53935");
        SERIES_COLORS.put("remap-roots",     "#8e24aa");
    }

    private ZgcPhaseHarvest() {}

    /**
     * Phase id → duration (seconds), in fixed display order.
     * Phases that didn't occur in this cycle come back as {@code 0.0}.
     */
    static Map<String, Double> harvest(ZGCCollection event) {
        Map<String, Double> out = new LinkedHashMap<>(SERIES_LABELS.size() * 2);
        out.put("mark",            nonNeg(event.getConcurrentMarkDuration()));
        out.put("mark-continue",   nonNeg(event.getConcurrentMarkContinueDuration()));
        out.put("mark-free",       nonNeg(event.getConcurrentMarkFreeDuration()));
        out.put("ref-processing",  nonNeg(event.getConcurrentProcessNonStrongReferencesDuration()));
        out.put("reset-reloc",     nonNeg(event.getConcurrentResetRelocationSetDuration()));
        out.put("select-reloc",    nonNeg(event.getConcurrentSelectRelocationSetDuration()));
        out.put("relocate",        nonNeg(event.getConcurrentRelocateDuration()));
        out.put("remap-roots",     nonNeg(event.getConcurrentRemapRootsDuration()));
        return out;
    }

    private static double nonNeg(double v) { return v > 0.0 ? v : 0.0; }
}
