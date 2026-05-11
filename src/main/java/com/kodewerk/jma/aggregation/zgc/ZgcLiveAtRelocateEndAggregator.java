package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCLiveSummary;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Relocate-end counterpart of {@link ZgcLiveAtRelocateStartAggregator}.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcLiveAtRelocateEndAggregator
        extends Aggregator<ZgcLiveAtRelocateEndAggregation> {

    private static final double BYTES_TO_MB = 1.0 / (1024.0 * 1024.0);

    public ZgcLiveAtRelocateEndAggregator(ZgcLiveAtRelocateEndAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        double t = event.getDateTimeStamp().toSeconds();
        aggregation().noteEventSeen(t);
        ZGCLiveSummary live = event.getLiveSummary();
        if (live == null) {
            aggregation().noteInvalidSample(t);
            return;
        }
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;
        aggregation().recordLive(category, t, live.getRelocateEnd() * BYTES_TO_MB);
    }
}
