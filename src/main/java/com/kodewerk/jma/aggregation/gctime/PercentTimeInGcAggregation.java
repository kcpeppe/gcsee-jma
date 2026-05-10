package com.kodewerk.jma.aggregation.gctime;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Per-event % time in GC: for each stop-the-world pause we record
 * {@code pauseDuration / (thisEventStart − previousEventStart) × 100}.
 * <p>
 * Plotted as a scatter coloured by {@link GCCategory}. Pairs naturally
 * with the pause-time chart — the latter shows duration in milliseconds,
 * this one shows what fraction of the inter-collection window the
 * application lost to that pause. Easy way to spot pauses that look
 * short on the pause chart but happened in a tight cluster, or pauses
 * that look long but had a long gap behind them.
 * <p>
 * Y-unit is "%" so the frontend's existing 100-clamp on the scatter
 * renderer keeps the y-axis pinned at 100.
 */
@Collates(PercentTimeInGcAggregator.class)
public final class PercentTimeInGcAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "% time in GC", "%");

    /** Required by the GCSee module SPI. */
    public PercentTimeInGcAggregation() {}

    public void recordPercent(GCCategory category, double tSec, double pct) {
        data.add(category, tSec, pct);
    }

    public ScatterData getData() {
        return data;
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
