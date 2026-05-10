package com.kodewerk.jma.aggregation.reference;

import com.kodewerk.jma.aggregation.JmaAggregation;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base for the two reference-processing scatter views — count
 * and time. Captures one row per pause that carried a
 * {@link com.kodewerk.gcsee.event.ReferenceGCSummary}; the concrete
 * subclasses pick which fields to plot. Sharing the row store keeps
 * the absolute and percent-of-pause variants in lock-step on the same
 * input data.
 * <p>
 * The four reference categories tracked are Soft, Weak, Final, and
 * Phantom — JNI-weak is omitted because most operators don't tune
 * around it and including it adds another series that crowds the
 * legend without changing the diagnosis.
 */
public abstract class ReferenceProcessingAggregation extends JmaAggregation {

    /** Per-event reference-processing summary in plot-ready units. */
    public record EventRow(
            double tSec,
            int    softCount,    double softMs,
            int    weakCount,    double weakMs,
            int    finalCount,   double finalMs,
            int    phantomCount, double phantomMs) {}

    private final List<EventRow> rows = new ArrayList<>();

    protected ReferenceProcessingAggregation() {}

    public final void recordEvent(EventRow row) {
        rows.add(row);
    }

    protected final List<EventRow> rows() { return rows; }

    @Override
    public final boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
