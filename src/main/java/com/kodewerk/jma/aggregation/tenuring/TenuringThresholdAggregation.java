package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * "Calculated tenuring thresholds" chart — one point per young pause
 * showing the tenuring threshold the JVM picked for that collection.
 * Plotted as a single time-series scatter on a seconds x-axis. Mirrors
 * Censum's chart of the same name.
 * <p>
 * Threshold values are integers in {@code [0, maxTenuringThreshold]}; we
 * emit them as doubles so the existing scatter-rendering plumbing works
 * unchanged. A single colour suffices because there is only one
 * conceptual series — the threshold the collector chose at that moment
 * in time.
 */
@Collates(TenuringAggregator.class)
public final class TenuringThresholdAggregation extends JmaAggregation
        implements TenuringSink {

    private static final String SERIES_COLOR = "#1e3a8a";

    private final List<XYSeriesData.Point> points = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public TenuringThresholdAggregation() {}

    @Override
    public void record(double tSec, SurvivorRecord r) {
        points.add(new XYSeriesData.Point(tSec, r.getCalculatedTenuringThreshold()));
    }

    public XYSeriesData getData() {
        XYSeriesData.Series series = new XYSeriesData.Series(
                "calculated-threshold", "Calculated threshold",
                SERIES_COLOR, List.copyOf(points));
        return new XYSeriesData(
                "time (s)", "Tenuring threshold", "", List.of(series));
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
