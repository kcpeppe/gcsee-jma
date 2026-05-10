package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/**
 * Pre-Evacuate Collection Set sub-phase scatter, absolute (ms). One
 * series per (sub-phase × collection-type) combination; sub-phase drives
 * the colour, collection type drives the Chart.js point shape — circle
 * for Young, triangle for Mixed, square for InitialMark.
 */
@Collates(G1PreCollectionAggregator.class)
public final class G1PreCollectionAggregation extends G1SubPhaseAggregation {

    /** Required by the GCSee module SPI. */
    public G1PreCollectionAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), false, "duration (ms)", "ms");
    }
}
