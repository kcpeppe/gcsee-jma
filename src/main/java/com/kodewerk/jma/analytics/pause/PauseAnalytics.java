package com.kodewerk.jma.analytics.pause;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Pause-time feature group analytics. Three checks, each tuned to a
 * different operator question:
 * <ul>
 *   <li><b>High pause time</b> — how bad is the worst pause? &gt; 200 ms
 *       gets a warning, &gt; 1 s significant. Counts pauses crossing the
 *       same thresholds in the message so the reader can tell whether
 *       the worst pause is a one-off or part of a pattern.</li>
 *   <li><b>Application throughput</b> — fraction of wall-clock time the
 *       application got. Below 95 % overall is significant; the same
 *       95 % threshold applied to a sliding 30-minute window catches
 *       transient bad periods that would otherwise hide inside a good
 *       overall figure.</li>
 *   <li><b>Pause distribution</b> — descriptive shape report at p10,
 *       p50, p90 and p99 plus max, so the operator can see the
 *       distribution body and tail at a glance.</li>
 * </ul>
 */
public final class PauseAnalytics {

    private static final String GROUP = "Pause times";

    /** Warning threshold for max pause (ms). */
    public static final double HIGH_PAUSE_WARN_MS        = 200.0;

    /** Significant threshold for max pause (ms). */
    public static final double HIGH_PAUSE_SIGNIFICANT_MS = 1000.0;

    /** Throughput target (percentage). */
    public static final double THROUGHPUT_TARGET_PCT = 95.0;

    /** Sliding-window length (seconds) for the windowed-throughput check. */
    public static final double THROUGHPUT_WINDOW_SEC = 30.0 * 60.0;

    /** Step (seconds) between consecutive sliding windows. */
    public static final double THROUGHPUT_WINDOW_STEP_SEC = 60.0;

    /**
     * Window length (seconds) for the pause-clustering check. 10 s is a
     * "user-experience" timescale — long enough that individual pauses
     * don't dominate, short enough to catch bursts the 30-minute
     * throughput check averages away.
     */
    public static final double CLUSTER_WINDOW_SEC = 10.0;

    /** Step between sliding clustering windows. */
    public static final double CLUSTER_WINDOW_STEP_SEC = 1.0;

    /**
     * Below this short-window throughput the burst is concerning —
     * 90 % means &gt; 1 s of GC in 10 s, which is user-visible on
     * latency-sensitive paths.
     */
    public static final double CLUSTER_WARN_THROUGHPUT_PCT = 90.0;

    /**
     * Below this short-window throughput the burst is significant —
     * 70 % means &gt; 3 s of GC in 10 s, which kills user-visible
     * latency for anything happening in that window.
     */
    public static final double CLUSTER_SIGNIFICANT_THROUGHPUT_PCT = 70.0;

