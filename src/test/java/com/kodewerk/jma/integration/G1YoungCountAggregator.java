package com.kodewerk.jma.integration;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1Young;

@Aggregates({EventSource.G1GC})
public final class G1YoungCountAggregator extends Aggregator<G1YoungCountAggregation> {

    public G1YoungCountAggregator(G1YoungCountAggregation aggregation) {
        super(aggregation);
        register(G1Young.class, this::onG1Young);
    }

    private void onG1Young(G1Young event) {
        aggregation().onG1Young(event);
    }
}
