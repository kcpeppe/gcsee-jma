package com.kodewerk.jma.aggregation.pause;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Pause-time time series: one point per stop-the-world pause, y-axis in
 * milliseconds, coloured by the collection category (young / mixed /
 * tenured / full / initial-mark).
 * <p>
 * Concurrent-only events never reach this aggregation — the aggregator
 * only subscribes to pause-bearing event types.
 */
@Collates(PauseTimeAggregator.class)
public final class PauseTimeAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "pause (ms)", "ms");

    /** Required by the GCSee module SPI. */
    public PauseTimeAggregation() {}

    /** Add one pause sample. */
    public void recordPause(GCCategory category, double tSec, double pauseMs) {
        data.add(category, tSec, pauseMs);
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
