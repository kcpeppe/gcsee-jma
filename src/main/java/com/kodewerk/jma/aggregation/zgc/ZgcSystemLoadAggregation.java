package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * System-load time series captured at each ZGC cycle. ZGC writes the
 * three Linux load averages — 1, 5, and 15 minute — into each
 * collection event via {@code getLoad()}, so the chart is just a
 * pass-through with one point per series per cycle.
 * <p>
 * Cross-references the MMU view: pause-time MMU dropping while system
 * load stays low points at GC; both dropping together points at host
 * pressure outside the JVM.
 */
@Collates(ZgcSystemLoadAggregator.class)
public final class ZgcSystemLoadAggregation extends JmaAggregation {

    private final List<XYSeriesData.Point> load1  = new ArrayList<>();
    private final List<XYSeriesData.Point> load5  = new ArrayList<>();
    private final List<XYSeriesData.Point> load15 = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public ZgcSystemLoadAggregation() {}

    public void record(double tSec, double l1, double l5, double l15) {
        if (l1  >= 0.0) load1.add(new XYSeriesData.Point(tSec, l1));
        if (l5  >= 0.0) load5.add(new XYSeriesData.Point(tSec, l5));
        if (l15 >= 0.0) load15.add(new XYSeriesData.Point(tSec, l15));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "load avg", "",
                List.of(
                        new XYSeriesData.Series("1m",  "1 min",  "#fb8c00", load1),
                        new XYSeriesData.Series("5m",  "5 min",  "#1e88e5", load5),
                        new XYSeriesData.Series("15m", "15 min", "#43a047", load15)));
    }

    @Override
    public boolean isEmpty() {
        return load1.isEmpty() && load5.isEmpty() && load15.isEmpty();
    }

    @Override
    public boolean hasWarning() { return false; }
}
