package com.kodewerk.jma.analytics.gccause;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.List;
import java.util.Locale;

/**
 * GC-cause feature group analytics. Reads the cause-related fields the
 * {@link AnalyticsAggregation} collects (System.gc timestamps, GCLocker
 * count, full-GC count and pause total, other explicit triggers) and
 * appends one finding per analytic to the supplied list.
 * <p>
 * Each analytic is a small static method returning a single
 * {@link AnalyticsFinding} (or null when not applicable). The order
 * inside {@link #evaluate} controls the rendering order in the
 * "GC cause" section of the analytics page — most-likely-to-fire
 * checks first so the user sees the headline issue without scrolling.
 */
public final class GCCauseAnalytics {

    /** Section heading the frontend uses to bucket these findings. */
    private static final String GROUP = "GC cause";

    /** CV threshold below which we call a series of intervals "periodic". */
    private static final double PERIODIC_CV_THRESHOLD = 0.30;

    /** Minimum number of System.gc() calls before we look for a period. */
    private static final int PERIODICITY_MIN_SAMPLES = 5;

    private GCCauseAnalytics() {}

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        out.add(systemGc(agg));
        AnalyticsFinding periodic = systemGcPeriodicity(agg);
        if (periodic != null) out.add(periodic);
        AnalyticsFinding explicit = otherExplicitGc(agg);
        if (explicit != null) out.add(explicit);
        out.add(gcLocker(agg));
        out.add(fullGc(agg));
    }

    // ---- System.gc() presence -----------------------------------------------

    private static AnalyticsFinding systemGc(AnalyticsAggregation agg) {
        int n = agg.getSystemGcTimestamps().size();
        if (n == 0) {
            return new AnalyticsFinding(GROUP, "System.gc() calls", Impact.OK,
                    "No System.gc() calls observed.", "");
        }
        String message = n + " System.gc() call" + (n == 1 ? "" : "s")
                + " observed in the log.";
        String details = """
                System.gc() forces a stop-the-world collection that — depending on \
                collector flags — can escalate to a full GC. Common sources include:
                  • Application code calling System.gc() directly.
                  • RMI Distributed GC (sun.rmi.dgc.client.gcInterval / \
                sun.rmi.dgc.server.gcInterval, default 1 hour, but often lowered \
                by frameworks).
                  • Direct ByteBuffer reclamation on older JDKs.
                  • Profilers, agents, or container management code attached in \
                production (Tomcat ResourceLeakDetector, jmap -histo:live, etc.).

                Mitigations:
                  • -XX:+ExplicitGCInvokesConcurrent — make System.gc() trigger a \
                concurrent G1 / CMS cycle instead of a full GC.
                  • -XX:+DisableExplicitGC — make the call a no-op (verify no \
                RMI client depends on it first).
                  • Raise -Dsun.rmi.dgc.client.gcInterval and \
                -Dsun.rmi.dgc.server.gcInterval if RMI DGC is the source.""";
        return new AnalyticsFinding(GROUP, "System.gc() calls",
                Impact.CONCERNING, message, details);
    }

    // ---- System.gc() periodicity --------------------------------------------

    private static AnalyticsFinding systemGcPeriodicity(AnalyticsAggregation agg) {
        List<Double> ts = agg.getSystemGcTimestamps();
        if (ts.size() < PERIODICITY_MIN_SAMPLES) return null;

        // Pairwise intervals between consecutive System.gc() calls.
        double[] intervals = new double[ts.size() - 1];
        for (int i = 1; i < ts.size(); i++) {
            intervals[i - 1] = ts.get(i) - ts.get(i - 1);
        }

        double sum = 0.0;
        for (double d : intervals) sum += d;
        double mean = sum / intervals.length;

        // Reject implausible periods up front — a "period" of 30ms is
        // either parser noise or back-to-back collections, not a timer.
        if (mean < 1.0 || mean > 86400.0) return null;

        double variance = 0.0;
        for (double d : intervals) variance += (d - mean) * (d - mean);
        variance /= intervals.length;
        double stddev = Math.sqrt(variance);
        double cv = stddev / mean;
        if (cv > PERIODIC_CV_THRESHOLD) return null;

        String suspect = suspectedPeriodicSource(mean);
        String message = String.format(Locale.ROOT,
                "%d System.gc() calls at near-constant intervals (mean %.1fs, "
                + "coefficient of variation %.2f).",
                ts.size(), mean, cv);
        String details = ("Suspected source: " + suspect + ".\n\n")
                + """
                A constant-interval System.gc() pattern is almost always a \
                configuration issue rather than application logic. The usual \
                culprits are:
                  • RMI Distributed GC — its client/server intervals default to \
                1 hour but are often lowered by frameworks or operators. The \
                classic symptom is exactly this: hourly (or 60s / 120s) \
                System.gc() calls regardless of load.
                  • A scheduled thread (Timer, ScheduledExecutorService, cron \
                inside the app) calling System.gc() — usually leftover from \
                old "memory hygiene" tuning.
                  • Older direct-buffer cleanup paths in code that allocates \
                NIO ByteBuffers under pressure.

                Fix:
                  • -Dsun.rmi.dgc.client.gcInterval=3600000 and \
                -Dsun.rmi.dgc.server.gcInterval=3600000 to restore the default \
                hourly cadence (or set higher to effectively disable it).
                  • -XX:+DisableExplicitGC to make the call a no-op. Safe when \
                no RMI client relies on the timely DGC cycle.
                  • -XX:+ExplicitGCInvokesConcurrent if you have to keep the \
                call but want to avoid the full GC.
                  • Audit application code for Runtime.getRuntime().gc() / \
                System.gc() inside scheduled tasks.""";
        return new AnalyticsFinding(GROUP, "Periodic System.gc() pattern",
                Impact.SIGNIFICANT, message, details);
    }

    /**
     * Best-guess label for a near-constant period — purely heuristic
     * but maps the most common configurations operators ship with.
     * Returns generic phrasing for anything that doesn't match a known
     * setting so we don't lie about a specific source.
     */
    private static String suspectedPeriodicSource(double meanSeconds) {
        if (Math.abs(meanSeconds - 60.0)   <  5.0) return "RMI DGC tuned to 60s";
        if (Math.abs(meanSeconds - 120.0)  < 10.0) return "RMI DGC tuned to ~2 min (very common manual setting)";
        if (Math.abs(meanSeconds - 300.0)  < 15.0) return "RMI DGC tuned to 5 min";
        if (Math.abs(meanSeconds - 600.0)  < 30.0) return "RMI DGC tuned to 10 min";
        if (Math.abs(meanSeconds - 1800.0) < 60.0) return "RMI DGC tuned to 30 min";
        if (Math.abs(meanSeconds - 3600.0) < 60.0) return "RMI DGC default (1 hour)";
        return "scheduled application-level System.gc() (no RMI default matches)";
    }

    // ---- Other explicit-trigger GCs -----------------------------------------

    private static AnalyticsFinding otherExplicitGc(AnalyticsAggregation agg) {
        int n = agg.getOtherExplicitGcTimestamps().size();
        if (n == 0) return null;
        return new AnalyticsFinding(GROUP, "Diagnostic / agent-triggered GCs",
                Impact.CONCERNING,
                n + " GC" + (n == 1 ? "" : "s")
                        + " triggered by jcmd, a JVMTI agent, or a heap dump.",
                """
                These are typically operator-initiated: jcmd GC.run, jmap \
                -histo:live, JFR or a profiler attached. They behave like \
                System.gc() — a stop-the-world collection out of band with \
                the application. A handful is usually fine; many strongly \
                suggests a profiler or diagnostic tool is still attached in \
                production.""");
    }

    // ---- GCLocker -----------------------------------------------------------

    private static AnalyticsFinding gcLocker(AnalyticsAggregation agg) {
        long n = agg.getGcLockerCount();
        if (n == 0) {
            return new AnalyticsFinding(GROUP, "GCLocker-triggered GCs",
                    Impact.OK,
                    "No GCLocker-triggered collections observed.", "");
        }
        return new AnalyticsFinding(GROUP, "GCLocker-triggered GCs",
                Impact.CONCERNING,
                n + " GC" + (n == 1 ? "" : "s") + " deferred by the GCLocker "
                        + "and run when JNI critical sections released.",
                """
                The GCLocker postpones a GC while a thread is inside a JNI \
                GetPrimitiveArrayCritical / GetStringCritical block. When the \
                block exits the deferred GC runs immediately, often as a \
                young pause but sometimes upgraded depending on collector \
                state.

                A handful per log is usually fine. Frequent occurrences \
                point to long JNI critical sections that should be \
                shortened, or JNI code that allocates while inside one — \
                both are classic anti-patterns.""");
    }

    // ---- Full GC ------------------------------------------------------------

    private static AnalyticsFinding fullGc(AnalyticsAggregation agg) {
        long n = agg.getFullGcCount();
        if (n == 0) {
            return new AnalyticsFinding(GROUP, "Full GCs",
                    Impact.OK, "No full GCs observed.", "");
        }
        Impact impact = (n >= 3) ? Impact.SIGNIFICANT : Impact.CONCERNING;
        String message = String.format(Locale.ROOT,
                "%d full GC%s, total stop-the-world time %.2fs.",
                n, (n == 1 ? "" : "s"), agg.getTotalFullGcPauseSec());
        String details = """
                Full GCs are a sign of heap pressure or a collector that has \
                fallen behind. With G1 or ZGC a full GC means the concurrent \
                cycle did not finish in time (concurrent mode failure / \
                evacuation failure) — usually addressable by raising the \
                heap, tuning -XX:InitiatingHeapOccupancyPercent (G1), or \
                reducing allocation rate. With Parallel or Serial, full \
                GCs are part of normal operation but should be rare in a \
                healthy long-running JVM.""";
        return new AnalyticsFinding(GROUP, "Full GCs", impact, message, details);
    }
}
