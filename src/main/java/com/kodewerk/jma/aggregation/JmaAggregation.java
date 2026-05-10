package com.kodewerk.jma.aggregation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kodewerk.gcsee.aggregator.Aggregation;
import com.kodewerk.gcsee.time.DateTimeStamp;

/**
 * Base class for every aggregation exposed by gcsee-jma views.
 * <p>
 * Extends GCSee's native {@link Aggregation} so the JVMEventChannel still
 * dispatches events to our aggregators via the {@code @Aggregates} /
 * {@code @Collates} annotation pair, and adds the small amount of metadata
 * the UI layer needs on top:
 * <ul>
 *   <li>{@link #getWarningMessage()} — text the UI shows next to a
 *       {@code WARNING} view (e.g. "concurrent-mode failure observed").</li>
 *   <li>{@link #isError()} / {@link #getErrorMessage()} — a domain-level
 *       signal that this log cannot support the analysis (e.g. log too
 *       short to compute this percentile). Not for technical/IO errors —
 *       those never reach this layer.</li>
 * </ul>
 * Subclasses still implement the two {@code abstract} methods inherited from
 * {@link Aggregation} ({@code hasWarning()} and {@code isEmpty()}); the
 * default implementations here give sensible "no message / no error"
 * behaviour so subclasses only override what they actually use.
 * <p>
 * Required by the GCSee module SPI: subclasses must declare a public, no-arg
 * constructor.
 * <p>
 * The {@link JsonIgnoreProperties} annotation hides inherited bean
 * properties from {@link Aggregation} that would otherwise leak into the
 * REST response. They are just noise the UI should not see —
 * {@code collates} is a {@code Class} reference and the two timestamps are
 * nested objects with a dozen fields each. We expose the runtime as a
 * single decimal-seconds value through {@link #getEstimatedRuntimeSeconds()}.
 */
@JsonIgnoreProperties({
        "estimatedStartTime",
        "timeOfTerminationEvent",
        "collates"
})
public abstract class JmaAggregation extends Aggregation {

    protected JmaAggregation() {}

    /**
     * Human-readable message to show when {@link #hasWarning()} is true.
     * Default: {@code null} (no message beyond the warning flag).
     */
    public String getWarningMessage() {
        return null;
    }

    /**
     * Domain-level error flag: the log does not support this analysis.
     * Default: {@code false}. Aggregations that have a minimum-data
     * requirement (e.g. a percentile view) override this.
     */
    public boolean isError() {
        return false;
    }

    /**
     * Human-readable message to show when {@link #isError()} is true.
     * Default: {@code null}.
     */
    public String getErrorMessage() {
        return null;
    }

    /**
     * Diagnostic: the termination-event timestamp GCSee recorded for this
     * aggregation, in seconds. Returns {@code null} when no termination
     * record was actually seen.
     * <p>
     * Note that GCSee initialises {@code timeOfTermination} to
     * {@code DateTimeStamp.baseDate()} (EPOC at 0.0s) at construction, so a
     * raw {@code timeOfTerminationEvent()} is never {@code null}. We treat
     * a {@code 0.0} timestamp as "not set" — a real JVMTermination from a
     * non-empty run is always after the first event, never at 0.
     */
    public Double getLogLastEventSeconds() {
        DateTimeStamp t = timeOfTerminationEvent();
        if (t == null || !t.hasTimeStamp()) return null;
        double seconds = t.toSeconds();
        return seconds > 0.0 ? seconds : null;
    }

    /**
     * GCSee's runtime estimate, computed by {@code Diary} during the diarizer
     * pre-pass and forwarded onto every {@code Aggregation} via the
     * {@code JVMTermination} event. Equals
     * {@code timeOfTerminationEvent − estimatedStartTime}, where the start
     * time accounts for log truncation when {@code ZERO_GCID} is FALSE or
     * when the heuristic concludes the first event is too far from JVM
     * start to be the first one.
     */
    public double getEstimatedRuntimeSeconds() {
        return estimatedRuntime();
    }
}
