package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

/**
 * "Other" sub-phase scatter, absolute (ms). The sub-phase set is
 * synthesised from individual fields on the GCSee API
 * (codeRootFixup, codeRootMigration, codeRootPurge, clearCT,
 * expandHeap, stringDeduping); zero-duration entries are omitted on
 * a per-event basis.
 */
@Collates(G1OtherCollectionAggregator.class)
public final class G1OtherCollectionAggregation extends G1SubPhaseAggregation {

    public G1OtherCollectionAggregation() {}

    public XYSeriesData getData() {
        return G1SubPhaseSeriesBuilder.build(rows(), false, "duration (ms)", "ms");
    }
}
