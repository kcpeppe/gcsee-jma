package com.kodewerk.jma.aggregation.reference;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference-processing count scatter — one point per pause per
 * reference type with non-zero count, four named series:
 * <ul>
 *   <li>Soft   (orange)</li>
 *   <li>Weak   (yellow)</li>
 *   <li>Final  (blue)</li>
 *   <li>Phantom (brown)</li>
 * </ul>
 * Series colours stay in lock-step with the matching time view so a
 * point pair on the two charts always reads as the same reference
 * type.
 */
@Collates(ReferenceProcessingAggregator.class)
public final class ReferenceCountAggregation extends ReferenceProcessingAggregation {

    private static final String COLOR_SOFT    = "#fb8c00";
    private static final String COLOR_WEAK    = "#fdd835";
    private static final String COLOR_FINAL   = "#1e88e5";
    private static final String COLOR_PHANTOM = "#6d4c41";

    /** Required by the GCSee module SPI. */
    public ReferenceCountAggregation() {}

    public XYSeriesData getData() {
        List<XYSeriesData.Point> soft    = new ArrayList<>();
        List<XYSeriesData.Point> weak    = new ArrayList<>();
        List<XYSeriesData.Point> finalP  = new ArrayList<>();
        List<XYSeriesData.Point> phantom = new ArrayList<>();

        for (EventRow r : rows()) {
            if (r.softCount()    > 0) soft   .add(new XYSeriesData.Point(r.tSec(), r.softCount()));
            if (r.weakCount()    > 0) weak   .add(new XYSeriesData.Point(r.tSec(), r.weakCount()));
            if (r.finalCount()   > 0) finalP .add(new XYSeriesData.Point(r.tSec(), r.finalCount()));
            if (r.phantomCount() > 0) phantom.add(new XYSeriesData.Point(r.tSec(), r.phantomCount()));
        }

        return new XYSeriesData(
                "time (s)", "reference count", "",
                java.util.List.of(
                        new XYSeriesData.Series("soft",    "Soft",    COLOR_SOFT,    soft),
                        new XYSeriesData.Series("weak",    "Weak",    COLOR_WEAK,    weak),
                        new XYSeriesData.Series("final",   "Final",   COLOR_FINAL,   finalP),
                        new XYSeriesData.Series("phantom", "Phantom", COLOR_PHANTOM, phantom)));
    }
}
