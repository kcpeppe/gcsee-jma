package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * "Tenuring over time" chart — bytes surviving at each age, one series
 * per age, plotted against time. Mirrors Censum's chart of the same name.
 * <p>
 * Series cardinality is data-dependent (up to
 * {@code maxTenuringThreshold} series, typically 15) so this view uses
 * {@link XYSeriesData} rather than {@link com.kodewerk.jma.chart.ScatterData}
 * — the latter is keyed by {@link com.kodewerk.jma.chart.GCCategory},
 * which has no slot for "bytes at age N". Colours are drawn from a
 * fixed 16-entry palette in age order so two different logs comparing
 * the same age are coloured the same.
 */
@Collates(TenuringAggregator.class)
public final class TenuringByAgeAggregation extends JmaAggregation
        implements TenuringSink {

    /**
     * 16-colour palette indexed by age (age 1 → PALETTE[0]). Picked for
     * pairwise contrast — adjacent ages do not get visually similar
     * colours, so one age's points stay distinguishable when several
     * series cluster together. Exceeds the JVM's
     * {@code THEORETICAL_MAX_TENURING_THRESHOLD} so the palette never
     * wraps on a real log.
     */
    private static final String[] PALETTE = {
            "#e53935", // age 1  red
            "#1e88e5", // age 2  blue
            "#43a047", // age 3  green
            "#fb8c00", // age 4  orange
            "#8e24aa", // age 5  violet
            "#00acc1", // age 6  teal
            "#fdd835", // age 7  yellow
            "#6d4c41", // age 8  brown
            "#3949ab", // age 9  indigo
            "#7cb342", // age 10 lime
            "#d81b60", // age 11 pink
            "#039be5", // age 12 light blue
            "#827717", // age 13 olive
            "#5e35b1", // age 14 deep purple
            "#00897b", // age 15 dark teal
            "#546e7a"  // age 16 blue-grey (reserve slot)
    };
    private static final double BYTES_TO_KB = 1.0 / 1024.0;

    /** Per-age point list, sorted by age so the legend reads 1..N. */
    private final TreeMap<Integer, List<XYSeriesData.Point>> perAge = new TreeMap<>();

    /** Required by the GCSee module SPI. */
    public TenuringByAgeAggregation() {}

    @Override
    public void record(double tSec, SurvivorRecord r) {
        long[] bytesByAge = r.getBytesAtEachAge();
        if (bytesByAge == null) return;
        for (int age = 1; age < bytesByAge.length; age++) {
            long bytes = bytesByAge[age];
            // Skip empty slots — most logs only populate a small prefix
            // of the array, and storing zeros bloats the series for ages
            // that never see traffic.
            if (bytes == 0) continue;
            perAge.computeIfAbsent(age, k -> new ArrayList<>())
                  .add(new XYSeriesData.Point(tSec, bytes * BYTES_TO_KB));
        }
    }

    public XYSeriesData getData() {
        List<XYSeriesData.Series> series = new ArrayList<>(perAge.size());
        for (var entry : perAge.entrySet()) {
            int age = entry.getKey();
            String color = PALETTE[(age - 1) % PALETTE.length];
            series.add(new XYSeriesData.Series(
                    "age-" + age,
                    "Bytes @ age " + age,
                    color,
                    entry.getValue()));
        }
        return new XYSeriesData(
                "time (s)", "Surviving bytes (KBytes)", "KB", series);
    }

    @Override
    public boolean isEmpty() {
        for (List<XYSeriesData.Point> pts : perAge.values()) {
            if (!pts.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
