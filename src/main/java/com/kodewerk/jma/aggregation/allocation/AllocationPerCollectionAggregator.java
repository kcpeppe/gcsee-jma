package com.kodewerk.jma.aggregation.allocation;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCMemorySummary;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Companion to {@link AllocationRateAggregator} that emits the raw
 * MB allocated between consecutive collections (not divided by
 * elapsed time) and tags each point with the {@link GCCategory} of
 * the collection that consumed the allocation, so the chart shows
 * which kind of pause is sweeping each allocation burst.
 * <p>
 * The before/after-occupancy delta logic is identical to
 * {@code AllocationRateAggregator} — same prev-state tracking, same
 * unit normalisation (KB → MB for G1 / Generational, bytes → MB for
 * ZGC), same drop of negative deltas. The two aggregators run side
 * by side; if the maintenance ever forks, refactor the shared
 * sample-then-record logic into a static helper.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class AllocationPerCollectionAggregator
        extends Aggregator<AllocationPerCollectionAggregation> {

    private static final double KB_TO_MB    = 1.0 / 1024.0;
    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    /** Occupancy-after (MB) of the last well-formed sample, or NaN if none yet. */
    private double prevOccupancyAfterMb = Double.NaN;

    public AllocationPerCollectionAggregator(AllocationPerCollectionAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        sample(event,
               heap.getOccupancyBeforeCollection() * KB_TO_MB,
               heap.getOccupancyAfterCollection()  * KB_TO_MB);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        sample(event,
               heap.getOccupancyBeforeCollection() * KB_TO_MB,
               heap.getOccupancyAfterCollection()  * KB_TO_MB);
    }

    private void onZgcCycle(ZGCCollection event) {
        ZGCMemorySummary memory = event.getMemorySummary();
        if (memory == null) return;
        sample(event,
               memory.getOccupancyBefore() * BYTES_TO_MB,
               memory.getOccupancyAfter()  * BYTES_TO_MB);
    }

    /**
     * Fold one normalised sample into the running series. Records an
     * allocation point when we have a previous sample to diff against;
     * updates state either way so the next event has a baseline to
     * subtract from.
     */
    private void sample(JVMEvent event, double occupancyBeforeMb, double occupancyAfterMb) {
        if (!Double.isNaN(prevOccupancyAfterMb)) {
            double allocated = occupancyBeforeMb - prevOccupancyAfterMb;
            GCCategory category = GCCategoryMapper.categoryOf(event);
            if (allocated >= 0.0 && category != null) {
                aggregation().recordAllocation(category,
                        event.getDateTimeStamp().toSeconds(), allocated);
            } else if (allocated < 0.0) {
                aggregation().noteNegativeDrop();
            }
        }
        prevOccupancyAfterMb = occupancyAfterMb;
    }
}
