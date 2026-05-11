package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Companion to {@link HeapOccupancyAggregator} that captures the
 * tenured-pool slice (size + after-collection occupancy) on every
 * generational pause event. G1 and ZGC are not subscribed because they
 * do not expose tenured as a separate {@link MemoryPoolSummary} slot
 * on the pause event the way Parallel / Serial / CMS do.
 */
@Aggregates(EventSource.GENERATIONAL)
public final class TenuredOccupancyAggregator extends Aggregator<TenuredOccupancyAggregation> {

    private static final double KB_TO_MB = 1.0 / 1024.0;

    public TenuredOccupancyAggregator(TenuredOccupancyAggregation aggregation) {
        super(aggregation);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        MemoryPoolSummary tenured = event.getTenured();
        if (tenured == null || !tenured.isValid()) {
            aggregation().noteInvalidSample(t);
            return;
        }
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;
        double sizeMb      = tenured.getSizeAfterCollection()      * KB_TO_MB;
        double occupancyMb = tenured.getOccupancyAfterCollection() * KB_TO_MB;
        aggregation().recordTenuredCapacity(t, sizeMb);
        aggregation().recordTenuredOccupancy(category, t, occupancyMb);
    }
}
