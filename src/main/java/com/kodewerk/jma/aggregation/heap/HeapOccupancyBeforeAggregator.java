package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCHeapCapacitySummary;
import com.kodewerk.gcsee.event.zgc.ZGCMemorySummary;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Companion to {@link HeapOccupancyAggregator} that captures the
 * <em>before</em>-collection occupancy and the heap-size measurement
 * available at that moment. Same event sources, same unit-normalisation
 * (KB → MB for Generational / G1, bytes → MB for ZGC), same per-category
 * colouring — only the side of the collection differs.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class HeapOccupancyBeforeAggregator extends Aggregator<HeapOccupancyBeforeAggregation> {

    private static final double KB_TO_MB    = 1.0 / 1024.0;
    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    public HeapOccupancyBeforeAggregator(HeapOccupancyBeforeAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) {
            aggregation().noteInvalidSample(t);
            return;
        }
        recordFromMemoryPool(event, heap);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) {
            aggregation().noteInvalidSample(t);
            return;
        }
        recordFromMemoryPool(event, heap);
    }

    /** Shared path for anything that exposes heap via {@link MemoryPoolSummary} (KB). */
    private void recordFromMemoryPool(JVMEvent event, MemoryPoolSummary heap) {
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        double t = event.getDateTimeStamp().toSeconds();
        // "Size before" is the capacity entering the pause — use that
        // as the heap-size line so the line and the occupancy points
        // share a consistent moment.
        double sizeMb      = heap.getSizeBeforeCollection()      * KB_TO_MB;
        double occupancyMb = heap.getOccupancyBeforeCollection() * KB_TO_MB;

        aggregation().recordHeapCapacity(t, sizeMb);
        aggregation().recordHeapOccupancy(category, t, occupancyMb);
    }

    private void onZgcCycle(ZGCCollection event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);

        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        ZGCMemorySummary memory = event.getMemorySummary();
        ZGCHeapCapacitySummary capacity = event.getHeapCapacitySummary();
        if (memory == null || capacity == null) {
            aggregation().noteInvalidSample(t);
            return;
        }

        // ZGC reports a single capacity per cycle; reuse it as the size
        // for both the before and after views — the after-side aggregator
        // makes the same choice for symmetry.
        double sizeMb      = capacity.getMaxCapacity()  * BYTES_TO_MB;
        double occupancyMb = memory.getOccupancyBefore() * BYTES_TO_MB;

        aggregation().recordHeapCapacity(t, sizeMb);
        aggregation().recordHeapOccupancy(category, t, occupancyMb);
    }
}
