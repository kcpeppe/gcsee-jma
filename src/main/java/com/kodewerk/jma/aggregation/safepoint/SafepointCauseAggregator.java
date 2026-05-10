package com.kodewerk.jma.aggregation.safepoint;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.jvm.Safepoint;

/**
 * Counts {@link Safepoint} events by VM operation name and feeds
 * {@link SafepointCauseAggregation} for the cause histogram view.
 */
@Aggregates(EventSource.SAFEPOINT)
public final class SafepointCauseAggregator extends Aggregator<SafepointCauseAggregation> {

    public SafepointCauseAggregator(SafepointCauseAggregation aggregation) {
        super(aggregation);
        register(Safepoint.class, this::onSafepoint);
    }

    private void onSafepoint(Safepoint event) {
        aggregation().recordCause(event.getVmOperation());
    }
}
