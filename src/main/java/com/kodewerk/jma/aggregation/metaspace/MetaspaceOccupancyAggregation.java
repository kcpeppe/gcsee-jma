package com.kodewerk.jma.aggregation.metaspace;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * Metaspace-occupancy time series — the metaspace counterpart of
 * {@link com.kodewerk.jma.aggregation.heap.HeapOccupancyAggregation}.
 * For every GC event we plot two points: the committed metaspace size
 * (capacity line) and the metaspace occupancy after the collection,
 * coloured by the event's {@link GCCategory}.
 * <p>
 * Values arrive already normalised to MB / log-relative seconds by the
 * aggregator, so this class is just a thin holder over {@link ScatterData}.
 * <p>
 * Empty when no GC event in the log carried a metaspace summary —
 * including JFR captures that omit metaspace records and any future
 * collector that doesn't emit one.
 */
@Collates(MetaspaceOccupancyAggregator.class)
public final class MetaspaceOccupancyAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "metaspace (MB)", "MB");

    private boolean sawInvalidSample = false;

    /** Required by the GCSee module SPI. */
    public MetaspaceOccupancyAggregation() {}

    /** Add one metaspace-capacity point (the committed-size line). */
    public void recordMetaspaceCapacity(double tSec, double mb) {
        data.add(GCCategory.METASPACE_CAPACITY, tSec, mb);
    }

    /** Add one occupancy-after-collection point, coloured by category. */
    public void recordMetaspaceOccupancy(GCCategory category, double tSec, double mb) {
        data.add(category, tSec, mb);
    }

    /** Called by the aggregator when an event arrived without a usable metaspace summary. */
    public void noteInvalidSample() {
        sawInvalidSample = true;
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
        // Only flag a warning when there *was* metaspace data — if every
        // event lacked a summary the view is simply EMPTY, which is not
        // a problem worth shouting about.
        return sawInvalidSample && !data.isEmpty();
    }

    @Override
    public String getWarningMessage() {
        return hasWarning()
                ? "One or more GC events did not carry a metaspace summary and were skipped."
                : null;
    }
}
