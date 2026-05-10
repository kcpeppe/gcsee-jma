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
 * Captures heap-size and post-collection occupancy for every pause across
 * G1, Generational and ZGC collector families, feeding them into a single
 * {@link HeapOccupancyAggregation}.
 * <p>
 * Unit normalisation happens here so the aggregation and UI layer never
 * need to know about the underlying collector:
 * <ul>
 *   <li>Generational / G1 {@code MemoryPoolSummary} values are in <b>KB</b>.</li>
 *   <li>ZGC {@code ZGCMemorySummary} / {@code ZGCHeapCapacitySummary} are in <b>bytes</b>.</li>
 * </ul>
 * Both are converted to <b>MB</b> before recording.
 * <p>
 * Concurrent-only events (ZGC phase events without a cycle summary, G1
 * concurrent marking cycles, etc.) do not arrive here — we only listen to
 * the pause / cycle types that carry an authoritative heap sample.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class HeapOccupancyAggregator extends Aggregator<HeapOccupancyAggregation> {

    private static final double KB_TO_MB = 1.0 / 1024.0;
    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    public HeapOccupancyAggregator(HeapOccupancyAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,          this::onG1Pause);
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
        double sizeMb      = heap.getSizeAfterCollection()      * KB_TO_MB;
        double occupancyMb = heap.getOccupancyAfterCollection() * KB_TO_MB;

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

        double sizeMb      = capacity.getMaxCapacity() * BYTES_TO_MB;
        double occupancyMb = memory.getOccupancyAfter() * BYTES_TO_MB;

        aggregation().recordHeapCapacity(t, sizeMb);
        aggregation().recordHeapOccupancy(category, t, occupancyMb);
    }
}
