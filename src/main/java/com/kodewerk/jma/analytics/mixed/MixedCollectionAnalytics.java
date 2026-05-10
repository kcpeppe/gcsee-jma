package com.kodewerk.jma.analytics.mixed;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mixed-collections feature group analytics. One descriptive finding —
 * a histogram of the number of consecutive G1 mixed collections that
 * follow each concurrent-mark cycle — plus the standard background on
 * how G1 builds the mixed CSet and which flags shape that decision.
 * <p>
 * The shape of this distribution is informative even when nothing is
 * obviously wrong:
 * <ul>
 *   <li>Runs clustered near the upper bound (default 8 from
 *       {@code -XX:G1MixedGCCountTarget}) means the collector is using
 *       its full sweep budget.</li>
 *   <li>Runs cut short well below the upper bound usually mean the
 *       collector ran out of eligible regions, hit pause-time pressure,
 *       or the heap doesn't need that much old-gen sweeping.</li>
 *   <li>Bimodal distributions (a peak at 8 and another at 1–2) are
 *       commonly seen on apps with bursty old-gen growth.</li>
 * </ul>
 * Severity is left at {@code OK} — the finding is informational. The
 * pause-time and heap-usage groups will already badge anything truly
 * pathological.
 */
public final class MixedCollectionAnalytics {

    private static final String GROUP = "Mixed collections";

    private MixedCollectionAnalytics() {}

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        // Analytics-always-on rule for collector-specific groups: as long
        // as the log is G1 we emit something, even with an empty
        // histogram. The Mixed Collections section is always meaningful
        // for a G1 operator — its absence on a G1 log is itself a
        // signal worth surfacing. For non-G1 logs the section stays
        // hidden because mixed collections aren't a concept there.
        if (!agg.isG1Observed()) return;

        Map<Integer, Long> hist = agg.getMixedRunHistogram();
        long totalCycles = 0L;
        long totalMixed  = 0L;
        for (Map.Entry<Integer, Long> e : hist.entrySet()) {
            totalCycles += e.getValue();
            totalMixed  += (long) e.getKey() * e.getValue();
        }

        String message;
        if (hist.isEmpty()) {
            message = "No completed concurrent-mark cycles observed in this log — "
                    + "either the heap never crossed -XX:InitiatingHeapOccupancyPercent, "
                    + "the log was too short for a full cycle, or the cycle that did "
                    + "start has not yet completed.";
        } else {
            double mean = totalMixed / (double) totalCycles;
            message = String.format(Locale.ROOT,
                    "%d concurrent-mark cycle(s) produced %d mixed collection(s) "
                    + "(mean %.1f per cycle).",
                    totalCycles, totalMixed, mean);
        }

        // Build the histogram table — render as a fixed-width text block
        // so the existing white-space: pre-wrap details renderer aligns
        // the columns without any new UI work. When the histogram is
        // empty we still show the column headers and a placeholder row
        // so the section reads as "we looked, here's the (empty) result".
        StringBuilder details = new StringBuilder();
        details.append("Mixed Collections | Occurrences\n");
        details.append("------------------+-------------\n");
        if (hist.isEmpty()) {
            details.append("              (no cycles observed)\n");
        } else {
            for (Map.Entry<Integer, Long> e : hist.entrySet()) {
                details.append(String.format(Locale.ROOT,
                        "%17d | %11d%n",
                        e.getKey(), e.getValue()));
            }
        }
        details.append('\n');
        details.append("""
                Description
                The histogram above is a count of the different numbers of \
                consecutive mixed collections following each Concurrent Mark \
                cycle.

                The Collection Set (CSet) for a mixed collection contains all \
                of the young generational regions plus a smallish number of \
                old generational regions. The number of old regions added to \
                the CSet is calculated using a number of factors; the two \
                primary ones are the pause-time goal and the estimated cost \
                of collecting a region. The objective is to use the estimates \
                to build a CSet that can be collected within the pause-time \
                goal.

                Additionally, the old regions eligible to be collected will \
                be distributed over a number of mixed collections. If not \
                enough memory can be freed, the pause-time goal will be \
                purposely violated. It may be possible to decrease pause \
                times by configuring the JVM to produce a better distribution \
                of the number of mixed collections needed to complete the \
                sweep of tenured.

                Tuning flags
                  • -XX:G1MixedGCCountTarget=N — upper bound on the number of \
                mixed collections per concurrent-mark cycle. Default 8.
                  • -XX:G1MixedGCLiveThresholdPercent=N — an old region is \
                eligible for collection if its calculated occupancy after the \
                concurrent-mark phase is below this percentage. Default 85.
                  • -XX:G1HeapWastePercent=N — the collector will stop \
                producing mixed collections once the reclaimable heap drops \
                below this fraction. Default 5.
                  • -XX:G1OldCSetRegionThresholdPercent=N — caps the number \
                of old regions added per mixed CSet (as a percent of the \
                total region count). Default 10.""");

        out.add(new AnalyticsFinding(GROUP, "Mixed collection counts",
                Impact.OK, message, details.toString()));
    }
}
