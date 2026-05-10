package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/** "Other" sub-phase scatter, percentage of phase total. */
@Collates(G1OtherCollectionAggregator.class)
public final class G1OtherCollectionPercentAggregation extends G1SubPhaseAggregation {

    public G1OtherCollectionPercentAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), true, "% of phase", "%");
    }
}
