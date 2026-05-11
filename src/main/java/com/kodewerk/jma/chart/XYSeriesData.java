package com.kodewerk.jma.chart;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Generic multi-series x-y scatter data — one or more named series, each
 * carrying its own list of {@code (x, y)} points and a colour. Sits next
 * to {@link ScatterData} (which is keyed by {@link GCCategory}, fine for
 * collector-coloured charts) and {@link StackedBarData} (which uses a
 * dense per-sample layout, fine when every sample contributes to every
 * series). This shape is the right fit when the series are open-ended
 * categorical buckets — for example "bytes at age 1, age 2, …, age 15"
 * over time, where the series count is data-dependent and not enumerated
 * in {@link GCCategory}.
 * <p>
 * Series storage is sparse per-series: a series with no points contributes
 * nothing, and the points within a series can be at arbitrary x values
 * with no requirement that every series share an x-grid.
 */
public final class XYSeriesData {

    public record Point(double x, double y) {}

    /**
     * Series record. {@code pointStyle} is an optional Chart.js marker
     * hint — {@code "circle"}, {@code "triangle"}, {@code "rect"}, etc.
     * — used by the frontend to encode a second categorical dimension
     * on top of colour. Pass {@code null} for the default (circle).
     */
    public record Series(String key, String label, String color,
                         String pointStyle, List<Point> points) {

        /** Convenience constructor for series that don't need a custom marker. */
        public Series(String key, String label, String color, List<Point> points) {
            this(key, label, color, null, points);
        }
    }

    private final String xAxisLabel;
    private final String yAxisLabel;
    private final String yUnit;
    private final List<Series> series;

    public XYSeriesData(String xAxisLabel, String yAxisLabel, String yUnit,
                        List<Series> series) {
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.yUnit = yUnit;
        this.series = List.copyOf(series);
    }

    // See note in ScatterData on the Jackson naming pin.
    @JsonProperty("xAxisLabel") public String getXAxisLabel() { return xAxisLabel; }
    @JsonProperty("yAxisLabel") public String getYAxisLabel() { return yAxisLabel; }
    @JsonProperty("yUnit")      public String getYUnit()      { return yUnit; }
    public List<Series> getSeries() { return series; }

    public boolean isEmpty() {
        for (Series s : series) {
            if (!s.points().isEmpty()) return false;
        }
        return true;
    }
}
