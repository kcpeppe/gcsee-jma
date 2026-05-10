package com.kodewerk.jma.aggregation.allocation;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Bytes allocated by the application <em>between consecutive
 * collections</em>, plotted as one scatter point per pause and coloured
 * by the {@link GCCategory} of the pause that consumed the allocation.
 * <p>
 * Pairs with {@link AllocationRateAggregation} (rate over time) and
 * gives the per-collection volume directly — useful for spotting
 * bursty allocation patterns where the rate looks fine on average
 * but individual collections see large spikes.
 */
@Collates(AllocationPerCollectionAggregator.class)
public final class AllocationPerCollectionAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "allocated (MB)", "MB");

    /** Count of negative deltas dropped (concurrent cycle / full GC freed
     *  memory between samples — not application allocation). */
    private long negativeDropCount = 0;

    /** Required by the GCSee module SPI. */
    public AllocationPerCollectionAggregation() {}

    public void recordAllocation(GCCategory category, double tSec, double mb) {
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
                ? negativeDropCount + " inter-collection delta(s) were negative and dropped — "
                  + "concurrent activity freed memory between samples."
                : null;
    }
}
