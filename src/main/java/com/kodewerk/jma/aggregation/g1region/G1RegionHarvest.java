package com.kodewerk.jma.aggregation.g1region;

import com.kodewerk.gcsee.event.RegionSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;

/**
 * Shared extractor for G1 region counts. Pulls the per-region-type
 * {@link RegionSummary} pairs ({@code before}, {@code after}) off a
 * {@link G1GCPauseEvent} into the fixed-shape struct the four region
 * aggregations consume.
 * <p>
 * GCSee populates {@code addRegionSummary(eden, survivor, old, humongous,
 * archive)} on every G1 pause event that carries region detail. Pause
 * subtypes that have no region data attached (some G1Remark / G1Cleanup
 * variants, G1FullGC without region accounting) come back as
 * {@link RegionSummary#NULL_REGION} for all five slots; we treat that as
 * "no usable sample" and the calling aggregator skips the event with a
 * {@code noteMissingRegionData()} count.
 */
final class G1RegionHarvest {

    /** Fixed series order — kept in one place so all four views agree. */
    static final String EDEN      = "eden";
    static final String SURVIVOR  = "survivor";
    static final String OLD       = "old";
    static final String HUMONGOUS = "humongous";
    static final String ARCHIVE   = "archive";

    /** Five-slot record so the aggregation never has to reach into the GCSee API. */
    record RegionCounts(int eden, int survivor, int old, int humongous, int archive) {
        int sum() { return eden + survivor + old + humongous + archive; }
    }

    /** One pause event's worth of (before, after) snapshots. */
    record Snapshot(double tSec, RegionCounts before, RegionCounts after) {}

    private G1RegionHarvest() {}

    /**
     * Extract the (before, after) region counts from a G1 pause event.
     * Returns {@code null} when the event has no region detail attached
     * (every slot null or the whole snapshot summing to zero on both
     * sides — typical of G1FullGC and some concurrent-cycle pauses).
     */
    static Snapshot snapshotOf(G1GCPauseEvent event) {
        RegionCounts before = new RegionCounts(
                beforeOf(event.getEdenRegionSummary()),
                beforeOf(event.getSurvivorRegionSummary()),
                beforeOf(event.getOldRegionSummary()),
                beforeOf(event.getHumongousRegionSummary()),
                beforeOf(event.getArchiveRegionSummary()));
        RegionCounts after = new RegionCounts(
                afterOf(event.getEdenRegionSummary()),
                afterOf(event.getSurvivorRegionSummary()),
                afterOf(event.getOldRegionSummary()),
                afterOf(event.getHumongousRegionSummary()),
                afterOf(event.getArchiveRegionSummary()));
        if (before.sum() == 0 && after.sum() == 0) return null;
        return new Snapshot(event.getDateTimeStamp().toSeconds(), before, after);
    }

    /**
     * Defensive accessor: a missing or sentinel summary contributes 0 rather
     * than a negative count or NPE. GCSee uses a static {@code NULL_REGION}
     * sentinel for unset slots; both {@code null} and the sentinel report 0
     * via {@code getBefore()} so we just normalise negatives.
     */
    private static int beforeOf(RegionSummary summary) {
        if (summary == null) return 0;
        int v = summary.getBefore();
        return v > 0 ? v : 0;
    }

    private static int afterOf(RegionSummary summary) {
        if (summary == null) return 0;
        int v = summary.getAfter();
        return v > 0 ? v : 0;
    }
}
