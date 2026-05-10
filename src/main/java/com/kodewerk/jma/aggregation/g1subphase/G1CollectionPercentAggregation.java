package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/** Evacuate Collection Set sub-phase scatter, percentage of phase total. */
@Collates(G1CollectionAggregator.class)
public final class G1CollectionPercentAggregation extends G1SubPhaseAggregation {

    public G1CollectionPercentAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), true, "% of phase", "%");
    }
}
