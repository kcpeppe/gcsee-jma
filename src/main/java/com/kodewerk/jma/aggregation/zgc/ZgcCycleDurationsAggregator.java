package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.event.zgc.ZGCFullCollection;
import com.kodewerk.gcsee.event.zgc.ZGCOldCollection;
import com.kodewerk.gcsee.event.zgc.ZGCYoungCollection;

/**
 * Feeds {@link ZgcCycleDurationsAggregation}. Routes the event into one
 * of three series by cycle type — {@code ZGCYoungCollection},
 * {@code ZGCOldCollection}, {@code ZGCFullCollection}.
 */
@Aggregates(EventSource.ZGC)
public final class ZgcCycleDurationsAggregator
        extends Aggregator<ZgcCycleDurationsAggregation> {

    public ZgcCycleDurationsAggregator(ZgcCycleDurationsAggregation aggregation) {
        super(aggregation);
        register(ZGCCollection.class, this::onZgcCycle);
    }

    private void onZgcCycle(ZGCCollection event) {
        if (event.getDateTimeStamp() == null) return;
        double t = event.getDateTimeStamp().toSeconds();
        double durationSec = event.getDuration();
        if (durationSec <= 0.0) return;
        if (event instanceof ZGCYoungCollection)      aggregation().recordYoung(t, durationSec);
        else if (event instanceof ZGCOldCollection)   aggregation().recordOld(t,   durationSec);
        else if (event instanceof ZGCFullCollection)  aggregation().recordFull(t,  durationSec);
        // Unknown ZGCCollection subtypes (defensive) are silently
        // skipped — keeping the chart constrained to the three series
        // listed in the aggregation is cleaner than introducing a
        // catch-all bucket.
    }
}
