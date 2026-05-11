package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Tenured-generation occupancy captured <em>before</em> each
 * generational collection. The mirror of {@link TenuredOccupancyAggregation};
 * read together the two views show what was in tenured entering and
 * leaving each collection so old-gen growth, premature promotion
 * effects, and full-GC reclamation are easy to see at a glance.
 */
@Collates(TenuredOccupancyBeforeAggregator.class)
public final class TenuredOccupancyBeforeAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "tenured (MB)", "MB");

    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public TenuredOccupancyBeforeAggregation() {}

    public void recordTenuredCapacity(double tSec, double mb) {
        data.add(GCCategory.HEAP_CAPACITY, tSec, mb);
    }

    public void recordTenuredOccupancy(GCCategory category, double tSec, double mb) {
        data.add(category, tSec, mb);
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

    public ScatterData getData() { return data; }

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
                ? "One or more generational GC events did not carry a valid tenured summary and were skipped."
                : null;
    }
}
