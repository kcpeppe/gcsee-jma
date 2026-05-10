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
 * Companion to {@link HeapOccupancyAggregator} that emits the
 * after-collection occupancy as a percentage of the heap size at that
 * moment rather than absolute MB. Same event sources and category
 * mapping; only the aggregation differs.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class HeapOccupancyAfterPercentAggregator
        extends Aggregator<HeapOccupancyAfterPercentAggregation> {

    public HeapOccupancyAfterPercentAggregator(HeapOccupancyAfterPercentAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        recordFromMemoryPool(event, event.getHeap());
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        recordFromMemoryPool(event, event.getHeap());
    }

    private void recordFromMemoryPool(JVMEvent event, MemoryPoolSummary heap) {
        double t = event.getDateTimeStamp().toSeconds();
        if (heap == null || !heap.isValid()) {
            aggregation().noteInvalidSample(t);
            return;
        }
        long size      = heap.getSizeAfterCollection();
        long occupancy = heap.getOccupancyAfterCollection();
        if (size <= 0) {
            aggregation().noteInvalidSample(t);
            return;
        }
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        double pct = 100.0 * occupancy / size;
        aggregation().recordOccupancyPercent(category, t, pct);
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
        long size = capacity.getMaxCapacity();
        if (size <= 0) {
            aggregation().noteInvalidSample(t);
            return;
        }
        double pct = 100.0 * memory.getOccupancyAfter() / size;
        aggregation().recordOccupancyPercent(category, t, pct);
    }
}
