package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Cycle pacing chart. Two series, both plotted at the cycle's start
 * timestamp:
 * <ul>
 *   <li><b>Duration</b> — the cycle's own wall-clock span
 *       ({@code event.getDuration()}).</li>
 *   <li><b>Interval</b> — gap since the previous cycle (of any type)
 *       started. First cycle in the log has no predecessor and
 *       contributes no interval point.</li>
 * </ul>
 * Reading the two together shows whether cycles are firing too close
 * together (interval ≤ duration → back-to-back), at a comfortable
 * cadence (interval ≫ duration), or stretching as the heap grows
 * (duration rises, interval stays steady).
 */
@Collates(ZgcCycleDurationsAndIntervalsAggregator.class)
public final class ZgcCycleDurationsAndIntervalsAggregation extends JmaAggregation {

    private final List<XYSeriesData.Point> duration = new ArrayList<>();
    private final List<XYSeriesData.Point> interval = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public ZgcCycleDurationsAndIntervalsAggregation() {}

    public void recordCycleDuration(double tSec, double durationSec) {
        duration.add(new XYSeriesData.Point(tSec, durationSec));
    }

    public void recordCycleInterval(double tSec, double intervalSec) {
        interval.add(new XYSeriesData.Point(tSec, intervalSec));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "seconds", "s",
                List.of(
                        new XYSeriesData.Series("duration", "Duration", "#1e88e5", duration),
                        new XYSeriesData.Series("interval", "Interval", "#fb8c00", interval)));
    }

    @Override
    public boolean isEmpty() {
        return duration.isEmpty() && interval.isEmpty();
    }

    @Override
    public boolean hasWarning() { return false; }
}
