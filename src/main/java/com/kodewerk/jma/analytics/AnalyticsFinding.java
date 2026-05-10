package com.kodewerk.jma.analytics;

/**
 * One result from an analytic. A finding is the outcome of a single
 * check — "did the log show System.gc() calls?", "is the calculated
 * tenuring threshold churning?" — and carries enough context for the
 * UI to render a self-contained row.
 *
 * @param group   feature area the finding belongs to (e.g. "GC cause",
 *                "Heap usage"). The frontend groups findings by this
 *                so all GC-cause findings render together.
 * @param title   short name of the analytic (e.g. "System.gc() calls").
 *                Stays the same across runs even when severity / message
 *                differ.
 * @param impact  severity tier; see {@link Impact}.
 * @param message one-line summary of what the analytic found. Always
 *                emitted, even when {@code impact == OK}.
 * @param details optional longer-form explanation: why the finding
 *                matters, likely sources of the symptom, JVM flags
 *                that might address it. Rendered as a collapsible
 *                block under the row. Never null — pass an empty
 *                string when there's nothing to elaborate on.
 */
public record AnalyticsFinding(
        String group,
        String title,
        Impact impact,
        String message,
        String details) {
}
