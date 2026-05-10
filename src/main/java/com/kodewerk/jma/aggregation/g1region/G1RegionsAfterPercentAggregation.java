package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.StackedBarData;

import java.util.ArrayList;
import java.util.List;

/**
 * G1 per-pause region counts <em>after</em> collection, rescaled per
 * event so every stack sums to ~100%. The mirror of
 * {@link G1RegionsBeforePercentAggregation} for post-collection
 * occupancy — together they show how the relative region mix
 * shifts across each pause.
 */
@Collates(G1RegionsAfterPercentAggregator.class)
public final class G1RegionsAfterPercentAggregation extends JmaAggregation {

    private final List<G1RegionHarvest.Snapshot> rows = new ArrayList<>();
    private long eventsWithoutRegionData = 0;

    /** Required by the GCSee module SPI. */
    public G1RegionsAfterPercentAggregation() {}

    void recordSnapshot(G1RegionHarvest.Snapshot snap) {
        rows.add(snap);
    }

    void noteMissingRegionData() {
        eventsWithoutRegionData++;
    }

    public StackedBarData getData() {
        StackedBarData out = new StackedBarData(
                "time (s)", "% of regions", "%", G1RegionPalette.seriesList());
        for (G1RegionHarvest.Snapshot snap : rows) {
            double[] absolute = G1RegionPalette.valuesOf(snap.after());
            out.add(snap.tSec(), toPercent(absolute));
        }
        return out;
    }

    /** Linear rescale to per-event % — preserves zeros when total is zero. */
    private static double[] toPercent(double[] values) {
        double total = 0.0;
        for (double v : values) total += v;
        if (total <= 0.0) return values;
        double[] out = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = values[i] / total * 100.0;
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return eventsWithoutRegionData > 0;
    }

    @Override
    public String getWarningMessage() {
        if (eventsWithoutRegionData == 0) return null;
        return eventsWithoutRegionData + " G1 pause event(s) did not carry region "
                + "detail and were skipped.";
    }
}
