package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.StackedBarData;

import java.util.ArrayList;
import java.util.List;

/**
 * G1 per-pause region counts <em>after</em> the collection. Same shape as
 * {@link G1RegionsBeforeAggregation} but reading the {@code after} slot of
 * each {@link G1RegionHarvest.Snapshot} — useful side-by-side with the
 * before view to see how many regions of each type the collection
 * reclaimed (eden typically goes to zero, old / survivor shift, archive
 * stays put).
 */
@Collates(G1RegionsAfterAggregator.class)
public final class G1RegionsAfterAggregation extends JmaAggregation {

    private final List<G1RegionHarvest.Snapshot> rows = new ArrayList<>();
    private long eventsWithoutRegionData = 0;

    /** Required by the GCSee module SPI. */
    public G1RegionsAfterAggregation() {}

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
            out.add(snap.tSec(), G1RegionPalette.valuesOf(snap.after()));
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
