package com.kodewerk.jma.analytics.heap;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Heap-usage feature group analytics. Four checks, each tuned to a
 * specific GC-log fingerprint:
 * <ul>
 *   <li><b>Premature promotion</b> — fraction of survivor records where
 *       the calculated tenuring threshold dropped below the configured
 *       max. Above 10 % is concerning, above 15 % needs attention.</li>
 *   <li><b>Metaspace-threshold-triggered GC after warmup</b> — these
 *       are normal during class loading but signal a class-loading
 *       leak when they continue past the warmup window.</li>
 *   <li><b>Heap-too-small indicators</b> — combined count of full GCs,
 *       concurrent-mark aborts, concurrent-mark resets for overflow,
 *       and to-space exhausted events. Format mirrors Censum's so the
 *       same finding reads the same way to anyone migrating across
 *       tools.</li>
 *   <li><b>Heap stability</b> — first-half vs second-half mean
 *       post-collection occupancy. A growing floor is the canonical
 *       memory-leak signature.</li>
 * </ul>
 */
public final class HeapUsageAnalytics {

    private static final String GROUP = "Heap usage";

    /** Premature-promotion thresholds — directly from the user's spec. */
    private static final double PROMOTION_CONCERNING = 0.10;
    private static final double PROMOTION_SIGNIFICANT = 0.15;

    /**
     * Two-sided p-value cutoff for declaring the Mann-Kendall trend
     * statistically meaningful. Anything weaker than this we treat as
     * "stable" regardless of the sign of the slope.
     */
    private static final double STABILITY_P_THRESHOLD = 0.05;

    /**
     * Theil-Sen slope (MB/h) at or above which we tip from "trending
     * up" (concerning) into "leaking" (significant). Picked to match
     * the cadence at which slow leaks become operationally painful —
     * 10 MB/h is roughly a quarter-GB per day, hard to ignore on any
     * heap size.
     */
    private static final double STABILITY_LEAK_MB_PER_HOUR = 10.0;

    /**
     * Cap on samples passed to Mann-Kendall / Theil-Sen — both are
     * O(n²) and we don't want a 50 k-pause log spending seconds in
     * the analytic. Even-stride subsampling preserves time ordering
     * and the trend signal; the rank-based test is robust to the
     * resulting density change.
     */
    private static final int STABILITY_MAX_SAMPLES = 5000;

    /**
     * "After warmup" cutoff — first 60 s of observed events are class-
     * loading dominated, and a metaspace-threshold GC there is normal
     * startup behaviour. Past this point the same cause is a class
     * loading leak fingerprint.
     */
    public static final double WARMUP_WINDOW_SEC = 60.0;

    private HeapUsageAnalytics() {}

