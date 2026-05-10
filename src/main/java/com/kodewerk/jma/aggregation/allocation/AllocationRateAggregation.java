package com.kodewerk.jma.aggregation.allocation;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Allocation-rate time series: MB of application allocation per second,
 * computed between consecutive GC events. One series coloured with the
 * {@link GCCategory#ALLOCATION_RATE} palette entry.
 */
@Collates(AllocationRateAggregator.class)
public final class AllocationRateAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "allocation rate (MB/s)", "MB/s");

    // TODO: expose for user customization once threshold tuning lands
    private static double NEGATIVE_SKIPPED_WARNING_THRESHOLD = 5;

    private int negativeSkipped = 0;

    /** Required by the GCSee module SPI. */
    public AllocationRateAggregation() {}

    public void recordRate(double tSec, double mbPerSecond) {
        data.add(GCCategory.ALLOCATION_RATE, tSec, mbPerSecond);
    }

    /** Aggregator tells us it dropped a sample whose delta was negative. */
    public void noteNegativeDrop() {
        negativeSkipped++;
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
        // A few dropped points is normal (full-GCs, concurrent phases between
        // samples). Only warn when more than a handful were skipped.
        return negativeSkipped > NEGATIVE_SKIPPED_WARNING_THRESHOLD;
    }

    @Override
    public String getWarningMessage() {
        return hasWarning()
                ? negativeSkipped + " negative allocation deltas were skipped "
                  + "(likely caused by concurrent cycles or full collections between samples)."
                : null;
    }
}
