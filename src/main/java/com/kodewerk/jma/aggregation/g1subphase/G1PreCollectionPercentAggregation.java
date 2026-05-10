package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/**
 * Pre-Evacuate Collection Set sub-phase scatter, expressed as a
 * percentage of the phase total per event. Same input data as
 * {@link G1PreCollectionAggregation}; only the y-axis transform differs.
 */
@Collates(G1PreCollectionAggregator.class)
public final class G1PreCollectionPercentAggregation extends G1SubPhaseAggregation {

    /** Required by the GCSee module SPI. */
    public G1PreCollectionPercentAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), true, "% of phase", "%");
    }
}
