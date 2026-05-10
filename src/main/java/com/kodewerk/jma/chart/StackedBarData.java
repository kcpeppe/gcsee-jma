package com.kodewerk.jma.chart;

import java.util.ArrayList;
import java.util.List;

/**
 * Stacked-bar time-series data holder. Pairs with {@link ScatterData} /
 * {@link SummaryData} as the third presentation-layer shape.
 * <p>
 * Each sample is an {@code (x, double[] values)} tuple where the values are
 * laid out in the same order as {@link #getSeries()} — so at time {@code x}
 * series[i] contributes {@code values[i]} to the stack. The Chart.js
 * frontend turns this into a stacked bar chart by pivoting to one dataset
 * per series with the shared x as bar label.
 * <p>
 * We use a dense layout (parallel arrays across series) rather than the
 * ScatterData per-series sparse layout because every event contributes to
 * every series for this chart type — all four G1 phase durations are
 * defined for every G1 pause, even when the phase is zero.
 */
public final class StackedBarData {

    public record Series(String key, String label, String color) {}

    public record Sample(double t, double[] values) {}

    private final String xAxisLabel;
    private final String yAxisLabel;
    private final String yUnit;
    private final List<Series> series;
    private final List<Sample> samples = new ArrayList<>();

    public StackedBarData(String xAxisLabel, String yAxisLabel, String yUnit,
                          List<Series> series) {
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.yUnit = yUnit;
        this.series = List.copyOf(series);
    }

    /** Add one bar (one event's worth of stacked values). */
    public void add(double t, double... values) {
        if (values.length != series.size()) {
            throw new IllegalArgumentException(
                    "value count (" + values.length + ") must match series count ("
                            + series.size() + ")");
        }
        samples.add(new Sample(t, values.clone()));
    }

    public String getXAxisLabel() { return xAxisLabel; }
    public String getYAxisLabel() { return yAxisLabel; }
    public String getYUnit()      { return yUnit; }
    public List<Series> getSeries() { return series; }
    public List<Sample> getSamples() { return samples; }

    public boolean isEmpty() {
        return samples.isEmpty();
    }
}
