package com.kodewerk.jma.aggregation.safepoint;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Safepoint duration scatter — one point per parsed
 * {@link com.kodewerk.gcsee.event.jvm.Safepoint} event, plotted as
 * milliseconds. Single series; the safepoint cause histogram lives on
 * its own view because the visual encoding is different (categorical
 * bars vs. continuous time).
 */
@Collates(SafepointDurationAggregator.class)
public final class SafepointDurationAggregation extends JmaAggregation {

    private static final String SERIES_COLOR = "#1e3a8a";

    private final List<XYSeriesData.Point> points = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public SafepointDurationAggregation() {}

    public void recordSafepoint(double tSec, double durationMs) {
        points.add(new XYSeriesData.Point(tSec, durationMs));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "duration (ms)", "ms",
                java.util.List.of(
                        new XYSeriesData.Series("safepoint", "Safepoint",
                                                SERIES_COLOR, java.util.List.copyOf(points))));
    }

    @Override
    public boolean isEmpty() {
        return points.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
