package com.kodewerk.jma.aggregation.safepoint;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.jvm.Safepoint;

/**
 * Captures every {@link Safepoint} event from the safepoint parser
 * and feeds {@link SafepointDurationAggregation} for the duration
 * scatter view.
 */
@Aggregates(EventSource.SAFEPOINT)
public final class SafepointDurationAggregator extends Aggregator<SafepointDurationAggregation> {

    private static final double SECONDS_TO_MILLIS = 1000.0;

    public SafepointDurationAggregator(SafepointDurationAggregation aggregation) {
        super(aggregation);
        register(Safepoint.class, this::onSafepoint);
    }

    private void onSafepoint(Safepoint event) {
        if (event.getDateTimeStamp() == null) return;
        double durationMs = event.getDuration() * SECONDS_TO_MILLIS;
        if (durationMs <= 0.0) return;
        aggregation().recordSafepoint(event.getDateTimeStamp().toSeconds(), durationMs);
    }
}
