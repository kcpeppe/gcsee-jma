package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/** Post-Evacuate Collection Set sub-phase scatter, percentage of phase total. */
@Collates(G1PostCollectionAggregator.class)
public final class G1PostCollectionPercentAggregation extends G1SubPhaseAggregation {

    public G1PostCollectionPercentAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), true, "% of phase", "%");
    }
}
