package com.kodewerk.jma.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation-layer container for analytics. Holds a flat list of
 * {@link AnalyticsFinding} plus a derived view that buckets them by
 * {@link AnalyticsFinding#group()} so the frontend can render one
 * section per feature area without having to do the grouping itself.
 * <p>
 * Sits next to {@link com.kodewerk.jma.chart.SummaryData},
 * {@link com.kodewerk.jma.chart.ScatterData} and friends as another
 * presentation shape. Group iteration order matches the order findings
 * were added — the analytic-running code controls section order by
 * calling its analytics in the desired order.
 */
public final class AnalyticsData {

    private final List<AnalyticsFinding> findings;

    public AnalyticsData(List<AnalyticsFinding> findings) {
        this.findings = List.copyOf(findings);
    }

    public List<AnalyticsFinding> getFindings() {
        return findings;
    }

    /**
     * Findings bucketed by group. Insertion-ordered so the frontend
     * renders sections in the order the analytics ran.
     */
    public Map<String, List<AnalyticsFinding>> getByGroup() {
        Map<String, List<AnalyticsFinding>> out = new LinkedHashMap<>();
        for (AnalyticsFinding f : findings) {
            out.computeIfAbsent(f.group(), k -> new ArrayList<>()).add(f);
        }
        return out;
    }

    public boolean isEmpty() {
        return findings.isEmpty();
    }
}
