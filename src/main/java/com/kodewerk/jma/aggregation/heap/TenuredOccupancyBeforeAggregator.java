package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.MemoryPoolSummary;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Before-collection counterpart of {@link TenuredOccupancyAggregator}.
 * Same event source and category mapping; reads
 * {@link MemoryPoolSummary#getOccupancyBeforeCollection()} /
 * {@link MemoryPoolSummary#getSizeBeforeCollection()} instead of the
 * after-collection values.
 */
@Aggregates(EventSource.GENERATIONAL)
public final class TenuredOccupancyBeforeAggregator
        extends Aggregator<TenuredOccupancyBeforeAggregation> {

    private static final double KB_TO_MB = 1.0 / 1024.0;

    public TenuredOccupancyBeforeAggregator(TenuredOccupancyBeforeAggregation aggregation) {
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
        double sizeMb      = tenured.getSizeBeforeCollection()      * KB_TO_MB;
        double occupancyMb = tenured.getOccupancyBeforeCollection() * KB_TO_MB;
        aggregation().recordTenuredCapacity(t, sizeMb);
        aggregation().recordTenuredOccupancy(category, t, occupancyMb);
    }
}
