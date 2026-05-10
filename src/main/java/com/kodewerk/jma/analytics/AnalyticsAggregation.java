package com.kodewerk.jma.analytics;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.CPUSummary;
import com.kodewerk.gcsee.event.GCCause;
import com.kodewerk.gcsee.event.GCEvent;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1ConcurrentMark;
import com.kodewerk.gcsee.event.g1gc.G1ConcurrentMarkResetForOverflow;
import com.kodewerk.gcsee.event.g1gc.G1GCEvent;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.g1gc.G1Mixed;
import com.kodewerk.gcsee.event.g1gc.G1Young;
import com.kodewerk.gcsee.event.g1gc.G1YoungInitialMark;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.time.DateTimeStamp;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.analytics.cpu.CPUAnalytics;
import com.kodewerk.jma.analytics.gccause.GCCauseAnalytics;
import com.kodewerk.jma.analytics.heap.HeapUsageAnalytics;
import com.kodewerk.jma.analytics.logduration.LogDurationAnalytics;
import com.kodewerk.jma.analytics.mixed.MixedCollectionAnalytics;
import com.kodewerk.jma.analytics.pause.PauseAnalytics;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Central analytics aggregation. Collects the raw data each analytic
 * group needs and runs the registered analytic classes at emit time
 * to produce the {@link AnalyticsData} the view returns.
 * <p>
 * One aggregation, many groups: the framework is intentionally a single
 * data store rather than one aggregation per feature area. Findings
 * naturally cluster on the same page, the {@code @Collates} pairing
 * stays one-to-one with {@link AnalyticsAggregator}, and adding a new
 * group is just "add the {@code record*} call you need plus a new
 * static evaluator in {@link #getData()}".
 * <p>
 * Storage shape per data type is deliberately minimal — for example
 * GC-cause analytics only needs timestamps of {@code System.gc()}
 * calls (for the periodicity check), so we don't keep the {@code GCEvent}
 * itself. Adding fields here when a future analytic needs them is the
 * intended evolution path.
 */
@Collates(AnalyticsAggregator.class)
public final class AnalyticsAggregation extends JmaAggregation {

    // ---- GC-cause group ------------------------------------------------------

    /** Timestamps (s) of every {@code System.gc()} call — used for periodicity. */
    private final List<Double> systemGcTimestamps = new ArrayList<>();

    /** Other explicit-trigger causes that aren't application System.gc() but
     *  read the same way to ops (jcmd, JVMTI agent, heap dump). Tracked
     *  separately so the System.gc() finding stays focused. */
    private final List<Double> otherExplicitGcTimestamps = new ArrayList<>();

    /** GCLocker-initiated collections — one entry per occurrence. */
    private long gcLockerCount = 0;

    /** Full GC stop-the-world events: count and total pause seconds. */
    private long fullGcCount = 0;
    private double totalFullGcPauseSec = 0.0;

    // ---- CPU group ----------------------------------------------------------

    /**
     * Number of pauses for which we had a parsed {@link CPUSummary}. Acts as
     * the denominator for the CPU-pressure ratios so a single pause out of
     * several thousand doesn't read as systemic.
     */
    private long pausesWithCpuSummary = 0;

    /** Pauses where {@code kernel > user} — GC is mostly user-space work, so this is unusual. */
    private long kernelExceedsUserCount = 0;

    /**
     * Pauses that look single-threaded — total CPU (user + kernel) is at
     * most {@code 1.5 × wall}. A parallel collector running on N workers
     * with healthy concurrency should produce {@code (user + kernel) ≈ N × wall};
     * a ratio near 1 means only one thread was effectively doing work.
     * Pauses below a small wall-clock floor are excluded so noise on
     * sub-50 ms events does not show up here.
     */
    private long singleThreadedCount = 0;

    /**
     * Pauses where kernel time was an abnormally large share of the
     * combined CPU time. Threshold lives in {@link CPUAnalytics} so the
     * "what counts as abnormal" knob and the explanation text stay
     * together.
     */
    private long abnormalKernelTimeCount = 0;

    // ---- Heap usage group ---------------------------------------------------

    /** Total tenuring records observed — denominator for premature-promotion %. */
    private long survivorRecords = 0;

    /** Records where calculated tenuring threshold dropped below max — survivor pressure. */
    private long prematurePromotions = 0;

    /** Metaspace-threshold-triggered GCs anywhere in the log. */
    private long metaspaceTriggeredCount = 0;

    /** Subset of the above that occurred after the warmup window. */
    private long metaspaceTriggeredAfterWarmupCount = 0;

    /** Heap-too-small fingerprints — counted from their dedicated event types. */
    private long concurrentMarkAbortedCount   = 0;
    private long concurrentMarkResetForOverflowCount = 0;
    private long toSpaceExhaustedCount = 0;

    /**
     * Post-collection heap occupancy samples for the heap-stability
     * analytic. We keep them in arrival order so growth-trend math can
     * quartile the run; only one {@code (tSec, mb)} pair per pause that
     * carried a valid summary, so memory cost stays linear in pause count.
     */
    private final java.util.List<double[]> heapOccupancySamples = new java.util.ArrayList<>();

    /** First event timestamp seen — anchors the warmup-window calculation. */
    private double firstEventSec = Double.NaN;

    /**
     * Most recent event timestamp seen. Used as a fallback for the
     * log-duration analytic when GCSee's Diary doesn't provide a
     * reliable {@code estimatedRuntime} (truncated logs, missing
     * JVMTermination event).
     */
    private double lastEventSec = Double.NaN;

    // ---- Pause group --------------------------------------------------------

    /**
     * Stop-the-world pause events recorded as {@code (tSec, durationSec)}
     * tuples for the pause-time analytics (max / percentiles, throughput
     * windows, distribution shape). G1 + Generational pauses contribute
     * one tuple per event; each ZGCCollection contributes up to three
     * (mark-start, mark-end, relocate-start sub-pauses).
     */
    private final java.util.List<double[]> pauseEvents = new java.util.ArrayList<>();

    // ---- Mixed-collections group --------------------------------------------

    /**
     * Has at least one G1 concurrent-mark cycle started? Until we see
     * the first {@link G1YoungInitialMark} we don't know where a run
     * began, so we ignore any G1Mixed events that arrive before it.
     */
    private boolean mixedRunStarted = false;

    /** Mixed-collection count for the current (open) run. */
    private int currentMixedRunCount = 0;

    /**
     * Histogram of completed mixed-collection runs. Key is the count of
     * consecutive G1 mixed collections following one concurrent-mark
     * cycle; value is the number of cycles that produced that count.
     * TreeMap so the row order in the rendered table reads ascending.
     */
    private final java.util.Map<Integer, Long> mixedRunHistogram = new java.util.TreeMap<>();

    // ---- Cross-group context -------------------------------------------------

    /** Total observed GC events — used as the "log had GC at all" floor. */
    private long totalGcEvents = 0;

    /**
     * Has at least one G1 event been observed? Drives the
     * "analytics always show for the relevant collector" rule —
     * collector-specific analytics (currently Mixed Collections)
     * use this flag to decide whether to emit at all on a given
     * log, separate from whether they happened to find anything
     * to histogram.
     */
    private boolean g1Observed = false;

    /** Required by the GCSee module SPI. */
    public AnalyticsAggregation() {}

    // ---- Aggregator-side mutation API ---------------------------------------

    void recordCollection(double tSec, GCEvent event) {
        totalGcEvents++;
        if (Double.isNaN(firstEventSec)) firstEventSec = tSec;
        lastEventSec = tSec;
        if (event instanceof G1GCEvent) g1Observed = true;

        GCCause cause = event.getGCCause();
        if (cause == GCCause.JAVA_LANG_SYSTEM) {
            systemGcTimestamps.add(tSec);
        } else if (cause == GCCause.DIAGNOSTIC_COMMAND
                || cause == GCCause.JVMTI_FORCE_GC
                || cause == GCCause.HEAP_DUMP
                || cause == GCCause.HEAP_INSPECTION) {
            otherExplicitGcTimestamps.add(tSec);
        } else if (cause == GCCause.GC_LOCKER) {
            gcLockerCount++;
        } else if (cause == GCCause.METADATA_GENERATION_THRESHOLD) {
            metaspaceTriggeredCount++;
            // "After warmup" — first WARMUP_WINDOW_SEC seconds of observed
            // events are class-loading dominated and a metaspace-triggered
            // GC there is normal startup behaviour. Past that point the
            // same cause is a class-loading leak fingerprint.
            if (tSec - firstEventSec >= HeapUsageAnalytics.WARMUP_WINDOW_SEC) {
                metaspaceTriggeredAfterWarmupCount++;
            }
        }

        if (GCCategoryMapper.categoryOf(event) == GCCategory.FULL) {
            fullGcCount++;
            totalFullGcPauseSec += event.getDuration();
        }

        // Heap-too-small fingerprints — counted here (rather than from
        // dedicated subscriptions) because GCSee's dispatcher fires only
        // the most-specific handler. With a single GCEvent subscription
        // every check sees every event.
        if (event instanceof G1ConcurrentMark cm && cm.isAborted()) {
            concurrentMarkAbortedCount++;
        }
        if (event instanceof G1ConcurrentMarkResetForOverflow) {
            concurrentMarkResetForOverflowCount++;
        }
        if (event instanceof G1Young g1y && g1y.isToSpaceExhausted()) {
            toSpaceExhaustedCount++;
        }

        // Mixed-collection run detection (G1). Each G1YoungInitialMark
        // starts a new concurrent-mark cycle; the count of G1Mixed
        // events between two consecutive initial-marks is the run
        // length to histogram. We record only completed runs (between
        // two initial-marks) so an in-flight run at end-of-log doesn't
        // skew the distribution. G1YoungInitialMark must be checked
        // before G1Mixed because both extend G1Young.
        if (event instanceof G1YoungInitialMark) {
            if (mixedRunStarted) {
                mixedRunHistogram.merge(currentMixedRunCount, 1L, Long::sum);
            }
            mixedRunStarted = true;
            currentMixedRunCount = 0;
        } else if (mixedRunStarted && event instanceof G1Mixed) {
            currentMixedRunCount++;
        }

        recordCpuPressure(event);
        recordHeapOccupancy(tSec, event);
        recordPauseEvent(tSec, event);
    }

    /**
     * Capture stop-the-world pause durations for the pause-time analytics.
     * Mirrors {@link com.kodewerk.jma.aggregation.pause.PauseTimeAggregator}'s
     * pause-selection logic so the two views stay consistent: G1 +
     * Generational pause-bearing events contribute one tuple each, ZGC
     * splits each cycle into its three STW sub-pauses (mark-start,
     * mark-end, relocate-start). Concurrent-only events do not reach
     * this branch.
     */
    private void recordPauseEvent(double tSec, GCEvent event) {
        if (event instanceof G1GCPauseEvent || event instanceof GenerationalGCPauseEvent) {
            double dur = event.getDuration();
            if (dur > 0.0) pauseEvents.add(new double[] { tSec, dur });
            return;
        }
        if (event instanceof ZGCCollection zgc) {
            addZgcSubPause(zgc.getPauseMarkStartTimeStamp(),     zgc.getPauseMarkStartDuration());
            addZgcSubPause(zgc.getPauseMarkEndTimeStamp(),       zgc.getPauseMarkEndDuration());
            addZgcSubPause(zgc.getPauseRelocateStartTimeStamp(), zgc.getPauseRelocateStartDuration());
        }
    }

    private void addZgcSubPause(DateTimeStamp ts, double durationSec) {
        if (ts == null || durationSec <= 0.0) return;
        pauseEvents.add(new double[] { ts.toSeconds(), durationSec });
    }

    /** Capture post-collection heap occupancy in MB for the stability analytic. */
    private void recordHeapOccupancy(double tSec, GCEvent event) {
        MemoryPoolSummary heap = null;
        if (event instanceof G1GCPauseEvent g1)               heap = g1.getHeap();
        else if (event instanceof GenerationalGCPauseEvent g) heap = g.getHeap();
        if (heap == null || !heap.isValid()) return;
        // MemoryPoolSummary exposes occupancy in KB; the stability check
        // works on MB so the trend numbers are easier to read in the
        // remediation message.
        double mb = heap.getOccupancyAfterCollection() / 1024.0;
        heapOccupancySamples.add(new double[] { tSec, mb });
    }

    /**
     * Pull the per-pause {@link CPUSummary} off pause-bearing events and
     * tally the three kernel-pressure conditions. Concurrent-only events
     * and ZGC cycles do not carry a single CPUSummary the same way (their
     * CPU accounting is split across phases), so we focus on the two
     * collector families where the {@code [Times: user= sys= real= ]}
     * line is reliably present.
     */
    private void recordCpuPressure(GCEvent event) {
        CPUSummary cpu = null;
        if (event instanceof G1GCPauseEvent g1)              cpu = g1.getCpuSummary();
        else if (event instanceof GenerationalGCPauseEvent g) cpu = g.getCpuSummary();
        if (cpu == null) return;

        double user   = cpu.getUser();
        double kernel = cpu.getKernel();
        double wall   = cpu.getWallClock();
        double total  = user + kernel;

        // Skip pauses that have no CPU data at all (parser may have failed
        // to find the line on a malformed entry) — counting them would
        // distort the rates.
        if (user <= 0.0 && kernel <= 0.0 && wall <= 0.0) return;

        pausesWithCpuSummary++;

        if (kernel > user) {
            kernelExceedsUserCount++;
        }
        // Floor at 50 ms wall-clock so sub-noise pauses don't create
        // false single-threaded readings — many GC entries report
        // user/kernel/wall all at the millisecond grid floor and the
        // ratio becomes meaningless.
        if (wall >= 0.05 && total <= wall * CPUAnalytics.SINGLE_THREAD_RATIO_THRESHOLD) {
            singleThreadedCount++;
        }
        if (total > 0.0 && (kernel / total) >= CPUAnalytics.ABNORMAL_KERNEL_RATIO) {
            abnormalKernelTimeCount++;
        }
    }

    /**
     * Tally one survivor record. We mirror Censum's premature-promotion
     * definition: the calculated tenuring threshold dropped below the
     * configured max, meaning survivor pressure pushed objects to the
     * old generation earlier than the user's setting asked for.
     */
    void recordSurvivorRecord(SurvivorRecord r) {
        survivorRecords++;
        if (r.getCalculatedTenuringThreshold() < r.getMaxTenuringThreshold()) {
            prematurePromotions++;
        }
    }

    // ---- Read API for analytics ---------------------------------------------

    public List<Double> getSystemGcTimestamps()        { return systemGcTimestamps; }
    public List<Double> getOtherExplicitGcTimestamps() { return otherExplicitGcTimestamps; }
    public long   getGcLockerCount()                   { return gcLockerCount; }
    public long   getFullGcCount()                     { return fullGcCount; }
    public double getTotalFullGcPauseSec()             { return totalFullGcPauseSec; }
    public long   getTotalGcEvents()                   { return totalGcEvents; }
    public long   getPausesWithCpuSummary()            { return pausesWithCpuSummary; }
    public long   getKernelExceedsUserCount()          { return kernelExceedsUserCount; }
    public long   getSingleThreadedCount()             { return singleThreadedCount; }
    public long   getAbnormalKernelTimeCount()         { return abnormalKernelTimeCount; }
    public long   getSurvivorRecords()                 { return survivorRecords; }
    public long   getPrematurePromotions()             { return prematurePromotions; }
    public long   getMetaspaceTriggeredCount()         { return metaspaceTriggeredCount; }
    public long   getMetaspaceTriggeredAfterWarmupCount() { return metaspaceTriggeredAfterWarmupCount; }
    public long   getConcurrentMarkAbortedCount()      { return concurrentMarkAbortedCount; }
    public long   getConcurrentMarkResetForOverflowCount() { return concurrentMarkResetForOverflowCount; }
    public long   getToSpaceExhaustedCount()           { return toSpaceExhaustedCount; }
    public java.util.List<double[]> getHeapOccupancySamples() { return heapOccupancySamples; }
    public java.util.List<double[]> getPauseEvents()          { return pauseEvents; }
    public java.util.Map<Integer, Long> getMixedRunHistogram() { return mixedRunHistogram; }
    public boolean isG1Observed()                              { return g1Observed; }

    /**
     * Best available log-duration estimate in seconds, or {@code 0.0}
     * when nothing reliable is known.
     * <p>
     * Prefers GCSee's Diary {@code estimatedRuntime} (which accounts for
     * truncation via the ZERO_GCID heuristic) and falls back to the
     * span between the first and last GC event when the Diary estimate
     * is non-positive (e.g. logs with no JVMTermination record).
     */
    public double getLogDurationSec() {
        double diary = getEstimatedRuntimeSeconds();
        if (diary > 0.0) return diary;
        if (!Double.isNaN(firstEventSec) && !Double.isNaN(lastEventSec)
                && lastEventSec > firstEventSec) {
            return lastEventSec - firstEventSec;
        }
        return 0.0;
    }

    /** Run every registered analytic group and bundle the findings. */
    public AnalyticsData getData() {
        List<AnalyticsFinding> findings = new ArrayList<>();
        HeapUsageAnalytics.evaluate(this, findings);
        PauseAnalytics.evaluate(this, findings);
        GCCauseAnalytics.evaluate(this, findings);
        MixedCollectionAnalytics.evaluate(this, findings);
        CPUAnalytics.evaluate(this, findings);
        // Log-duration sits last as the frame-of-reference footer:
        // every other finding's confidence is bounded by it.
        LogDurationAnalytics.evaluate(this, findings);
        return new AnalyticsData(findings);
    }

    @Override
    public boolean isEmpty() {
        // Empty only when no GC events at all reached us — a non-GC log.
        // A log with GC events but no findings to surface still renders
        // (with OK rows), because "we looked and nothing's wrong" is
        // itself a useful answer.
        return totalGcEvents == 0;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
