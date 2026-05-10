package com.kodewerk.jma.aggregation.summary;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1FullGC;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.g1gc.G1YoungInitialMark;
import com.kodewerk.gcsee.event.generational.CMSRemark;
import com.kodewerk.gcsee.event.generational.ConcurrentModeFailure;
import com.kodewerk.gcsee.event.generational.ConcurrentModeInterrupted;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.generational.InitialMark;

/**
 * Builds a {@link SummaryAggregation} by walking every stop-the-world
 * pause event emitted for the Generational and G1 collector families.
 * ZGC is not registered here on purpose — see the note in
 * {@link SummaryAggregation}'s class javadoc.
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
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL})
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
}
