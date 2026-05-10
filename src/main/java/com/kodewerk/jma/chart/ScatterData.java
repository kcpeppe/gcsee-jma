package com.kodewerk.jma.chart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a time-series scatter plot as a set of category-tagged point series.
 * One chart component on the frontend renders every instance of this type.
 * <p>
 * Points are grouped by {@link GCCategory} so that legend entries, colouring,
 * and per-series show/hide behaviour is uniform across every view in the UI.
 * <p>
 * Not to be confused with GCSee's {@code Aggregation} — {@code ScatterData}
 * is purely a presentation-layer data holder. Our Aggregations hold one of
 * these (via composition) alongside any analysis metadata they require.
 */
public final class ScatterData {

    public record Point(double t, double y) {}

    public record Series(GCCategory category,
                         String      label,
                         String      color,
                         List<Point> points) {}

    private final String xAxisLabel;
    private final String yAxisLabel;
    private final String yUnit;
    private final Map<GCCategory, List<Point>> byCategory = new EnumMap<>(GCCategory.class);

    public ScatterData(String xAxisLabel, String yAxisLabel, String yUnit) {
        this.xAxisLabel = xAxisLabel;
        this.yAxisLabel = yAxisLabel;
        this.yUnit = yUnit;
    }

    /** Append a single point tagged with its category. */
    public void add(GCCategory category, double t, double y) {
        byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(new Point(t, y));
    }

    public String getXAxisLabel() { return xAxisLabel; }
    public String getYAxisLabel() { return yAxisLabel; }
    public String getYUnit()      { return yUnit; }

    public int getPointCount() {
        int n = 0;
        for (List<Point> list : byCategory.values()) n += list.size();
        return n;
    }

    public boolean isEmpty() {
        return getPointCount() == 0;
    }

    /**
     * Materializes the data as a list of series (one per category) with
     * colour already resolved from {@link ColorPalette}.
     */
    public List<Series> getSeries() {
        List<Series> out = new ArrayList<>(byCategory.size());
        for (Map.Entry<GCCategory, List<Point>> e : byCategory.entrySet()) {
            GCCategory cat = e.getKey();
            out.add(new Series(cat, cat.name(), ColorPalette.of(cat), e.getValue()));
        }
        return out;
    }
}
