package com.kodewerk.jma.view;

import com.kodewerk.jma.aggregation.JmaAggregation;

/**
 * Builds a {@link View} from a {@link JmaAggregation}.
 * <p>
 * The status rules live here so every REST resource and UI endpoint reports
 * the same status for the same aggregation state. Order of precedence:
 * <ol>
 *   <li>{@code isError()}   → {@link View.Status#ERROR}   — the log does
 *       not support this analysis. No aggregation attached.</li>
 *   <li>{@code isEmpty()}   → {@link View.Status#EMPTY}   — analysis ran
 *       but found no events of the kind this view needs. No aggregation
 *       attached.</li>
 *   <li>{@code hasWarning()}→ {@link View.Status#WARNING} — analysis ran
 *       with something domain-relevant to surface. Aggregation attached.</li>
 *   <li>otherwise           → {@link View.Status#OK}.</li>
 * </ol>
 * Error and empty states deliberately drop the aggregation reference so
 * the UI cannot accidentally render partial data for a view that failed
 * its precondition.
 */
public final class Views {

    private Views() {}

    public static <A extends JmaAggregation> View<A> of(String id, String title, A aggregation) {
        if (aggregation == null) {
            return View.error(id, title, "No aggregation produced for this view.");
        }
        if (aggregation.isError()) {
            return View.error(id, title, aggregation.getErrorMessage());
        }
        if (aggregation.isEmpty()) {
            return View.empty(id, title, aggregation.getWarningMessage());
        }
        if (aggregation.hasWarning()) {
            return View.warning(id, title, aggregation, aggregation.getWarningMessage());
        }
        return View.ok(id, title, aggregation);
    }
}
