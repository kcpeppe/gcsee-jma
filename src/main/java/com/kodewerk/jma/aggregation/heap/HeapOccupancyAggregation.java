package com.kodewerk.jma.aggregation.heap;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Heap-occupancy time series: for every GC event we plot two points — the
 * committed heap size (the capacity line) and the occupancy after the
 * collection, coloured by the event's {@link GCCategory}.
 * <p>
 * The aggregator feeds us already-normalised values (MB, log-relative
 * seconds) so this class is just a thin holder over {@link ScatterData}.
 * All colouring and legend wiring lives in the chart layer.
 */
@Collates(HeapOccupancyAggregator.class)
public final class HeapOccupancyAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "heap (MB)", "MB");

    // Diagnostic counters — tells us whether events past the visible
    // x-axis maximum are arriving at all, and if so, whether they were
    // dropped by the heap.isValid() filter.
    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public HeapOccupancyAggregation() {}

    /** Adds one heap-capacity point (the heap-size line). */
    public void recordHeapCapacity(double tSec, double mb) {
        data.add(GCCategory.HEAP_CAPACITY, tSec, mb);
    }

    /** Adds one occupancy-after-collection point, coloured by category. */
    public void recordHeapOccupancy(GCCategory category, double tSec, double mb) {
        data.add(category, tSec, mb);
    }

    /** Called by the aggregator on every dispatched event, before filtering. */
    public void noteEventSeen(double tSec) {
        eventsSeen++;
        if (Double.isNaN(firstEventTime)) firstEventTime = tSec;
        lastEventTime = tSec;
    }

    /** Called by the aggregator when an event arrived without a usable heap summary. */
    public void noteInvalidSample(double tSec) {
        invalidSampleCount++;
        lastInvalidTime = tSec;
    }

    /** Backward-compat shim — ZGC path calls this without a timestamp. */
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
