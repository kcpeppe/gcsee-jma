package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Per-pause heap occupancy entering each collection, expressed as a
 * percentage of the heap size at that moment. Pairs with
 * {@link HeapOccupancyAfterPercentAggregation} to give a "before /
 * after" view that's easy to read across changing -Xms / -Xmx
 * configurations: even when the absolute size shifts, the percentage
 * series stays comparable.
 * <p>
 * No capacity line on this view — the maximum is fixed at 100 % by
 * definition. The Y-axis is clamped to that ceiling on the frontend
 * so a tiny rounding-driven over-100 reading doesn't squash the chart.
 */
@Collates(HeapOccupancyBeforePercentAggregator.class)
public final class HeapOccupancyBeforePercentAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "% heap occupancy", "%");

    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public HeapOccupancyBeforePercentAggregation() {}

    /** Adds one before-collection percentage point, coloured by category. */
    public void recordOccupancyPercent(GCCategory category, double tSec, double pct) {
        data.add(category, tSec, pct);
    }

    public void noteEventSeen(double tSec) {
        eventsSeen++;
        if (Double.isNaN(firstEventTime)) firstEventTime = tSec;
        lastEventTime = tSec;
    }

    public void noteInvalidSample(double tSec) {
        invalidSampleCount++;
        lastInvalidTime = tSec;
    }

    public ScatterData getData() {
        return data;
    }

    public int    getEventsSeen()        { return eventsSeen; }
    public double getFirstEventTime()    { return firstEventTime; }
    public double getLastEventTime()     { return lastEventTime; }
    public int    getInvalidSampleCount(){ return invalidSampleCount; }
    public double getLastInvalidTime()   { return lastInvalidTime; }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return invalidSampleCount > 0;
    }

    @Override
    public String getWarningMessage() {
        return invalidSampleCount > 0
                ? "One or more GC events did not carry a valid heap summary and were skipped."
                : null;
    }
}
