package com.kodewerk.jma.aggregation.recovered;

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
 * Computes bytes recovered <em>per collection</em> as
 * {@code occupancyBefore − occupancyAfter} from the same heap summary
 * the existing heap-occupancy views read. No prev-state tracking
 * needed — the recovery for one event is a self-contained reading
 * within that event's own summary.
 * <p>
 * Unit normalisation matches every other heap-derived view: KB → MB
 * for G1 / Generational, bytes → MB for ZGC. A negative value
 * (pathological — a collection that grew the heap, e.g. evacuation
 * failure that triggered a hidden expand) is counted into the
 * aggregation's drop counter rather than plotted.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class RecoveredAggregator extends Aggregator<RecoveredAggregation> {

    private static final double KB_TO_MB    = 1.0 / 1024.0;
    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    public RecoveredAggregator(RecoveredAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        double recoveredMb = (heap.getOccupancyBeforeCollection()
                              - heap.getOccupancyAfterCollection()) * KB_TO_MB;
        record(event, recoveredMb);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        MemoryPoolSummary heap = event.getHeap();
        if (heap == null || !heap.isValid()) return;
        double recoveredMb = (heap.getOccupancyBeforeCollection()
                              - heap.getOccupancyAfterCollection()) * KB_TO_MB;
        record(event, recoveredMb);
    }

    private void onZgcCycle(ZGCCollection event) {
        ZGCMemorySummary memory = event.getMemorySummary();
        if (memory == null) return;
        double recoveredMb = (memory.getOccupancyBefore()
                              - memory.getOccupancyAfter()) * BYTES_TO_MB;
        record(event, recoveredMb);
    }

    private void record(JVMEvent event, double recoveredMb) {
        if (recoveredMb < 0.0) {
            aggregation().noteNegativeDrop();
            return;
        }
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;
        aggregation().recordRecovered(category,
                event.getDateTimeStamp().toSeconds(), recoveredMb);
    }
}
