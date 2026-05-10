package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/** Post-Evacuate Collection Set sub-phase scatter, absolute (ms). */
@Collates(G1PostCollectionAggregator.class)
public final class G1PostCollectionAggregation extends G1SubPhaseAggregation {

    public G1PostCollectionAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), false, "duration (ms)", "ms");
    }
}
