package com.kodewerk.jma.aggregation.safepoint;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.CategoryBarData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Safepoint cause histogram — one bar per VM operation, height is the
 * count of occurrences. Sorted descending so the operations causing
 * the most safepoints read first; ties broken alphabetically for
 * stable output.
 * <p>
 * Single-series CategoryBarData rendered by the existing
 * {@code renderCategoryStackedBar} on the frontend — the GC-cause bar
 * uses the same shape so both reads identically.
 */
@Collates(SafepointCauseAggregator.class)
public final class SafepointCauseAggregation extends JmaAggregation {

    private static final String SERIES_COLOR = "#1e3a8a";

    private final Map<String, Long> causeCounts = new TreeMap<>();

    /** Required by the GCSee module SPI. */
    public SafepointCauseAggregation() {}

    public void recordCause(String cause) {
        if (cause == null) return;
        causeCounts.merge(cause, 1L, Long::sum);
    }

    public CategoryBarData getData() {
        CategoryBarData out = new CategoryBarData(
                "VM operation", "count", "",
                java.util.List.of(new CategoryBarData.Series("count", "Count", SERIES_COLOR)));

        // Sort descending by count, alphabetic tiebreaker.
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(causeCounts.entrySet());
        sorted.sort(Comparator
                .comparingLong((Map.Entry<String, Long> e) -> -e.getValue())
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, Long> e : sorted) {
            out.add(e.getKey(), (double) e.getValue());
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        return causeCounts.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
