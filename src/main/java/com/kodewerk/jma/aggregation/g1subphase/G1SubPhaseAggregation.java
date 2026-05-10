package com.kodewerk.jma.aggregation.g1subphase;

import com.kodewerk.jma.aggregation.JmaAggregation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base for the eight G1 sub-phase aggregations (four phases ×
 * {absolute, percent}). Holds the per-event sub-phase data captured by
 * the matching aggregator; concrete subclasses choose the emit mode by
 * implementing {@code getData()} on top of
 * {@link G1SubPhaseSeriesBuilder}.
 * <p>
 * Carrying the raw data here keeps the {@link G1SubPhaseSeriesBuilder}
 * stateless and lets the absolute and percent variants for the same
 * phase share identical inputs — they're separately registered
 * aggregations because GCSee's SPI is one aggregator instance per
 * registered aggregation, but they observe the same event stream.
 */
public abstract class G1SubPhaseAggregation extends JmaAggregation {

    /**
     * One pause's worth of sub-phase data — the timestamp, the
     * collection type label (Young / Mixed / InitialMark), and the
     * per-sub-phase durations in seconds.
     */
    public record EventRow(double tSec,
                           String collectionType,
                           Map<String, Double> subPhaseSeconds) {}

    private final List<EventRow> rows = new ArrayList<>();
    private long eventsWithoutSubPhaseData = 0;

    protected G1SubPhaseAggregation() {}

    /** Aggregator-side mutation: record one event's sub-phase map. */
    public final void recordEvent(double tSec,
                                  String collectionType,
                                  Map<String, Double> subPhaseSeconds) {
        rows.add(new EventRow(tSec, collectionType,
                              new LinkedHashMap<>(subPhaseSeconds)));
    }

    /** Aggregator-side: count an event whose sub-phase map was empty. */
    public final void noteMissingSubPhaseData() {
        eventsWithoutSubPhaseData++;
    }

    /** Read-side: subclasses use this to feed the series builder. */
    protected final List<EventRow> rows() { return rows; }

    @Override
    public final boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public final boolean hasWarning() {
        return eventsWithoutSubPhaseData > 0;
    }

    @Override
    public final String getWarningMessage() {
        if (eventsWithoutSubPhaseData == 0) return null;
        return eventsWithoutSubPhaseData + " G1 pause event(s) did not carry "
                + "sub-phase detail and were skipped.";
    }
}
