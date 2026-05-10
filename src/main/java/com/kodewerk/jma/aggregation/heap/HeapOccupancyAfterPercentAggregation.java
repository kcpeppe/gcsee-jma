package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Per-pause heap occupancy <em>after</em> each collection, expressed
 * as a percentage of the heap size at that moment. The mirror of
 * {@link HeapOccupancyBeforePercentAggregation} — together they
 * sandwich the collection so a "before %" / "after %" pair reads the
 * reclamation work directly without the operator having to subtract.
 * <p>
 * No capacity line — every point is at or below 100 % by definition.
 */
@Collates(HeapOccupancyAfterPercentAggregator.class)
public final class HeapOccupancyAfterPercentAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "% heap occupancy", "%");

    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public HeapOccupancyAfterPercentAggregation() {}

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
