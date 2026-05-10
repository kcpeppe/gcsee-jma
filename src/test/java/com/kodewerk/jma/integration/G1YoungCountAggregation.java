package com.kodewerk.jma.integration;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.g1gc.G1Young;
import com.kodewerk.jma.aggregation.JmaAggregation;

@Collates(G1YoungCountAggregator.class)
public final class G1YoungCountAggregation extends JmaAggregation {

    private int count = 0;

    public void onG1Young(G1Young event) {
        count++;
    }

    public int getCount() {
        return count;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return count == 0;
    }
}
