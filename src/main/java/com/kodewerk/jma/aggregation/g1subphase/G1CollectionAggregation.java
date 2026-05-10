package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/**
 * Evacuate Collection Set sub-phase scatter, absolute (ms). The
 * sub-phase value is the slowest-worker {@code max} from the
 * {@code StatisticalSummary} GCSee returns — i.e. the wall-clock
 * contribution of the sub-phase to the pause.
 */
@Collates(G1CollectionAggregator.class)
public final class G1CollectionAggregation extends G1SubPhaseAggregation {

    public G1CollectionAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), false, "duration (ms)", "ms");
    }
}
