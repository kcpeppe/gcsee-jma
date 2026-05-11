package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eight concurrent phase durations plotted over the log timeline, one
 * series per phase. Zero-duration samples (phase didn't fire in this
 * cycle, e.g. Mark Continue on a Young) are dropped rather than
 * plotted as zero points — sparse-but-real reads better than dense-
 * with-zeros.
 */
@Collates(ZgcConcurrentPhaseDurationsAggregator.class)
public final class ZgcConcurrentPhaseDurationsAggregation extends JmaAggregation {

    /** Series id → ordered point list. Keys mirror {@link ZgcPhaseHarvest#SERIES_LABELS}. */
    private final Map<String, List<XYSeriesData.Point>> seriesPoints = new LinkedHashMap<>();

    /** Required by the GCSee module SPI. */
    public ZgcConcurrentPhaseDurationsAggregation() {
        for (String id : ZgcPhaseHarvest.SERIES_LABELS.keySet()) {
            seriesPoints.put(id, new ArrayList<>());
        }
    }

    /** Record one cycle's phases — {@code phases} is keyed by series id, values in seconds. */
    public void recordCycle(double tSec, Map<String, Double> phases) {
        for (Map.Entry<String, Double> e : phases.entrySet()) {
            double durationSec = e.getValue();
            if (durationSec <= 0.0) continue;
            List<XYSeriesData.Point> bucket = seriesPoints.get(e.getKey());
            if (bucket != null) {
                bucket.add(new XYSeriesData.Point(tSec, durationSec * 1000.0));
            }
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
        return new XYSeriesData("time (s)", "duration (ms)", "ms", series);
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