    /**
     * GC-frequency threshold (events/second) above which the Parallel /
     * Serial heap-too-small check considers the rate concerning. More
     * than 5 collections in a single one-second window is the rule of
     * thumb: a healthy heap pauses for GC dozens of times per minute,
     * not several times per second.
     */
    public static final int GC_FREQUENCY_THRESHOLD_PER_SEC = 5;

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        String collector = agg.detectCollector();
        // Premature promotion is currently only meaningful on G1 logs;
        // for Parallel / Serial we defer the analytic until we have a
        // better proxy (high recovery rate in tenured) and emit nothing
        // rather than a misleading reading. Generational ZGC resets
        // tenuring on every cycle so the tenuring-age fingerprint is
        // unavailable there — deferred per the same reasoning.
        if ("g1".equals(collector)) {
            out.add(prematurePromotion(agg));
        }
        out.add(metaspaceTriggered(agg));
        if ("zgc".equals(collector)) {
            // ZGC has no Full GC or G1 marking-overflow events in steady
            // state, and is sized too coarsely for the 1-second-bucket
            // GC-frequency check (its cycles span seconds). Allocation
            // stalls are the canonical "heap too small / concurrent
            // cycle can't keep up" signal — the mutator literally
            // blocked waiting for the collector to free pages.
            out.add(heapTooSmallByAllocationStalls(agg));
        } else if ("parallel".equals(collector) || "serial".equals(collector)) {
            out.add(heapTooSmallByGcFrequency(agg));
        } else {
            out.add(heapTooSmall(agg));
        }
        out.add(heapStability(agg));
    }

    // ---- Premature promotion ------------------------------------------------

    private static AnalyticsFinding prematurePromotion(AnalyticsAggregation agg) {
        long total = agg.getSurvivorRecords();
        if (total == 0) {
            return new AnalyticsFinding(GROUP, "Premature promotion",
                    Impact.OK,
                    "No survivor records available — premature promotion not assessed.",
                    "");
        }
        long premature = agg.getPrematurePromotions();
        double ratio = premature / (double) total;
        Impact impact;
        if (ratio >= PROMOTION_SIGNIFICANT)      impact = Impact.SIGNIFICANT;
        else if (ratio >= PROMOTION_CONCERNING)  impact = Impact.CONCERNING;
        else                                     impact = Impact.OK;

        String message = String.format(Locale.ROOT,
                "%d of %d young pauses (%.1f%%) showed survivor pressure pushing "
                + "the calculated tenuring threshold below the configured maximum.",
                premature, total, ratio * 100.0);
        String details = """
                Premature promotion happens when the survivor space fills up \
                during a young collection: the JVM lowers the tenuring \
                threshold and copies objects into the old generation \
                earlier than the configured -XX:MaxTenuringThreshold would \
                normally allow. The promoted objects then have to be \
                reclaimed by an old-gen / mixed / full GC, which is far \
                more expensive than letting them die young in eden.

                Above 10 % is concerning, above 15 % needs attention.

                Common fixes:
                  • Increase the survivor space — -XX:SurvivorRatio (lower \
                ratio = larger survivors), or grow the young generation \
                via -XX:NewRatio / -Xmn so the survivors scale with it.
                  • Increase -XX:MaxTenuringThreshold so short-lived \
                objects get more chances to die young (only useful if \
                survivors are not already overflowing).
                  • Investigate spiky allocation — bursts of medium-lived \
                objects (e.g. response buffers held briefly across a \
                request) overwhelm the survivor space even when the \
                steady-state survivor occupancy is low.""";
        return new AnalyticsFinding(GROUP, "Premature promotion",
                impact, message, details);
    }

    // ---- Metaspace-threshold-triggered GC -----------------------------------

    private static AnalyticsFinding metaspaceTriggered(AnalyticsAggregation agg) {
        long afterWarmup = agg.getMetaspaceTriggeredAfterWarmupCount();
        long total       = agg.getMetaspaceTriggeredCount();
        if (total == 0) {
            return new AnalyticsFinding(GROUP, "Metaspace-triggered GCs",
                    Impact.OK,
                    "No metaspace-threshold-triggered GCs observed.",
                    "");
        }
        if (afterWarmup == 0) {
            return new AnalyticsFinding(GROUP, "Metaspace-triggered GCs",
                    Impact.OK,
                    total + " metaspace-threshold-triggered GC(s), all within the "
                            + "first " + ((int) WARMUP_WINDOW_SEC) + " s — normal class-loading behaviour.",
                    "");
        }
        return new AnalyticsFinding(GROUP, "Metaspace-triggered GCs",
                Impact.CONCERNING,
                String.format(Locale.ROOT,
                        "%d metaspace-threshold-triggered GC(s) after the %ds warmup window "
                        + "(%d total in the log).",
                        afterWarmup, (int) WARMUP_WINDOW_SEC, total),
                """
                Metaspace GCs are triggered when class metadata growth crosses \
                the current metaspace threshold. During application startup \
                this is normal — frameworks load classes lazily. After warmup \
                the same trigger is almost always a class-loading leak: \
                dynamic proxy / classloader churn, repeated reflection-based \
                deserialization, or framework reload paths leaking \
                ClassLoader instances.

                Investigation:
                  • -XX:+TraceClassLoading and -XX:+TraceClassUnloading (or \
                JFR's class-loading event) to find which classes keep \
                getting reloaded.
                  • jcmd <pid> GC.class_stats to see metaspace contents.
                  • jcmd <pid> VM.classloader_stats to find classloaders \
                with surprisingly many classes.

                Mitigation while you investigate:
                  • -XX:MaxMetaspaceSize=<size> caps growth so the leak \
                surfaces as an OutOfMemoryError instead of a slow death.
                  • -XX:MetaspaceSize=<size> raises the initial threshold \
                to reduce the early-life GC noise.""");
    }

    // ---- Heap too small (combined four-indicator finding) -------------------

    private static AnalyticsFinding heapTooSmall(AnalyticsAggregation agg) {
        long fullGc        = agg.getFullGcCount();
        long markAbort     = agg.getConcurrentMarkAbortedCount();
        long markReset     = agg.getConcurrentMarkResetForOverflowCount();
        long toSpaceOverfl = agg.getToSpaceExhaustedCount();
        long total         = agg.getTotalGcEvents();
        long worst         = Math.max(fullGc,
                              Math.max(markAbort,
                              Math.max(markReset, toSpaceOverfl)));

        String message = String.format(Locale.ROOT,
                "This log reported a total of %d GC events. Indicator counts: "
                + "%d Full GC events, %d concurrent-mark-abort events, "
                + "%d concurrent-mark-restart-for-overflow events, "
                + "%d to-space overflow events.",
                total, fullGc, markAbort, markReset, toSpaceOverfl);

        if (worst == 0) {
            return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                    Impact.OK, message, "");
        }
        // Promote to SIGNIFICANT once any single indicator fires more
        // than a handful of times — a single Full GC under pressure can
        // be tolerable, but five of them across a log is not.
        Impact impact = (worst >= 5) ? Impact.SIGNIFICANT : Impact.CONCERNING;

        String details = """
                There are a number of indicators that can suggest that your \
                heap is too small. These indicators show up in the GC logs \
                as an interrupted or aborted event:

                  1. Full GC events — every full GC is a sign that the \
                concurrent / young / mixed paths could not keep up.
                  2. Concurrent-mark abort — G1 gave up on a marking cycle, \
                usually because the heap filled before the cycle could \
                finish.
                  3. Concurrent-mark restart for overflow — the marking \
                stack overflowed and the cycle had to restart, also a \
                heap-pressure symptom.
                  4. To-space overflow / evacuation failure — G1 could not \
                find enough free regions to evacuate the collection set \
                into, forcing a costly recovery path.

                Mitigations (in rough order of safety):
                  • Raise -Xmx so the collector has more headroom.
                  • Lower -XX:InitiatingHeapOccupancyPercent (G1) so the \
                concurrent cycle starts earlier and has time to complete.
                  • Investigate allocation rate — a growing live set or \
                spiky allocation can overwhelm even a generously sized \
                heap.
                  • For G1, raise -XX:G1ReservePercent if to-space \
                exhaustion is the dominant indicator.""";
        return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                impact, message, details);
    }

    // ---- Heap too small — Parallel / Serial (GC-frequency based) -----------

    /**
     * Frequency-based heap-too-small check for the stop-the-world
     * generational collectors (Parallel, Serial). The G1 indicators
     * — concurrent-mark abort, reset-for-overflow, to-space exhausted
     * — don't exist here, so we lean on raw GC rate instead. A healthy
     * heap pauses dozens of times per minute; multiple GCs inside a
     * single second is a strong signal the heap is too small for the
     * working set.
     * <p>
     * Method: bucket pause-event start times into fixed 1-second slots
     * starting at the first observed event. Bucket count exceeding
     * {@link #GC_FREQUENCY_THRESHOLD_PER_SEC} flags that second as
     * violating. Severity escalates with the share of log time that
     * was violating, with a separate eye on the worst-second rate.
     */
    private static AnalyticsFinding heapTooSmallByGcFrequency(AnalyticsAggregation agg) {
        List<double[]> events = agg.getPauseEvents();
        double duration = agg.getLogDurationSec();
        if (events.size() < 5 || duration < 2.0) {
            return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                    Impact.OK,
                    "Not enough pause events to assess GC frequency.",
                    "");
        }

        double firstSec = events.get(0)[0];
        double lastSec  = events.get(events.size() - 1)[0];
        int bucketCount = Math.max(1, (int) Math.ceil(lastSec - firstSec) + 1);
        int[] buckets = new int[bucketCount];
        for (double[] e : events) {
            int idx = (int) Math.floor(e[0] - firstSec);
            if (idx < 0) idx = 0;
            if (idx >= bucketCount) idx = bucketCount - 1;
            buckets[idx]++;
        }
        int maxRate = 0;
        int violatingBuckets = 0;
        for (int b : buckets) {
            if (b > maxRate) maxRate = b;
            if (b > GC_FREQUENCY_THRESHOLD_PER_SEC) violatingBuckets++;
        }
        double violatingFraction = bucketCount > 0
                ? (double) violatingBuckets / bucketCount
                : 0.0;

        Impact impact;
        if (maxRate <= GC_FREQUENCY_THRESHOLD_PER_SEC) {
            impact = Impact.OK;
        } else if (violatingFraction >= 0.10) {
            // More than 10 % of the log spent over the threshold —
            // the heap pressure is sustained, not a one-off burst.
            impact = Impact.SIGNIFICANT;
        } else {
            impact = Impact.CONCERNING;
        }

        String message;
        if (impact == Impact.OK) {
            message = String.format(Locale.ROOT,
                    "No 1-second window had more than %d GC events "
                    + "(worst observed: %d events in one second across %d analysed).",
                    GC_FREQUENCY_THRESHOLD_PER_SEC, maxRate, bucketCount);
        } else {
            message = String.format(Locale.ROOT,
                    "GC fired more than %d times in %d second(s) of the log "
                    + "(worst observed: %d events in one second across %d analysed, "
                    + "%.1f%% of seconds exceeded the threshold).",
                    GC_FREQUENCY_THRESHOLD_PER_SEC, violatingBuckets,
                    maxRate, bucketCount, violatingFraction * 100.0);
        }

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                    impact, message, "");
        }
        String details = """
                Parallel and Serial collectors don't expose the same
                "fell behind" signals G1 does (concurrent-mark abort,
                to-space exhausted, reset-for-overflow). The most
                reliable substitute is raw GC frequency: a healthy
                heap pauses for GC dozens of times per minute, with
                comfortable application-time gaps between collections.
                Multiple collections inside a single second mean the
                heap is filling faster than the application has time
                to do useful work — almost always heap-size pressure.

                Mitigations (in rough order of safety):
                  • Raise -Xmx so the young generation has more room \
                between collections.
                  • If the young generation alone is undersized, raise \
                -XX:NewRatio / -Xmn — short-lived objects then get \
                more chances to die before a young GC kicks in.
                  • If allocation rate is high but live set is small, \
                investigate whether the application can be made to \
                allocate less aggressively (object reuse, primitive \
                arrays, off-heap buffers).
                  • Consider switching to a low-pause collector (G1, \
                ZGC) if pause latency matters more than throughput.""";
        return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                impact, message, details);
    }

    // ---- Heap too small — ZGC (allocation-stall based) ----------------------

    /**
     * Stall-rate threshold (events / minute) at or above which the ZGC
     * heap-too-small finding tips from CONCERNING into SIGNIFICANT.
     * Picked to match the operational rule of thumb that any sustained
     * stall rate measurable in stalls-per-minute is intolerable: a
     * healthy ZGC log carries zero stalls. One a minute is loud enough
     * to be noticed by end-users on a latency-sensitive workload.
     */
    private static final double ALLOC_STALL_RATE_SIGNIFICANT_PER_MIN = 1.0;

    /**
     * Allocation-stall-based heap-too-small check for generational ZGC.
     * Counts {@link com.kodewerk.gcsee.event.GCCause#ALLOC_STALL}
     * events — collections where a mutator thread blocked waiting for
     * the collector to free pages. ZGC is designed to keep up
     * concurrently; any stall means it didn't, almost always because
     * the heap is too small for the allocation rate (or the soft-max
     * ceiling is undersized) so the cycle started too late or
     * couldn't finish in time.
     */
    private static AnalyticsFinding heapTooSmallByAllocationStalls(AnalyticsAggregation agg) {
        long stalls = agg.getAllocationStallCount();
        double logDur = agg.getLogDurationSec();
        double perMinute = (logDur > 0.0) ? stalls * 60.0 / logDur : 0.0;

        if (stalls == 0) {
            return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                    Impact.OK,
                    "No allocation stalls observed — ZGC kept up with the application's allocation rate.",
                    "");
        }

        Impact impact = (perMinute >= ALLOC_STALL_RATE_SIGNIFICANT_PER_MIN)
                ? Impact.SIGNIFICANT
                : Impact.CONCERNING;

        String message;
        if (logDur > 0.0) {
            message = String.format(Locale.ROOT,
                    "%d allocation stall(s) over %.1f s of log (%.2f stalls / minute). "
                    + "Mutator threads were blocked waiting for the collector to free pages.",
                    stalls, logDur, perMinute);
        } else {
            message = String.format(Locale.ROOT,
                    "%d allocation stall(s) observed. Mutator threads were blocked "
                    + "waiting for the collector to free pages.",
                    stalls);
        }

        String details = """
                ZGC schedules its work concurrently — pause times are bounded \
                regardless of heap size, and the mutator threads only block for \
                the three short stop-the-world sub-pauses per cycle. An allocation \
                stall (GCCause = "Allocation Stall") fires when a mutator asks \
                for memory and ZGC has none free: the concurrent cycle didn't \
                start early enough, or didn't finish fast enough, to keep ahead \
                of the allocation rate. The thread blocks for the duration of \
                the next cycle's relocation phase — orders of magnitude longer \
                than a normal STW pause.

                On a healthy ZGC log this counter is zero. Any non-zero count \
                is a heap-sizing or soft-max signal:

                Mitigations (in rough order of safety):
                  • Raise -Xmx so each cycle has more room to free before the \
                next one needs to start.
                  • Lower -XX:SoftMaxHeapSize if you've capped it artificially \
                — the soft-max controls when ZGC starts the next cycle, and \
                an undersized soft-max can stall the application even though \
                physical memory is available.
                  • For generational ZGC, check that the young generation is \
                large enough — most allocations should die young; if survivor \
                pressure is high the old-gen cycle has more to do per pass.
                  • Investigate allocation rate spikes — short bursts can \
                outrun even a generously sized ZGC. The "Heap Stability" \
                finding above flags steady-state growth; bursty allocation \
                won't show there but will produce stalls.""";
        return new AnalyticsFinding(GROUP, "Heap-too-small indicators",
                impact, message, details);
    }

    // ---- Heap stability (Mann-Kendall + Theil-Sen) --------------------------

    /**
     * Heap-stability gauge based on a Mann-Kendall trend test over the
     * post-collection heap-occupancy series. A positive, statistically
     * significant trend means the live set is monotonically rising —
     * the canonical memory-leak signature.
     * <p>
     * Reports the Mann-Kendall S statistic, the standardised z-score,
     * the two-sided p-value, the Theil-Sen slope (median of pairwise
     * slopes) in MB/h, and a categorical verdict.
     * <p>
     * Mann-Kendall is non-parametric (rank-based) and tolerant of
     * outliers and non-normal distributions, which suits noisy GC-log
     * data well. Theil-Sen is the matching robust slope estimator.
     * <p>
     * <b>Why post-GC and not post-Full-GC:</b> modern collectors (G1,
     * ZGC, Shenandoah) may run for hours without a Full GC, so
     * restricting to Full GCs would leave most logs without a verdict.
     * The trade-off is that post-young-GC samples include short-lived
     * garbage that has been promoted but not yet reclaimed; that adds
     * noise, but Mann-Kendall tolerates it and the rank-based test
     * still surfaces a true monotonic trend underneath it.
     */
    private static AnalyticsFinding heapStability(AnalyticsAggregation agg) {
        List<double[]> raw = agg.getHeapOccupancySamples();
        if (raw.size() < 20) {
            return new AnalyticsFinding(GROUP, "Heap stability",
                    Impact.OK,
                    "Not enough heap-occupancy samples (need ≥ 20) to assess stability.",
                    "");
        }

        List<double[]> samples = subsample(raw, STABILITY_MAX_SAMPLES);
        int n = samples.size();

        // ---- Mann-Kendall S = Σ sign(y_j − y_i) for i<j ---------------------
        long s = 0;
        for (int i = 0; i < n - 1; i++) {
            double yi = samples.get(i)[1];
            for (int j = i + 1; j < n; j++) {
                double yj = samples.get(j)[1];
                if      (yj > yi) s++;
                else if (yj < yi) s--;
            }
        }

        // ---- Variance with tie correction -----------------------------------
        // Var(S) = n(n-1)(2n+5)/18 − Σ_t t(t-1)(2t+5)/18 over tie groups t.
        double varS = ((double) n * (n - 1) * (2 * n + 5)) / 18.0;
        Map<Double, Integer> tieCounts = new HashMap<>();
        for (double[] sample : samples) {
            tieCounts.merge(sample[1], 1, Integer::sum);
        }
        for (int t : tieCounts.values()) {
            if (t > 1) varS -= ((double) t * (t - 1) * (2 * t + 5)) / 18.0;
        }
        if (varS <= 0.0) {
            // Degenerate (all values equal, or only ties). Trivially stable.
            return new AnalyticsFinding(GROUP, "Heap stability",
                    Impact.OK,
                    "Post-collection heap occupancy is constant — no trend to assess.",
                    "");
        }

        // ---- Standardised z-score with continuity correction ---------------
        double z;
        if      (s > 0) z = (s - 1) / Math.sqrt(varS);
        else if (s < 0) z = (s + 1) / Math.sqrt(varS);
        else            z = 0.0;
        double pValue = 2.0 * (1.0 - normalCdf(Math.abs(z)));

        // ---- Theil-Sen slope (median of pairwise slopes), MB/sec ----------
        // Allocate the worst-case capacity then fill only the valid pairs
        // (timestamps that aren't equal). Sort the filled prefix and pick
        // the median.
        int slopeCap = n * (n - 1) / 2;
        double[] slopes = new double[slopeCap];
        int idx = 0;
        for (int i = 0; i < n - 1; i++) {
            double ti = samples.get(i)[0];
            double yi = samples.get(i)[1];
            for (int j = i + 1; j < n; j++) {
                double tj = samples.get(j)[0];
                if (tj == ti) continue;
                slopes[idx++] = (samples.get(j)[1] - yi) / (tj - ti);
            }
        }
        Arrays.sort(slopes, 0, idx);
        double slopePerSec = (idx > 0) ? slopes[idx / 2] : 0.0;
        double slopePerHour = slopePerSec * 3600.0;

        // ---- Categorical verdict -------------------------------------------
        String verdict;
        Impact impact;
        if (pValue >= STABILITY_P_THRESHOLD) {
            verdict = "Stable (no statistically significant trend)";
            impact  = Impact.OK;
        } else if (slopePerHour <= 0.0) {
            verdict = "Stable (significant downward trend)";
            impact  = Impact.OK;
        } else if (slopePerHour < STABILITY_LEAK_MB_PER_HOUR) {
            verdict = "Trending up";
            impact  = Impact.CONCERNING;
        } else {
            verdict = "Leaking";
            impact  = Impact.SIGNIFICANT;
        }

        String message = String.format(Locale.ROOT,
                "%s. Mann-Kendall S=%d, z=%.2f, p=%s; Theil-Sen slope %s MB/h "
                + "(%d samples%s).",
                verdict, s, z,
                formatPValue(pValue),
                formatSlope(slopePerHour),
                n,
                (raw.size() > n) ? ", subsampled from " + raw.size() : "");

        if (impact == Impact.OK) {
            return new AnalyticsFinding(GROUP, "Heap stability",
                    impact, message, "");
        }
        String details = """
                Heap stability is gauged by a Mann-Kendall trend test over the \
                post-collection heap-occupancy series. The test is rank-based \
                and non-parametric, so it tolerates the noise inherent in \
                young-GC samples (short-lived garbage that has been promoted \
                but not yet reclaimed). Theil-Sen — the median of pairwise \
                slopes — is the matching robust slope estimator.

                A positive, statistically significant trend means the live \
                set is monotonically rising over the observed period. The \
                canonical reading is "memory leak", but the same signature \
                is produced by genuine workload changes (a config bump that \
                pre-loads more data, autoscaling resizing the live set) — \
                the test only describes shape. Root-cause work still needs \
                evidence from the application itself.

                Investigation:
                  • Capture a heap dump near the end of the log and compare \
                dominator trees with one taken near the start. The class \
                with the largest growth is usually the culprit.
                  • -XX:+HeapDumpOnOutOfMemoryError gives a free dump if \
                the leak is severe enough to OOM.
                  • Object retention often shows up first in caches with \
                no eviction, listener lists not unregistering, or \
                ThreadLocal values held by long-lived pools.""";
        return new AnalyticsFinding(GROUP, "Heap stability",
                impact, message, details);
    }

    /** Even-stride subsample preserving time ordering. */
    private static List<double[]> subsample(List<double[]> input, int maxSize) {
        if (input.size() <= maxSize) return input;
        List<double[]> out = new ArrayList<>(maxSize);
        double step = input.size() / (double) maxSize;
        for (int i = 0; i < maxSize; i++) {
            out.add(input.get((int) (i * step)));
        }
        return out;
    }

    /** Standard normal CDF via Abramowitz & Stegun approximation of erf. */
    private static double normalCdf(double x) {
        return 0.5 * (1.0 + erf(x / Math.sqrt(2.0)));
    }

    /**
     * Abramowitz & Stegun 7.1.26 polynomial approximation of erf —
     * max absolute error ~1.5e-7, plenty for a p-value report.
     */
    private static double erf(double x) {
        double sign = (x >= 0.0) ? 1.0 : -1.0;
        double ax = Math.abs(x);
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        double t  = 1.0 / (1.0 + p * ax);
        double y  = 1.0
                  - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1)
                    * t * Math.exp(-ax * ax);
        return sign * y;
    }

    private static String formatPValue(double p) {
        if (p < 0.001) return "<0.001";
        return String.format(Locale.ROOT, "%.3f", p);
    }

    private static String formatSlope(double mbPerHour) {
        return String.format(Locale.ROOT, "%+.1f", mbPerHour);
    }
}
