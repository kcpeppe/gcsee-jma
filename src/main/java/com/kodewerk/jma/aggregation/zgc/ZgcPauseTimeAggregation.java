package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * ZGC stop-the-world pause durations, split by sub-pause type. Each
 * cycle contributes up to three points — one each at
 * {@code pauseMarkStart}, {@code pauseMarkEnd}, {@code pauseRelocateStart}
 * — so the chart shows the three sub-pause magnitudes evolving
 * independently over the log timeline.
 * <p>
 * Cycle type (Young / Old / Full) is intentionally collapsed into the
 * sub-pause categorisation: ZGC's sub-pause shapes are similar across
 * cycle types, and the "where is my STW time going" question is more
 * naturally answered by sub-pause than by cycle type. Cycle-type
 * breakdowns live on the dedicated cycle-duration view.
 */
@Collates(ZgcPauseTimeAggregator.class)
public final class ZgcPauseTimeAggregation extends JmaAggregation {

    private final List<XYSeriesData.Point> markStart    = new ArrayList<>();
    private final List<XYSeriesData.Point> markEnd      = new ArrayList<>();
    private final List<XYSeriesData.Point> relocateStrt = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public ZgcPauseTimeAggregation() {}

    public void recordMarkStart(double tSec, double durationMs) {
        markStart.add(new XYSeriesData.Point(tSec, durationMs));
    }

    public void recordMarkEnd(double tSec, double durationMs) {
        markEnd.add(new XYSeriesData.Point(tSec, durationMs));
    }

    public void recordRelocateStart(double tSec, double durationMs) {
        relocateStrt.add(new XYSeriesData.Point(tSec, durationMs));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "pause (ms)", "ms",
                List.of(
                        new XYSeriesData.Series("mark-start",     "Mark Start",     "#1e88e5", markStart),
                        new XYSeriesData.Series("mark-end",       "Mark End",       "#fb8c00", markEnd),
                        new XYSeriesData.Series("relocate-start", "Relocate Start", "#43a047", relocateStrt)));
    }

    @Override
    public boolean isEmpty() {
        return markStart.isEmpty() && markEnd.isEmpty() && relocateStrt.isEmpty();
    }

    @Override
    public boolean hasWarning() { return false; }
}
