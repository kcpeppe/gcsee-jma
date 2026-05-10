package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Heap-occupancy time series captured <em>before</em> each collection,
 * mirroring {@link HeapOccupancyAggregation} (which captures occupancy
 * <em>after</em> collection). Two points per event — the heap-size line
 * (capacity at that moment) and the occupancy reading entering the pause,
 * coloured by the event's {@link GCCategory}.
 * <p>
 * The aggregator feeds already-normalised values (MB, log-relative
 * seconds) so this class is a thin holder over {@link ScatterData}; all
 * colouring and legend wiring lives in the chart layer and uses the
 * same palette as the after-collection chart so the two views read as
 * a matched pair.
 */
@Collates(HeapOccupancyBeforeAggregator.class)
public final class HeapOccupancyBeforeAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "heap (MB)", "MB");

    // Diagnostic counters — same shape as the after view so issues
    // surface symmetrically across the pair.
    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public HeapOccupancyBeforeAggregation() {}

    /** Adds one heap-capacity point (the heap-size line). */
    public void recordHeapCapacity(double tSec, double mb) {
        data.add(GCCategory.HEAP_CAPACITY, tSec, mb);
    }

    /** Adds one before-collection occupancy point, coloured by category. */
    public void recordHeapOccupancy(GCCategory category, double tSec, double mb) {
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

    public void noteInvalidSample() {
        invalidSampleCount++;
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
