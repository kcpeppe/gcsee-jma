package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.event.StatisticalSummary;
import com.kodewerk.gcsee.event.g1gc.G1Mixed;
import com.kodewerk.gcsee.event.g1gc.G1Young;
import com.kodewerk.gcsee.event.g1gc.G1YoungInitialMark;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared extractor for the G1Young per-phase sub-phase data behind
 * the eight G1 sub-phase charts (four phases × {absolute, percent}).
 * <p>
 * Three of the four top-level phases expose a name → duration map
 * directly on {@link G1Young}:
 * <ul>
 *   <li>Pre-Evacuate Collection Set —
 *       {@code preEvacuateCSetPhaseNames()} / {@code preEvacuateCSetPhaseDuration(name)}
 *       (seconds).</li>
 *   <li>Evacuate Collection Set —
 *       {@code evacuateCSetPhaseNames()} / {@code evacuateCSetPhaseDuration(name)}
 *       returning {@link StatisticalSummary} because the work is done in
 *       parallel by N workers; we use {@link StatisticalSummary#getMax()}
 *       so the value reflects the slowest worker (which is the phase's
 *       wall-clock contribution).</li>
 *   <li>Post-Evacuate Collection Set —
 *       {@code postEvacuateCSetPhaseNames()} / {@code postEvacuateCSetPhaseDuration(name)}
 *       (seconds).</li>
 * </ul>
 * <p>
 * The fourth phase, "Other", is rolled up into a single
 * {@code getOtherPhaseDurations()} double on the API. There is no
 * generic name / duration map for Other in this version of GCSee; we
 * synthesise one from the six known "Other"-flavoured fields the API
 * does expose individually (code-root fixup / migration / purge,
 * clear-card-table, expand-heap, string-deduping). New sub-phases the
 * JVM might add in future would not appear here until the GCSee API
 * exposes them.
 */
final class G1SubPhaseHarvest {

    /** Collection-type marker — drives series labelling and Chart.js point style. */
    static final String COLL_INITIAL_MARK = "InitialMark";
    static final String COLL_MIXED        = "Mixed";
    static final String COLL_YOUNG        = "Young";

    /** Chart.js marker name per collection type. */
    static String pointStyleFor(String collectionType) {
        if (COLL_MIXED.equals(collectionType))         return "triangle";
        if (COLL_INITIAL_MARK.equals(collectionType))  return "rect";
        return "circle"; // Young (and any future subtype) defaults to circle
    }

    private G1SubPhaseHarvest() {}

    /**
     * Classify the event for series-labelling purposes. Most-specific
     * subtypes first because {@link G1Mixed} and
     * {@link G1YoungInitialMark} both extend {@link G1Young}.
     */
    static String collectionTypeOf(G1Young event) {
        if (event instanceof G1Mixed)            return COLL_MIXED;
        if (event instanceof G1YoungInitialMark) return COLL_INITIAL_MARK;
        return COLL_YOUNG;
    }

    static Map<String, Double> preEvacuateSubPhases(G1Young event) {
        Map<String, Double> out = new LinkedHashMap<>();
        event.preEvacuateCSetPhaseNames().forEach(name -> {
            double sec = event.preEvacuateCSetPhaseDuration(name);
            if (validDuration(sec)) out.merge(name, sec, Double::sum);
        });
        return out;
    }

    /**
     * Evacuate sub-phases use the slowest-worker {@code max} value as
     * the per-event reading, since that's the phase's wall-clock
     * contribution to the pause.
     */
    static Map<String, Double> evacuateSubPhases(G1Young event) {
        Map<String, Double> out = new LinkedHashMap<>();
        event.evacuateCSetPhaseNames().forEach(name -> {
            StatisticalSummary stat = event.evacuateCSetPhaseDuration(name);
            if (stat == null) return;
            double max = stat.getMax();
            if (validDuration(max)) out.merge(name, max, Double::sum);
        });
        return out;
    }

    static Map<String, Double> postEvacuateSubPhases(G1Young event) {
        Map<String, Double> out = new LinkedHashMap<>();
        event.postEvacuateCSetPhaseNames().forEach(name -> {
            double sec = event.postEvacuateCSetPhaseDuration(name);
            if (validDuration(sec)) out.merge(name, sec, Double::sum);
        });
        return out;
    }

    /**
     * Synthetic "Other" sub-phase map built from the individual
     * Other-flavoured fields the API exposes. Names match what the
     * underlying log lines use so a user familiar with G1 logs reads
     * them on sight. Zero-duration sub-phases are skipped — they
     * clutter the legend and signal "this sub-phase didn't run on
     * this event".
     */
    static Map<String, Double> otherSubPhases(G1Young event) {
        Map<String, Double> out = new LinkedHashMap<>();
        addNonZero(out, "Code Root Fixup",     event.getCodeRootFixupDuration());
        addNonZero(out, "Code Root Migration", event.getCodeRootMigrationDuration());
        addNonZero(out, "Code Root Purge",     event.getCodeRootPurgeDuration());
        addNonZero(out, "Clear Card Table",    event.getClearCTDuration());
        addNonZero(out, "Expand Heap",         event.getExpandHeapDuration());
        addNonZero(out, "String Deduping",     event.getStringDedupingDuration());
        return out;
    }

    private static void addNonZero(Map<String, Double> out, String name, double sec) {
        if (validDuration(sec) && sec > 0.0) out.put(name, sec);
    }

    private static boolean validDuration(double sec) {
        return !Double.isNaN(sec) && sec >= 0.0;
    }
}
