package com.kodewerk.jma.aggregation.metaspace;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCMetaspaceSummary;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Metaspace counterpart of {@link com.kodewerk.jma.aggregation.heap.HeapOccupancyAggregator}.
 * Pulls a metaspace pool summary off every pause / cycle event the
 * dispatcher fans out and feeds it into a single
 * {@link MetaspaceOccupancyAggregation}.
 * <p>
 * Sources, in normalisation order:
 * <ul>
 *   <li>G1 / Generational pause events expose
 *       {@link MemoryPoolSummary} via {@code getPermOrMetaspace()} —
 *       same KB-scaled pool type the heap chart uses, so we apply the
 *       same KB→MB conversion.</li>
 *   <li>ZGC collection events expose a separate
 *       {@link ZGCMetaspaceSummary} (used / committed in KB), kept
 *       parallel because ZGC keeps metaspace bookkeeping outside of the
 *       main heap memory summary.</li>
 * </ul>
 * Concurrent-only events that don't carry a metaspace sample are quietly
 * skipped (and counted into the warning flag) — same shape as the heap
 * aggregator.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class MetaspaceOccupancyAggregator extends Aggregator<MetaspaceOccupancyAggregation> {

    private static final double KB_TO_MB = 1.0 / 1024.0;

    public MetaspaceOccupancyAggregator(MetaspaceOccupancyAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        MemoryPoolSummary metaspace = event.getPermOrMetaspace();
        if (metaspace == null || !metaspace.isValid()) {
            aggregation().noteInvalidSample();
            return;
        }
        recordFromMemoryPool(event, metaspace);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        MemoryPoolSummary metaspace = event.getPermOrMetaspace();
        if (metaspace == null || !metaspace.isValid()) {
            aggregation().noteInvalidSample();
            return;
        }
        recordFromMemoryPool(event, metaspace);
    }

    /** Shared path for anything that exposes metaspace via {@link MemoryPoolSummary} (KB). */
    private void recordFromMemoryPool(JVMEvent event, MemoryPoolSummary metaspace) {
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        double t = event.getDateTimeStamp().toSeconds();
        double sizeMb      = metaspace.getSizeAfterCollection()      * KB_TO_MB;
        double occupancyMb = metaspace.getOccupancyAfterCollection() * KB_TO_MB;

        aggregation().recordMetaspaceCapacity(t, sizeMb);
        aggregation().recordMetaspaceOccupancy(category, t, occupancyMb);
    }

    private void onZgcCycle(ZGCCollection event) {
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        ZGCMetaspaceSummary metaspace = event.getMetaspaceSummary();
        if (metaspace == null) {
            aggregation().noteInvalidSample();
            return;
        }

        double t = event.getDateTimeStamp().toSeconds();
        double sizeMb      = metaspace.getCommitted() * KB_TO_MB;
        double occupancyMb = metaspace.getUsed()      * KB_TO_MB;

        aggregation().recordMetaspaceCapacity(t, sizeMb);
        aggregation().recordMetaspaceOccupancy(category, t, occupancyMb);
    }
}
