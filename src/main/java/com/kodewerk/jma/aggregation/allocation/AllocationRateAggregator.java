package com.kodewerk.jma.aggregation.allocation;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCMemorySummary;

/**
 * Computes application allocation rate in MB/s between consecutive GC
 * events. Allocation is inferred from the change in heap occupancy:
 * <pre>
 *     allocated = occupancyBefore(current) - occupancyAfter(previous)
 *     rate      = allocated / (time(current) - time(previous))
 * </pre>
 * Rates are plotted at the current event's timestamp (i.e. "this is the
 * rate measured up to this collection"). Negative deltas — which happen
 * when a concurrent cycle or full GC freed memory between samples — are
 * dropped rather than plotted (per the project brief).
 * <p>
 * Unit normalisation matches the heap-occupancy view: Generational/G1
 * samples are in KB, ZGC samples are in bytes; both end up in MB before
 * the rate divide.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class AllocationRateAggregator extends Aggregator<AllocationRateAggregation> {

    private static final double KB_TO_MB    = 1.0 / 1024.0;
    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    /** Timestamp (seconds) of the last well-formed sample we recorded, or NaN if none yet. */
    private double prevTimeSec = Double.NaN;
    /** Occupancy-after (MB) of the last well-formed sample. */
    private double prevOccupancyAfterMb;

    public AllocationRateAggregator(AllocationRateAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        sample(event.getDateTimeStamp().toSeconds(),
               heap.getOccupancyBeforeCollection() * KB_TO_MB,
               heap.getOccupancyAfterCollection()  * KB_TO_MB);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        sample(event.getDateTimeStamp().toSeconds(),
               heap.getOccupancyBeforeCollection() * KB_TO_MB,
               heap.getOccupancyAfterCollection()  * KB_TO_MB);
    }

    private void onZgcCycle(ZGCCollection event) {
        ZGCMemorySummary memory = event.getMemorySummary();
        if (memory == null) return;
        sample(event.getDateTimeStamp().toSeconds(),
               memory.getOccupancyBefore() * BYTES_TO_MB,
               memory.getOccupancyAfter()  * BYTES_TO_MB);
    }

    /**
     * Fold one normalised sample into the running series. Records a rate
     * point when we have a previous sample to diff against; updates state
     * either way so the next event can compute a rate against this one.
     */
    private void sample(double tSec, double occupancyBeforeMb, double occupancyAfterMb) {
        if (!Double.isNaN(prevTimeSec)) {
            double dt        = tSec - prevTimeSec;
            double allocated = occupancyBeforeMb - prevOccupancyAfterMb;
            if (dt > 0.0 && allocated >= 0.0) {
                aggregation().recordRate(tSec, allocated / dt);
            } else if (allocated < 0.0) {
                aggregation().noteNegativeDrop();
            }
        }
        prevTimeSec          = tSec;
        prevOccupancyAfterMb = occupancyAfterMb;
    }
}
