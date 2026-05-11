package com.kodewerk.jma.chart;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Categorical stacked-bar data holder — sibling of {@link StackedBarData}
 * but with string-labelled x slots instead of a linear seconds axis.
 * <p>
 * Used when each bar represents a discrete bucket (e.g. a GC type) rather
 * than a point in time. The category labels are reported in
 * {@link #getCategories()} in the order they should be drawn on the
 * x-axis; each {@link Bar} carries the parallel array of stacked
 * {@code values} laid out in the same order as {@link #getSeries()}, and
 * the y-axis is just a count.
 * <p>
 * The dense layout (every bar contributes a value for every series) keeps
 * the wire shape regular and lets the Chart.js client treat it as one
 * dataset per series with a single shared {@code labels} array — matching
 * the JFreeChart {@code DefaultCategoryDataset} model Censum uses.
 */
public final class CategoryBarData {

    public record Series(String key, String label, String color) {}

    /** One x-axis slot. {@code values[i]} is series[i]'s stacked contribution. */
    public record Bar(String category, double[] values) {}

    private final String xAxisLabel;
    private final String yAxisLabel;
    private final String yUnit;
    private final List<Series> series;
    private final List<Bar> bars = new ArrayList<>();

    public CategoryBarData(String xAxisLabel, String yAxisLabel, String yUnit,
                           List<Series> series) {
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.yUnit = yUnit;
        this.series = List.copyOf(series);
    }

    /** Add one bar (one category's worth of stacked values). */
    public void add(String category, double... values) {
        if (values.length != series.size()) {
            throw new IllegalArgumentException(
                    "value count (" + values.length + ") must match series count ("
                            + series.size() + ")");
        }
        bars.add(new Bar(category, values.clone()));
    }

    // See note in ScatterData on the Jackson naming pin.
    @JsonProperty("xAxisLabel") public String getXAxisLabel() { return xAxisLabel; }
    @JsonProperty("yAxisLabel") public String getYAxisLabel() { return yAxisLabel; }
    @JsonProperty("yUnit")      public String getYUnit()      { return yUnit; }
    public List<Series> getSeries() { return series; }
    public List<Bar> getBars()      { return bars; }

    /** Convenience: just the category labels in draw order. */
    public List<String> getCategories() {
        List<String> out = new ArrayList<>(bars.size());
        for (Bar b : bars) out.add(b.category());
        return out;
    }

    public boolean isEmpty() {
        return bars.isEmpty();
    }
}
