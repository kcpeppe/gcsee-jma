package com.kodewerk.jma.aggregation.summary;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.GCCause;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1FullGC;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.g1gc.G1YoungInitialMark;
import com.kodewerk.gcsee.event.generational.CMSRemark;
import com.kodewerk.gcsee.event.generational.ConcurrentModeFailure;
import com.kodewerk.gcsee.event.generational.ConcurrentModeInterrupted;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.generational.InitialMark;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.time.DateTimeStamp;

/**
 * Builds a {@link SummaryAggregation} by walking every stop-the-world
 * pause event emitted for the Generational, G1, and ZGC collector
 * families. ZGC cycles arrive as a single {@link ZGCCollection} event
 * carrying three sub-pauses (mark-start / mark-end / relocate-start);
 * we fold those into one row per cycle type so the summary table
 * matches the user's mental model (one row per cycle, not three).
 * <p>
 * Per-event responsibilities:
 * <ul>
 *   <li>Pick the right row ({@code event.getClass().getSimpleName()} is
 *       the row key; that way G1Mixed vs G1Young vs G1YoungInitialMark
 *       each land in their own row without additional type branching).</li>
 *   <li>Fold pause duration into the row's total / max / invocation count
 *       and update its per-row interval running mean.</li>
 *   <li>Derive allocation / recovery deltas from the heap occupancy
 *       before/after (KB → MB, same unit choice as our existing views)
 *       using a single global {@code prevOccupancyAfterMb} — matches the
 *       convention in {@code AllocationRateAggregator}.</li>
 *   <li>Detect the back-to-back case (see the dedicated helper), bump the
 *       resize counter when heap capacity changes between events, and
 *       mark the log-level flags {@code anyG1Seen} / {@code anyCmsSeen}
 *       that the aggregation uses to decide which columns to emit.</li>
 * </ul>
 * Concurrent events are not currently subscribed to — GCSee 0.1.2 doesn't
 * expose distinct concurrent-phase events for CMS/G1 (concurrent STW
 * phases come through as {@code InitialMark} / {@code CMSRemark} /
 * {@code G1Remark} / {@code G1Cleanup}, all of which are pause events).
 * The concurrent-phases table infrastructure is in place so that a later
 * GCSee revision can start populating it without a second aggregator.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class SummaryAggregator extends Aggregator<SummaryAggregation> {

    private static final double KB_TO_MB = 1.0 / 1024.0;

    // TODO: expose for user customization once threshold tuning lands
    private static double BACK_TO_BACK_THRESHOLD_SEC = 0.01;

    /** Timestamp (seconds) at which the previous STW event ended, or NaN if none yet. */
    private double prevEventEndSec = Double.NaN;
    /** Previous STW event's occupancy-after in MB — used for the allocation delta. */
    private double prevOccupancyAfterMb = Double.NaN;
    /** Last-known heap capacity in MB — a change between events is a heap-resize signal. */
    private double prevHeapCapacityMb = Double.NaN;

    public SummaryAggregator(SummaryAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        aggregation().markG1Seen();
        if (event instanceof G1YoungInitialMark) {
            aggregation().noteG1InitialMark();
        }
        // G1FullGC covers G1SystemGC variants per GCCategoryMapper's comment.
        if (event instanceof G1FullGC) {
            // Leave it as its own row; don't count as System.gc() unless the
            // log format explicitly says so. Counted via the FullGC row.
        }
        // TODO: wire aggregation().noteToSpaceExhausted() once GCSee surfaces
        // the to-space-exhausted flag on G1 pauses. The note is already
        // plumbed through to the summary; only the detection is missing.
        recordStw(event);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        // SystemGC lands in the STW pauses table as its own row; no
        // separate headline counter — see note in SummaryAggregation.
        if (event instanceof InitialMark
                || event instanceof CMSRemark
                || event instanceof ConcurrentModeFailure
                || event instanceof ConcurrentModeInterrupted) {
            aggregation().markCmsSeen();
        }
        // Collector-family detection: Parallel and Serial don't have
        // marker events in the way CMS does, so we use the runtime
        // class simple name to keep the test exact (avoids accidental
        // matching of subclasses, e.g. ParNew → DefNew if the
        // hierarchy ever changes). PSYoungGen and PSFullGC fingerprint
        // Parallel; DefNew without any CMS markers fingerprints Serial.
        String name = event.getClass().getSimpleName();
        if (name.equals("PSYoungGen") || name.equals("PSFullGC")) {
            aggregation().markParallelSeen();
        }
        if (name.equals("DefNew") || name.equals("FullGC")) {
            // CMS markers (if any) get set later in the same call chain
            // or in a future event — collector resolution defers to
            // detectCollector(), which prefers CMS over Serial when
            // both flags end up set.
            aggregation().markSerialSeen();
        }
        recordStw(event);
    }

    /** Common STW-event handling for both collector families. */
    private void recordStw(com.kodewerk.gcsee.event.jvm.JVMEvent event) {
        String rowKey = event.getClass().getSimpleName();

        double tSec     = event.getDateTimeStamp().toSeconds();
        double pauseSec = event.getDuration();
        if (pauseSec < 0.0) pauseSec = 0.0;

        SummaryAggregation.StwRow row = aggregation().stwRow(rowKey, rowKey);
        row.invocations++;
        row.totalPauseSec += pauseSec;
        if (pauseSec > row.maxPauseSec) row.maxPauseSec = pauseSec;
        if (!Double.isNaN(row.lastStartSec)) {
            double interval = tSec - row.lastStartSec;
            if (interval > 0.0) {
                row.sumIntervalsSec += interval;
                row.intervalCount++;
            }
        }
        row.lastStartSec = tSec;

        // Allocation / recovery deltas, if the log carries heap occupancy.
        MemoryPoolSummary heap = heapOf(event);
        if (heap != null && heap.isValid()) {
            double occBeforeMb = heap.getOccupancyBeforeCollection() * KB_TO_MB;
            double occAfterMb  = heap.getOccupancyAfterCollection()  * KB_TO_MB;
            double sizeAfterMb = heap.getSizeAfterCollection()       * KB_TO_MB;

            // Bytes recovered by *this* collection — always this event's row.
            double recovered = occBeforeMb - occAfterMb;
            if (recovered > 0.0) row.totalBytesRecoveredMb += recovered;

            // Bytes allocated by the application *since* the previous event —
            // attribute to the current row (that's when we observed the delta).
            if (!Double.isNaN(prevOccupancyAfterMb)) {
                double allocated = occBeforeMb - prevOccupancyAfterMb;
                if (allocated > 0.0) row.totalBytesAllocatedMb += allocated;
            }
            prevOccupancyAfterMb = occAfterMb;

            // Heap resize: capacity changed between events.
            if (!Double.isNaN(prevHeapCapacityMb)
                    && Math.abs(sizeAfterMb - prevHeapCapacityMb) > 0.5) {
                aggregation().noteHeapResize();
            }
            prevHeapCapacityMb = sizeAfterMb;
        }

        // Back-to-back: time the application actually ran since the previous
        // pause ended. See project note — Censum compares start-to-start; we
        // intentionally use start-to-end because that's the application-run gap.
        if (!Double.isNaN(prevEventEndSec)) {
            double applicationTime = tSec - prevEventEndSec;
            if (applicationTime < BACK_TO_BACK_THRESHOLD_SEC) {
                aggregation().noteBackToBack();
            }
        }
        prevEventEndSec = tSec + pauseSec;
    }

    /**
     * The two pause-event hierarchies each expose {@code getHeap()} but on
     * different base classes, so we pattern-match here rather than try to
     * abstract it via a shared interface.
     */
    private static MemoryPoolSummary heapOf(com.kodewerk.gcsee.event.jvm.JVMEvent event) {
        if (event instanceof G1GCPauseEvent g1)           return g1.getHeap();
        if (event instanceof GenerationalGCPauseEvent gen) return gen.getHeap();
        return null;
    }

    /**
     * Fold a ZGC cycle into a single STW row.
     * <p>
     * One {@link ZGCCollection} carries up to three sub-pauses
     * (mark-start, mark-end, relocate-start). Treating each sub-pause as
     * its own row would triple the row count and mismatch the "rows are
     * cycle types" mental model the rest of the page uses, so we sum the
     * sub-pause durations into {@code totalPauseSec}, take their max as
     * the row's {@code maxPauseSec}, and bump {@code invocations} by one
     * per cycle. The interval running mean tracks the cycle's start
     * timestamp (not the sub-pause timestamps) so "Interval" reads as
     * "time between cycles of this type" — same as it does for every
     * other row.
     * <p>
     * Allocation deltas: ZGC's memory bookkeeping diverges from the
     * KB-based {@link MemoryPoolSummary} the generational families use,
     * so we leave the {@code allocMb} / {@code recoveredMb} columns
     * empty on ZGC rows for now. Headline-level allocation / throughput
     * numbers come out of the dedicated allocation / heap aggregations
     * (which already speak ZGC), so the summary doesn't need to
     * synthesise them here.
     */
    private void onZgcCycle(ZGCCollection event) {
        aggregation().markZgcSeen();
        if (event.getGCCause() == GCCause.ALLOC_STALL) {
            aggregation().noteAllocationStall();
        }

        String rowKey = event.getClass().getSimpleName();
        SummaryAggregation.StwRow row = aggregation().stwRow(rowKey, rowKey);

        double markStart    = nonNegative(event.getPauseMarkStartDuration());
        double markEnd      = nonNegative(event.getPauseMarkEndDuration());
        double relocateStrt = nonNegative(event.getPauseRelocateStartDuration());
        double totalPauseSec = markStart + markEnd + relocateStrt;
        double maxPauseSec   = Math.max(markStart, Math.max(markEnd, relocateStrt));

        row.invocations++;
        row.totalPauseSec += totalPauseSec;
        if (maxPauseSec > row.maxPauseSec) row.maxPauseSec = maxPauseSec;

        // Cycle start: ZGCCollection's own DateTimeStamp is the pause-mark-start
        // moment (the first sub-pause begins the cycle); fall back to the
        // sub-pause stamp if the event-level stamp is missing.
        DateTimeStamp cycleStart = event.getDateTimeStamp();
        if (cycleStart == null) cycleStart = event.getPauseMarkStartTimeStamp();
        if (cycleStart != null) {
            double tSec = cycleStart.toSeconds();
            if (!Double.isNaN(row.lastStartSec)) {
                double interval = tSec - row.lastStartSec;
                if (interval > 0.0) {
                    row.sumIntervalsSec += interval;
                    row.intervalCount++;
                }
            }
            row.lastStartSec = tSec;
            // Back-to-back detection (gap between this cycle's start and
            // the previous STW event's end). Mirrors the generational /
            // G1 path so the headline-level note is consistent.
            if (!Double.isNaN(prevEventEndSec)) {
                double applicationTime = tSec - prevEventEndSec;
                if (applicationTime < BACK_TO_BACK_THRESHOLD_SEC) {
                    aggregation().noteBackToBack();
                }
            }
            prevEventEndSec = tSec + totalPauseSec;
        }
    }

    private static double nonNegative(double v) {
        return v > 0.0 ? v : 0.0;
    }
}
