package com.kodewerk.jma.analytics.cpu;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.List;
import java.util.Locale;

/**
 * CPU feature group analytics. The premise is that GC is overwhelmingly
 * user-space work — GC threads should accumulate very little kernel time,
 * and on a parallel collector the combined CPU should be roughly
 * (worker count) × wall. Three patterns push back on those assumptions
 * and almost always point at <em>something outside the JVM</em> stealing
 * cycles or stalling the GC threads:
 * <ul>
 *   <li><b>Kernel exceeds user</b> on a single pause — extremely
 *       unusual; the OS spent more time servicing kernel calls (paging,
 *       I/O wait, scheduling) than the GC thread spent doing GC work.</li>
 *   <li><b>Single-threaded behaviour</b> — combined CPU close to wall
 *       clock means workers were not running in parallel. Either the
 *       collector legitimately ran a single-threaded phase (CMS
 *       concurrent-mode failure escalation, full GC) or the workers
 *       were waiting on something they couldn't parallelise out of
 *       (often I/O or large-page allocation).</li>
 *   <li><b>Abnormal kernel share</b> — kernel time more than ~2% of
 *       combined CPU time. GC ought to spend essentially zero time in
 *       the kernel; this is the most direct fingerprint of host-level
 *       contention.</li>
 * </ul>
 * All three counts roll up into one finding so the user sees the full
 * picture — the description and remediation block are the same shape
 * Censum has shipped for years.
 */
public final class CPUAnalytics {

    private static final String GROUP = "CPU";

    /**
     * (user + kernel) / wall ratio at or below which we treat the pause
     * as "looked single threaded". A truly parallel pause on N workers
     * should produce a ratio close to N; values up to 1.5 leave headroom
     * for short pauses where worker spin-up cost dominates.
     */
    public static final double SINGLE_THREAD_RATIO_THRESHOLD = 1.5;

    /**
     * kernel / (user + kernel) above which the kernel share is "abnormal".
     * 2% is the conventional Censum threshold — high enough to ignore
     * normal scheduling jitter, low enough to flag systemic pressure.
     */
    public static final double ABNORMAL_KERNEL_RATIO = 0.02;

    private CPUAnalytics() {}

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        long total = agg.getPausesWithCpuSummary();
        if (total == 0) {
            // No CPU data parsed — log either has no pause-bearing events
            // we look at (ZGC-only, concurrent-only) or the parser didn't
            // pick up the [Times: ...] line. Skip the finding silently
            // rather than emit a misleading "0 of 0" OK row.
            return;
        }

        long kernelExceedsUser = agg.getKernelExceedsUserCount();
        long singleThreaded    = agg.getSingleThreadedCount();
        long abnormalKernel    = agg.getAbnormalKernelTimeCount();

        if (kernelExceedsUser == 0 && singleThreaded == 0 && abnormalKernel == 0) {
            out.add(new AnalyticsFinding(GROUP, "Kernel-time pressure",
                    Impact.OK,
                    "No kernel-time anomalies in GC events.",
                    ""));
            return;
        }

        // SIGNIFICANT when any single condition fires on more than ~25 %
        // of the analysed pauses; the issue is systemic at that point.
        // CONCERNING otherwise — present, but not pervasive.
        long worst = Math.max(kernelExceedsUser, Math.max(singleThreaded, abnormalKernel));
        Impact impact = (worst * 4 >= total) ? Impact.SIGNIFICANT : Impact.CONCERNING;

        String message = String.format(Locale.ROOT,
                "Kernel time exceeded user time %d time(s) in this log file. "
                + "GC appeared to run single threaded %d time(s). "
                + "GC threads collected an abnormal amount of kernel time %d time(s). "
                + "(out of %d analysed pauses).",
                kernelExceedsUser, singleThreaded, abnormalKernel, total);

        String details = """
                Description
                High amounts of kernel CPU utilization is an indication that the \
                root cause of long pauses lies outside of your JVM. Garbage \
                collection runs in user space and generally should have very \
                little dependence on the kernel. Consequently, GC threads should \
                generally NOT collect any kernel time. If your GC threads are \
                collecting significant amounts of kernel time it is quite often \
                caused by interference from (an)other application(s) running on \
                the same host.

                Solution
                Identify other applications running on the same host that are \
                competing for core computing resources. For example, pressure \
                on disk I/O can cause the GC threads to stall as they write \
                data to the /tmp/hsperfdata_xxx file — in this case the GC \
                thread accumulates kernel time as it waits for the write to \
                complete. Another common source is page management: with \
                Linux Transparent Huge Pages, when the OS cannot find enough \
                contiguous free pages to satisfy a 2 MB allocation it stops \
                all processing and shuffles memory around to make space, \
                stalling GC threads in the kernel for the duration.

                In rare cases you may see gaps between elapsed real time and \
                the time reported for the GC threads' CPU consumption. The GC \
                threads were clearly not running, but it is not clear why. \
                The usual culprits are virtualization (the VM was descheduled \
                by the hypervisor) or NTP-driven clock adjustments.

                In just about every case the best solution is to redistribute \
                the load over more machines. An alternative is to reduce the \
                strength of the dependency on the overloaded compute resource.

                In some cases you may see a reduced level of parallelism in \
                the garbage collection cycle — for example a concurrent-mode \
                failure escalating CMS to a single-threaded full GC, or \
                serial scans of large linear data structures that are not \
                currently parallelised. In those cases pause times can \
                sometimes be improved by setting -XX:+CMSScavengeBeforeRemark \
                to produce more balanced workloads.""";

        out.add(new AnalyticsFinding(GROUP, "Kernel-time pressure",
                impact, message, details));
    }
}
