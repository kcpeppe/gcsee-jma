package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;

/**
 * Feeds {@link ZgcSystemLoadAggregation}. ZGC stamps each cycle with
 * the three Linux load averages from the host; we read them straight
 * off {@code getLoad()} and forward to the aggregation.
 * <p>
 * The {@code load} array is sized 3 on cycles that include the line,
 * empty / null on logs from JVMs not running with {@code Xlog:gc*=info}
 * (no load output) — defensively length-check before indexing so a
 * shorter array doesn't AIOBE the whole pipeline.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcSystemLoadAggregator extends Aggregator<ZgcSystemLoadAggregation> {

    public ZgcSystemLoadAggregator(ZgcSystemLoadAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        double[] load = event.getLoad();
        if (load == null || load.length == 0) return;
        double t   = event.getDateTimeStamp().toSeconds();
        double l1  = load.length > 0 ? load[0] : -1.0;
        double l5  = load.length > 1 ? load[1] : -1.0;
        double l15 = load.length > 2 ? load[2] : -1.0;
        aggregation().record(t, l1, l5, l15);
    }
}
