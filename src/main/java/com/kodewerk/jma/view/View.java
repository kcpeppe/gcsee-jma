package com.kodewerk.jma.view;

/**
 * UI-layer wrapper around an analysis result. Tells the frontend whether a
 * chart or page has valid data, nothing to show, something to warn about, or
 * cannot be rendered because the log does not support the analysis.
 * <p>
 * Status is a <em>domain</em> signal — "does this log support this analysis?"
 * — not a technical signal. Parse exceptions, I/O errors, and other
 * infrastructure failures are handled at the session layer and do not appear
 * here.
 * <p>
 * Semantics:
 * <ul>
 *   <li>{@code OK}      — analysis is valid and aggregation is normal.</li>
 *   <li>{@code WARNING} — analysis is valid but there is something
 *                         domain-relevant to surface (e.g. sparse data,
 *                         truncated log, only one collector family seen).
 *                         Aggregation present.</li>
 *   <li>{@code EMPTY}   — the log has no events of the kind this view
 *                         needs. Not a failure. No aggregation.</li>
 *   <li>{@code ERROR}   — the log does not support this analysis (e.g.
 *                         runtime too short to compute a percentile,
 *                         too few samples). No aggregation.</li>
 * </ul>
 *
 * @param <A> the Aggregation type wrapped by this view
 */
public final class View<A> {

    public enum Status { OK, EMPTY, WARNING, ERROR }

    private final String id;
    private final String title;
    private final Status status;
    private final A      aggregation; // null for EMPTY and ERROR
    private final String message;     // null unless EMPTY, WARNING, or ERROR

    private View(String id, String title, Status status, A aggregation, String message) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.aggregation = aggregation;
        this.message = message;
    }

    public static <A> View<A> ok(String id, String title, A aggregation) {
        return new View<>(id, title, Status.OK, aggregation, null);
    }

    public static <A> View<A> warning(String id, String title, A aggregation, String message) {
        return new View<>(id, title, Status.WARNING, aggregation, message);
    }

    public static <A> View<A> empty(String id, String title, String message) {
        return new View<>(id, title, Status.EMPTY, null, message);
    }

    public static <A> View<A> error(String id, String title, String message) {
        return new View<>(id, title, Status.ERROR, null, message);
    }

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public Status getStatus()      { return status; }
    public A      getAggregation() { return aggregation; }
    public String getMessage()     { return message; }

    public boolean hasData() {
        return aggregation != null;
    }
}
