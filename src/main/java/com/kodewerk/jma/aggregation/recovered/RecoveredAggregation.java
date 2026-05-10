package com.kodewerk.jma.aggregation.recovered;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Bytes recovered per collection — {@code occupancyBefore − occupancyAfter}
 * for each pause-bearing event, plotted as one scatter point coloured by
 * the event's {@link GCCategory}. Pairs naturally with the allocation-
 * per-collection chart: the two together show what each collection was
 * fed and what it gave back.
 * <p>
 * Negative values (very rare — a collection that grew the heap, e.g.
 * allocation failure during evacuation) are dropped so the y-axis
 * reads cleanly as "memory freed". The drop count is surfaced via
 * {@link #hasWarning()}.
 */
@Collates(RecoveredAggregator.class)
public final class RecoveredAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "recovered (MB)", "MB");

    private long negativeDropCount = 0;

    /** Required by the GCSee module SPI. */
    public RecoveredAggregation() {}

    public void recordRecovered(GCCategory category, double tSec, double mb) {
        data.add(category, tSec, mb);
    }

    public void noteNegativeDrop() {
        negativeDropCount++;
    }

    public ScatterData getData() {
        return data;
    }

    public long getNegativeDropCount() { return negativeDropCount; }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return negativeDropCount > 0;
    }

    @Override
    public String getWarningMessage() {
        return negativeDropCount > 0
                ? negativeDropCount + " collection(s) reported a negative recovery and were dropped."
                : null;
    }
}
