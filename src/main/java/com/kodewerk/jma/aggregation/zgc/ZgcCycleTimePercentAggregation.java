package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-cycle concurrent-phase share, expressed as a percentage of the
 * cycle's total wall-clock duration. Same series set as
 * {@link ZgcConcurrentPhaseDurationsAggregation} (so the two views read
 * as a matched pair) but normalised by cycle duration — useful for
 * spotting "Mark grew from 30 % to 70 % of the cycle" patterns even
 * as cycle length itself drifts.
 */
@Collates(ZgcCycleTimePercentAggregator.class)
public final class ZgcCycleTimePercentAggregation extends JmaAggregation {

    private final Map<String, List<XYSeriesData.Point>> seriesPoints = new LinkedHashMap<>();

    /** Required by the GCSee module SPI. */
    public ZgcCycleTimePercentAggregation() {
        for (String id : ZgcPhaseHarvest.SERIES_LABELS.keySet()) {
            seriesPoints.put(id, new ArrayList<>());
        }
    }

    public void recordCycle(double tSec, double cycleDurationSec, Map<String, Double> phases) {
        if (cycleDurationSec <= 0.0) return;
        for (Map.Entry<String, Double> e : phases.entrySet()) {
            double durationSec = e.getValue();
            if (durationSec <= 0.0) continue;
            double pct = 100.0 * durationSec / cycleDurationSec;
            List<XYSeriesData.Point> bucket = seriesPoints.get(e.getKey());
            if (bucket != null) bucket.add(new XYSeriesData.Point(tSec, pct));
        }
    }

    public XYSeriesData getData() {
        List<XYSeriesData.Series> series = new ArrayList<>(ZgcPhaseHarvest.SERIES_LABELS.size());
        for (Map.Entry<String, String> e : ZgcPhaseHarvest.SERIES_LABELS.entrySet()) {
            String id = e.getKey();
            series.add(new XYSeriesData.Series(
                    id,
                    e.getValue(),
                    ZgcPhaseHarvest.SERIES_COLORS.get(id),
                    seriesPoints.getOrDefault(id, List.of())));
        }
        return new XYSeriesData("time (s)", "% of cycle", "%", series);
    }

    @Override
    public boolean isEmpty() {
        for (List<XYSeriesData.Point> pts : seriesPoints.values()) {
            if (!pts.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean hasWarning() { return false; }
}
