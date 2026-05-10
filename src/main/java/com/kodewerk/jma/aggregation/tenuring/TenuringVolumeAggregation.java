package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * "Tenuring distribution" chart — average bytes-at-age across the whole
 * log, plotted as one scatter point per age. Mirrors Censum's chart of
 * the same name.
 * <p>
 * X-axis is age (1..max), y-axis is average occupancy in KBytes (the
 * underlying SurvivorRecord stores bytes; we divide by 1024 at emit time
 * to match the convention used by the Censum charts the user is
 * replicating). One series, one colour, no time axis: this is a steady-
 * state distribution, not a time series.
 */
@Collates(TenuringAggregator.class)
public final class TenuringVolumeAggregation extends JmaAggregation
        implements TenuringSink {

    /** Fixed series colour — single-series chart, dark blue for legibility. */
    private static final String SERIES_COLOR = "#1e3a8a";
    private static final double BYTES_TO_KB  = 1.0 / 1024.0;

    /** Sum + count per age for the average computation at emit time. */
    private static final class AgeAccumulator {
        long sumBytes = 0;
        long count    = 0;
    }

    private final TreeMap<Integer, AgeAccumulator> perAge = new TreeMap<>();

    /** Required by the GCSee module SPI. */
    public TenuringVolumeAggregation() {}

    @Override
    public void record(double tSec, SurvivorRecord r) {
        long[] bytesByAge = r.getBytesAtEachAge();
        if (bytesByAge == null) return;
        for (int age = 1; age < bytesByAge.length; age++) {
            AgeAccumulator a = perAge.computeIfAbsent(age, k -> new AgeAccumulator());
            a.sumBytes += bytesByAge[age];
            a.count    += 1;
        }
    }

    public XYSeriesData getData() {
        List<XYSeriesData.Point> points = new ArrayList<>(perAge.size());
        for (var entry : perAge.entrySet()) {
            AgeAccumulator a = entry.getValue();
            if (a.count == 0) continue;
            double avgKb = (a.sumBytes / (double) a.count) * BYTES_TO_KB;
            points.add(new XYSeriesData.Point(entry.getKey(), avgKb));
        }
        XYSeriesData.Series series = new XYSeriesData.Series(
                "avg-volume", "Avg volume", SERIES_COLOR, points);
        return new XYSeriesData(
                "age", "Avg volume (KBytes)", "KB", List.of(series));
    }

    @Override
    public boolean isEmpty() {
        for (AgeAccumulator a : perAge.values()) if (a.count > 0) return false;
        return true;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
