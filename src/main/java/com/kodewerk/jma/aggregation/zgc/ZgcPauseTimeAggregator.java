package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.time.DateTimeStamp;

/**
 * Feeds {@link ZgcPauseTimeAggregation}. Each ZGCCollection contributes
 * up to three points — one per sub-pause — placed on the chart at the
 * sub-pause's own timestamp (not the cycle's overall start), so the
 * mark-end point lands where mark-end actually fired.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcPauseTimeAggregator extends Aggregator<ZgcPauseTimeAggregation> {

    private static final double SEC_TO_MS = 1000.0;

    public ZgcPauseTimeAggregator(ZgcPauseTimeAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        emit(event.getPauseMarkStartTimeStamp(),     event.getPauseMarkStartDuration(),     0);
        emit(event.getPauseMarkEndTimeStamp(),       event.getPauseMarkEndDuration(),       1);
        emit(event.getPauseRelocateStartTimeStamp(), event.getPauseRelocateStartDuration(), 2);
    }

    private void emit(DateTimeStamp ts, double durationSec, int seriesIdx) {
        if (ts == null || durationSec <= 0.0) return;
        double t  = ts.toSeconds();
        double ms = durationSec * SEC_TO_MS;
        switch (seriesIdx) {
            case 0 -> aggregation().recordMarkStart(t, ms);
            case 1 -> aggregation().recordMarkEnd(t, ms);
            case 2 -> aggregation().recordRelocateStart(t, ms);
            default -> { /* unreachable */ }
        }
    }
}