    private PauseAnalytics() {}

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        out.add(highPauseTime(agg));
        out.add(applicationThroughput(agg));
        out.add(pauseClustering(agg));
        out.add(pauseDistribution(agg));
    }

    /** Worst-window summary returned by {@link #worstThroughputWindow}. */
    private record WindowStat(double startSec,
                              double throughputPct,
                              int    pauseCount,
                              double stwSec) {}

    // ---- High pause time ----------------------------------------------------

    private static AnalyticsFinding highPauseTime(AnalyticsAggregation agg) {
        List<double[]> events = agg.getPauseEvents();
        if (events.isEmpty()) {
            return new AnalyticsFinding(GROUP, "High pause time", Impact.OK,
                    "No stop-the-world pause events recorded.", "");
        }
        double maxSec = 0.0;
        long over200 = 0;
        long over1000 = 0;
        for (double[] e : events) {
            double s = e[1];
            if (s > maxSec) maxSec = s;
            if (s * 1000.0 > HIGH_PAUSE_WARN_MS)        over200++;
            if (s * 1000.0 > HIGH_PAUSE_SIGNIFICANT_MS) over1000++;
        }
        double maxMs = maxSec * 1000.0;

        Impact impact;
        if (maxMs > HIGH_PAUSE_SIGNIFICANT_MS)      impact = Impact.SIGNIFICANT;
        else if (maxMs > HIGH_PAUSE_WARN_MS)        impact = Impact.CONCERNING;
        else                                        impact = Impact.OK;

        String message = String.format(Locale.ROOT,
                "Max pause %s ms. %d pause(s) exceeded %.0f ms, %d pause(s) "
                + "exceeded %.0f ms (out of %d total).",
                formatMs(maxMs),
                over200, HIGH_PAUSE_WARN_MS,
                over1000, HIGH_PAUSE_SIGNIFICANT_MS,
                events.size());

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "High pause time", impact, message, "");
        }
        String details = """
                Pauses above 200 ms are user-noticeable in latency-sensitive \
                services; pauses above one second are disruptive on almost \
                any workload.

                Common drivers:
                  • Heap pressure forcing mixed / full collections — see the \
                Heap usage findings above for full GC counts and \
                heap-too-small indicators.
                  • Reference processing dominating a pause — soft / weak / \
                final / phantom reference queues that have grown out of \
                proportion.
                  • Long young pauses from a survivor that's too small \
                (premature promotion) or a young gen sized too large.
                  • Off-heap stalls picked up by the GC threads — see the \
                CPU findings for kernel-time pressure.

                Mitigations vary by collector. For G1, lower \
                -XX:MaxGCPauseMillis only if the live set permits, and \
                consider raising -Xmx if mixed cycles are dragging the \
                tail. For ZGC / Shenandoah, the worst pauses are usually \
                reference processing or root scanning — capture a JFR \
                recording during a bad pause to find the dominant phase.""";
        return new AnalyticsFinding(GROUP, "High pause time", impact, message, details);
    }

    // ---- Application throughput --------------------------------------------

    private static AnalyticsFinding applicationThroughput(AnalyticsAggregation agg) {
        List<double[]> events = agg.getPauseEvents();
        double duration = agg.getLogDurationSec();
        if (events.isEmpty() || duration <= 0.0) {
            return new AnalyticsFinding(GROUP, "Application throughput", Impact.OK,
                    "Not enough data to assess throughput.", "");
        }
        double totalPause = 0.0;
        for (double[] e : events) totalPause += e[1];
        double overallPct = (1.0 - totalPause / duration) * 100.0;

        // Worst sliding-window throughput. Only meaningful if the log is
        // long enough to host a full window — otherwise we report the
        // overall figure and skip the windowed check.
        Double worstWindowPct = null;
        Double worstWindowStartSec = null;
        if (duration >= THROUGHPUT_WINDOW_SEC) {
            WindowStat worst = worstThroughputWindow(events, agg,
                    THROUGHPUT_WINDOW_SEC, THROUGHPUT_WINDOW_STEP_SEC);
            worstWindowStartSec = worst.startSec();
            worstWindowPct      = worst.throughputPct();
        }

        Impact impact;
        String verdict;
        if (overallPct < THROUGHPUT_TARGET_PCT) {
            impact  = Impact.SIGNIFICANT;
            verdict = "Below target overall";
        } else if (worstWindowPct != null && worstWindowPct < THROUGHPUT_TARGET_PCT) {
            impact  = Impact.CONCERNING;
            verdict = "Below target in at least one 30-minute window";
        } else {
            impact  = Impact.OK;
            verdict = "Above target";
        }

        StringBuilder msg = new StringBuilder();
        msg.append(verdict).append(". Overall throughput ");
        msg.append(String.format(Locale.ROOT, "%.2f%%", overallPct));
        msg.append(String.format(Locale.ROOT,
                " (target %.0f%%); total STW %.2fs over %.1fs of runtime",
                THROUGHPUT_TARGET_PCT, totalPause, duration));
        if (worstWindowPct != null) {
            msg.append(String.format(Locale.ROOT,
                    "; worst 30-minute window %.2f%% starting at t=%.1fs",
                    worstWindowPct, worstWindowStartSec));
        }
        msg.append('.');

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "Application throughput",
                    impact, msg.toString(), "");
        }
        String details = """
                Application throughput is the fraction of wall-clock time \
                the application's threads got to run — i.e., 1 minus the \
                fraction of time spent in stop-the-world GC pauses. The \
                conventional target for server-side workloads is 95 %%.

                Two checks are layered here:
                  • The overall figure across the whole log — under 95 %% \
                here means the collector spent more than 5 %% of total \
                wall time pausing the application.
                  • A 30-minute sliding-window check — catches transient \
                bad periods that an averaged figure would smooth over. \
                The window stride is one minute, so the worst window we \
                report is the actual worst window (within a minute of \
                resolution).

                If the overall figure is fine but a 30-minute window \
                dropped below 95 %%, that's typically a cluster of mixed \
                or full GCs (heap pressure spike), a single very long \
                pause (full GC), or a burst of pauses driven by an \
                allocation spike. Cross-reference with the heap-too-small \
                indicators and pause-time findings to locate the cause.""";
        return new AnalyticsFinding(GROUP, "Application throughput",
                impact, msg.toString(), details);
    }

    /**
     * Worst sliding-window throughput across the log. Generic over
     * window length and step so both the application-throughput
     * (30-minute) and pause-clustering (10-second) checks share one
     * implementation. The pause list is in arrival order — monotonic
     * in time for our purposes — so a two-pointer scan computes every
     * window's running sum in O(n) total.
     * <p>
     * Returns the window start, the throughput percentage (low is
     * worst), the count of pauses inside the window, and the total
     * STW seconds the window contained.
     */
    private static WindowStat worstThroughputWindow(List<double[]> events,
                                                    AnalyticsAggregation agg,
                                                    double windowSec,
                                                    double stepSec) {
        double duration = agg.getLogDurationSec();
        // Anchor windows to the first observed event so we don't waste
        // iterations on negative-time slack at the head of the log.
        double startMin = events.get(0)[0];
        double startMax = startMin + duration - windowSec;
        if (startMax < startMin) {
            // Log shorter than one window; nothing meaningful to scan.
            return new WindowStat(startMin, 100.0, 0, 0.0);
        }

        int lo = 0, hi = 0;
        double sumInWindow = 0.0;
        double worstPct   = Double.POSITIVE_INFINITY;
        double worstStart = startMin;
        int    worstCount = 0;
        double worstStw   = 0.0;

        for (double t = startMin; t <= startMax + 1e-6; t += stepSec) {
            double windowEnd = t + windowSec;
            // Advance lo past events that fell out of the left edge.
            while (lo < events.size() && events.get(lo)[0] < t) {
                sumInWindow -= events.get(lo)[1];
                lo++;
            }
            // Advance hi to include events inside the right edge.
            while (hi < events.size() && events.get(hi)[0] < windowEnd) {
                sumInWindow += events.get(hi)[1];
                hi++;
            }
            // Floating-point drift safety — the running sum can dip
            // microscopically negative when events drop out.
            double effective = Math.max(0.0, sumInWindow);
            double pct = (1.0 - effective / windowSec) * 100.0;
            if (pct < worstPct) {
                worstPct   = pct;
                worstStart = t;
                worstCount = hi - lo;
                worstStw   = effective;
            }
        }
        return new WindowStat(worstStart, worstPct, worstCount, worstStw);
    }

    // ---- Pause clustering ---------------------------------------------------

    /**
     * Detect short-window pause clustering — i.e., bursts of stop-the-world
     * activity that the 30-minute throughput check averages away. Uses the
     * same sliding-window machinery at a 10-second window / 1-second step,
     * so a chain of normal-length young pauses fired back-to-back during an
     * allocation spike still surfaces here even when overall throughput is
     * fine.
     */
    private static AnalyticsFinding pauseClustering(AnalyticsAggregation agg) {
        List<double[]> events = agg.getPauseEvents();
        double duration = agg.getLogDurationSec();
        if (events.isEmpty() || duration <= 0.0) {
            return new AnalyticsFinding(GROUP, "Pause clustering", Impact.OK,
                    "Not enough data to assess pause clustering.", "");
        }
        if (duration < CLUSTER_WINDOW_SEC * 2) {
            return new AnalyticsFinding(GROUP, "Pause clustering", Impact.OK,
                    "Log too short to assess pause clustering.", "");
        }

        WindowStat worst = worstThroughputWindow(events, agg,
                CLUSTER_WINDOW_SEC, CLUSTER_WINDOW_STEP_SEC);

        Impact impact;
        String verdict;
        if (worst.throughputPct() >= CLUSTER_WARN_THROUGHPUT_PCT) {
            impact  = Impact.OK;
            verdict = "No significant clustering";
        } else if (worst.throughputPct() >= CLUSTER_SIGNIFICANT_THROUGHPUT_PCT) {
            impact  = Impact.CONCERNING;
            verdict = "Pause cluster detected";
        } else {
            impact  = Impact.SIGNIFICANT;
            verdict = "Severe pause cluster";
        }

        String message = String.format(Locale.ROOT,
                "%s. Worst %.0fs window: %.1f%% throughput (%d pause%s, "
                + "%.2fs STW) starting at t=%.1fs.",
                verdict, CLUSTER_WINDOW_SEC, worst.throughputPct(),
                worst.pauseCount(), (worst.pauseCount() == 1 ? "" : "s"),
                worst.stwSec(), worst.startSec());

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "Pause clustering",
                    impact, message, "");
        }
        String details = """
                Pause clustering detects short bursts of stop-the-world \
                activity that the longer-window checks above smooth away. \
                The 30-minute throughput check averages a 10-second cluster \
                across 1800 seconds, so a healthy overall figure can hide \
                spikes that block user-facing requests entirely while they \
                last.

                The window above is the worst 10-second slice in the log. \
                Common causes:
                  • Allocation spike — a request that allocates aggressively \
                (large response, deserialization burst, batch import) drives \
                a chain of young pauses back-to-back.
                  • Survivor pressure — premature promotion (see Heap usage) \
                forces extra mixed or full work in succession.
                  • Mixed-cycle clustering — a sequence of mixed collections \
                firing inside one window after a concurrent-mark cycle. \
                Cross-reference with the Mixed collections histogram.
                  • A single very long pause — if the pause count above is \
                1, the window's throughput is dominated by one event; the \
                High pause time finding will say more.

                Investigation:
                  • Look at the allocation-rate chart around the reported \
                timestamp; allocation spikes usually show as obvious peaks.
                  • Look at the pause-time chart around the same timestamp \
                to see whether the cluster is many small pauses or a few \
                large ones.
                  • Cross-check with the GC-cause histogram to see what \
                kind of work the cluster contained.""";
        return new AnalyticsFinding(GROUP, "Pause clustering",
                impact, message, details);
    }

    // ---- Pause distribution -------------------------------------------------

    private static AnalyticsFinding pauseDistribution(AnalyticsAggregation agg) {
        List<double[]> events = agg.getPauseEvents();
        if (events.isEmpty()) {
            return new AnalyticsFinding(GROUP, "Pause distribution", Impact.OK,
                    "No stop-the-world pause events recorded.", "");
        }
        double[] durationsMs = new double[events.size()];
        for (int i = 0; i < events.size(); i++) {
            durationsMs[i] = events.get(i)[1] * 1000.0;
        }
        Arrays.sort(durationsMs);

        double p10 = percentile(durationsMs, 10.0);
        double p50 = percentile(durationsMs, 50.0);
        double p90 = percentile(durationsMs, 90.0);
        double p99 = percentile(durationsMs, 99.0);
        double max = durationsMs[durationsMs.length - 1];

        // Descriptive view — severity mirrors the high-pause-time finding's
        // headline (no sense badging the same problem twice). p99 is the
        // best single number to grade on because it's stable to outliers.
        Impact impact;
        if (p99 > HIGH_PAUSE_SIGNIFICANT_MS)      impact = Impact.SIGNIFICANT;
        else if (p99 > HIGH_PAUSE_WARN_MS)        impact = Impact.CONCERNING;
        else                                      impact = Impact.OK;

        String message = String.format(Locale.ROOT,
                "p10=%s ms, p50=%s ms, p90=%s ms, p99=%s ms, max=%s ms (n=%d).",
                formatMs(p10), formatMs(p50), formatMs(p90), formatMs(p99),
                formatMs(max), events.size());

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "Pause distribution",
                    impact, message, "");
        }
        String details = """
                The pause distribution describes how stop-the-world pauses \
                spread out across the log. The percentiles are read in pairs:
                  • p10 → p50 — the body of the distribution. Most pauses \
                land in this band; a wide gap here means a chatty collector \
                with inconsistent young-pause sizing.
                  • p50 → p90 — the typical operating range. A 90th \
                percentile much higher than the median means even \
                "ordinary" runs include slow pauses.
                  • p90 → p99 → max — the tail. A heavy tail means rare \
                pauses are dragging down the experience of a small but \
                visible fraction of operations.

                Severity here is graded on p99 (rather than max) because \
                p99 is stable to single-event outliers. If p99 is fine but \
                max is high, you have a small number of bad events worth \
                investigating individually; if p99 itself is high, the \
                tail is the population to fix.""";
        return new AnalyticsFinding(GROUP, "Pause distribution",
                impact, message, details);
    }

    /**
     * Linear-interpolation percentile on a pre-sorted array, percentiles
     * given in {@code [0, 100]}. Matches numpy's default {@code linear}
     * method and gnuplot — the small differences from rank-based
     * percentile estimators don't matter at the resolution we report.
     */
    private static double percentile(double[] sorted, double pct) {
        if (sorted.length == 0) return 0.0;
        if (sorted.length == 1) return sorted[0];
        double rank = (pct / 100.0) * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        if (lo == hi) return sorted[lo];
        return sorted[lo] + (rank - lo) * (sorted[hi] - sorted[lo]);
    }

    /**
     * Pause-friendly numeric formatting — milliseconds as integers when
     * &ge; 10 ms, one decimal place otherwise. Sub-millisecond pauses
     * read as "0.x" rather than "0", which matters for ZGC sub-pauses.
     */
    private static String formatMs(double ms) {
        if (ms >= 10.0) return String.format(Locale.ROOT, "%.0f", ms);
        return String.format(Locale.ROOT, "%.1f", ms);
    }
}
