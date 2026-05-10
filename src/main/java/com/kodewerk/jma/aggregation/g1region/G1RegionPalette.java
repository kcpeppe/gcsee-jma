package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.jma.chart.StackedBarData;

import java.util.List;

/**
 * Canonical colour and series order for G1 region charts. Single source of
 * truth so all four region views (before / after, absolute / percent) emit
 * the same series in the same order with the same colours — eden green,
 * survivor yellow, old blue, humongous red, archive grey, in heap-layout
 * order.
 */
final class G1RegionPalette {

    /** Iteration / stack order shared by all four views. */
    static final String[] SERIES_ORDER = {
            G1RegionHarvest.EDEN,
            G1RegionHarvest.SURVIVOR,
            G1RegionHarvest.OLD,
            G1RegionHarvest.HUMONGOUS,
            G1RegionHarvest.ARCHIVE
    };

    private G1RegionPalette() {}

    /** A fresh series list — defensive copy because StackedBarData stores by reference. */
    static List<StackedBarData.Series> seriesList() {
        return List.of(
                new StackedBarData.Series(G1RegionHarvest.EDEN,      "eden",      "#43a047"),
                new StackedBarData.Series(G1RegionHarvest.SURVIVOR,  "survivor",  "#fdd835"),
                new StackedBarData.Series(G1RegionHarvest.OLD,       "old",       "#1e88e5"),
                new StackedBarData.Series(G1RegionHarvest.HUMONGOUS, "humongous", "#e53935"),
                new StackedBarData.Series(G1RegionHarvest.ARCHIVE,   "archive",   "#90a4ae")
        );
    }

    /** Project a five-slot record into a {@code double[]} in {@link #SERIES_ORDER}. */
    static double[] valuesOf(G1RegionHarvest.RegionCounts counts) {
        return new double[] {
                counts.eden(),
                counts.survivor(),
                counts.old(),
                counts.humongous(),
                counts.archive()
        };
    }
}
