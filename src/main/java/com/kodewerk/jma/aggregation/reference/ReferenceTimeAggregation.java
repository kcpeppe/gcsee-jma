package com.kodewerk.jma.aggregation.reference;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Reference-processing time scatter — one point per pause per
 * reference type with non-zero pause-time contribution. Same series
 * (Soft / Weak / Final / Phantom) and same colours as
 * {@link ReferenceCountAggregation}, plotted as milliseconds.
 * <p>
 * The two reference views read together: the count chart says how
 * many references the pause processed, the time chart says how
 * expensive that processing was. A pause with high count but low time
 * is healthy; a pause with low count but high time is unusual and
 * worth investigating.
 */
@Collates(ReferenceProcessingAggregator.class)
public final class ReferenceTimeAggregation extends ReferenceProcessingAggregation {

    private static final String COLOR_SOFT    = "#fb8c00";
    private static final String COLOR_WEAK    = "#fdd835";
    private static final String COLOR_FINAL   = "#1e88e5";
    private static final String COLOR_PHANTOM = "#6d4c41";

    public ReferenceTimeAggregation() {}

    public XYSeriesData getData() {
        List<XYSeriesData.Point> soft    = new ArrayList<>();
        List<XYSeriesData.Point> weak    = new ArrayList<>();
        List<XYSeriesData.Point> finalP  = new ArrayList<>();
        List<XYSeriesData.Point> phantom = new ArrayList<>();

        for (EventRow r : rows()) {
            if (r.softMs()    > 0.0) soft   .add(new XYSeriesData.Point(r.tSec(), r.softMs()));
            if (r.weakMs()    > 0.0) weak   .add(new XYSeriesData.Point(r.tSec(), r.weakMs()));
            if (r.finalMs()   > 0.0) finalP .add(new XYSeriesData.Point(r.tSec(), r.finalMs()));
            if (r.phantomMs() > 0.0) phantom.add(new XYSeriesData.Point(r.tSec(), r.phantomMs()));
        }

        return new XYSeriesData(
                "time (s)", "duration (ms)", "ms",
                java.util.List.of(
                        new XYSeriesData.Series("soft",    "Soft",    COLOR_SOFT,    soft),
                        new XYSeriesData.Series("weak",    "Weak",    COLOR_WEAK,    weak),
                        new XYSeriesData.Series("final",   "Final",   COLOR_FINAL,   finalP),
                        new XYSeriesData.Series("phantom", "Phantom", COLOR_PHANTOM, phantom)));
    }
}
