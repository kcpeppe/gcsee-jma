package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Total cycle duration over time, one series per ZGC cycle type
 * (Young / Old / Full). "Duration" is GCSee's
 * {@code event.getDuration()} — the wall-clock span the cycle occupied,
 * including its concurrent work and its three STW sub-pauses. Useful
 * for spotting Old cycles that are stretching as the heap grows, or
 * Young cycles whose duration drifts when the allocation rate rises.
 */
@Collates(ZgcCycleDurationsAggregator.class)
public final class ZgcCycleDurationsAggregation extends JmaAggregation {

    private final List<XYSeriesData.Point> young = new ArrayList<>();
    private final List<XYSeriesData.Point> old   = new ArrayList<>();
    private final List<XYSeriesData.Point> full  = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public ZgcCycleDurationsAggregation() {}

    public void recordYoung(double tSec, double durationSec) {
        young.add(new XYSeriesData.Point(tSec, durationSec));
    }

    public void recordOld(double tSec, double durationSec) {
        old.add(new XYSeriesData.Point(tSec, durationSec));
    }

    public void recordFull(double tSec, double durationSec) {
        full.add(new XYSeriesData.Point(tSec, durationSec));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "cycle duration (s)", "s",
                List.of(
                        new XYSeriesData.Series("young", "Young", "#1e88e5", young),
                        new XYSeriesData.Series("old",   "Old",   "#fb8c00", old),
                        new XYSeriesData.Series("full",  "Full",  "#e53935", full)));
    }

    @Override
    public boolean isEmpty() {
        return young.isEmpty() && old.isEmpty() && full.isEmpty();
    }

    @Override
    public boolean hasWarning() { return false; }
}
