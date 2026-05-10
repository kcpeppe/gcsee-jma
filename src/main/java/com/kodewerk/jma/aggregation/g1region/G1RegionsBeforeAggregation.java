package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.StackedBarData;

import java.util.ArrayList;
import java.util.List;

/**
 * G1 per-pause region counts <em>before</em> the collection — one stacked
 * bar per pause event, segments coloured by region type (eden green,
 * survivor yellow, old blue, humongous red, archive grey).
 * <p>
 * Bar height equals the total live region count entering the pause, so the
 * top of the stack reads as "size of the heap in regions". Pauses without
 * region detail are not plotted; their count surfaces via
 * {@link #hasWarning()} / {@link #getWarningMessage()}.
 */
@Collates(G1RegionsBeforeAggregator.class)
public final class G1RegionsBeforeAggregation extends JmaAggregation {

    private final List<G1RegionHarvest.Snapshot> rows = new ArrayList<>();
    private long eventsWithoutRegionData = 0;

    /** Required by the GCSee module SPI. */
    public G1RegionsBeforeAggregation() {}

    void recordSnapshot(G1RegionHarvest.Snapshot snap) {
        rows.add(snap);
    }

    void noteMissingRegionData() {
        eventsWithoutRegionData++;
    }

    public StackedBarData getData() {
        StackedBarData out = new StackedBarData(
                "time (s)", "regions", "regions", G1RegionPalette.seriesList());
        for (G1RegionHarvest.Snapshot snap : rows) {
            out.add(snap.tSec(), G1RegionPalette.valuesOf(snap.before()));
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return eventsWithoutRegionData > 0;
    }

    @Override
    public String getWarningMessage() {
        if (eventsWithoutRegionData == 0) return null;
        return eventsWithoutRegionData + " G1 pause event(s) did not carry region "
                + "detail and were skipped.";
    }
}
