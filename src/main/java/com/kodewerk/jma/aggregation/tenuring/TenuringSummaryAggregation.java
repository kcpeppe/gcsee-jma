package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.SummaryData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Tenuring distribution summary — the table view that mirrors Censum's
 * "Tenuring Distribution Summary" page. Rolls up every
 * {@link SurvivorRecord} the parser emits into:
 * <ul>
 *   <li>A headline strip — max tenuring threshold, desired survivor
 *       occupancy, premature-promotion count and percentage, total
 *       collections, and the lowest age at which the cumulative
 *       occupancy ever exceeded the desired occupancy (or "Never").</li>
 *   <li>A "Tenuring Breakdown" table — one row per age, columns Average,
 *       Total and Maximum Occupancy in bytes. The "Total" column carries
 *       the cumulative byte count across collections (sum of bytes seen
 *       at this age) so it reads as the cumulative tenuring volume.</li>
 *   <li>A "Tenuring Threshold Distribution" table — one row per
 *       calculated-threshold value, with a count of how many collections
 *       chose that threshold.</li>
 * </ul>
 * Empty-flag is {@code true} when no SurvivorRecord ever arrived (so the
 * UI can show the EMPTY state instead of a zero-padded table).
 */
@Collates(TenuringAggregator.class)
public final class TenuringSummaryAggregation extends JmaAggregation
        implements TenuringSink {

    /** Rolling stats for one age slot. */
    private static final class AgeStats {
        long sumBytes  = 0;
        long countObservations = 0;
        long maxBytes  = 0;
    }

    private long collections          = 0;
    private int  maxTenuringThreshold = 0;
    private long desiredOccupancy     = 0;
    private long prematurePromotions  = 0;
    /** Lowest age where cumulative bytes exceeded the desired occupancy in any record; 0 = never. */
    private int  desiredExceededAtAge = 0;

    // Insertion-ordered by age so the breakdown table reads top-down.
    private final Map<Integer, AgeStats> perAge = new TreeMap<>();
    private final Map<Integer, Long>     thresholdCounts = new TreeMap<>();

    /** Required by the GCSee module SPI. */
    public TenuringSummaryAggregation() {}

    @Override
    public void record(double tSec, SurvivorRecord r) {
        collections++;
        // Use the largest threshold seen — typical logs are constant, but a
        // log spanning a -XX:MaxTenuringThreshold change should still report
        // the most permissive value rather than the first.
        if (r.getMaxTenuringThreshold() > maxTenuringThreshold) {
            maxTenuringThreshold = r.getMaxTenuringThreshold();
        }
        // Latest desired occupancy — it tracks survivor sizing decisions.
        desiredOccupancy = r.getDesiredOccupancyAfterCollection();

        int calc = r.getCalculatedTenuringThreshold();
        thresholdCounts.merge(calc, 1L, Long::sum);
        // "Premature promotion" — survivor pressure pulled the calculated
        // threshold below the configured max, so objects of age < max
        // were promoted to old gen earlier than the user asked for.
        if (calc < r.getMaxTenuringThreshold()) prematurePromotions++;

        long[] bytesByAge = r.getBytesAtEachAge();
        if (bytesByAge == null) return;

        long cumulative = 0;
        // GCSee stores the array age-indexed: bytesByAge[i] holds the bytes
        // for age i. Index 0 is conventionally unused (an object only enters
        // a survivor space once it has survived at least one young pause).
        for (int age = 1; age < bytesByAge.length; age++) {
            long bytes = bytesByAge[age];
            cumulative += bytes;

            AgeStats stats = perAge.computeIfAbsent(age, k -> new AgeStats());
            stats.sumBytes += bytes;
            stats.countObservations += 1;
            if (bytes > stats.maxBytes) stats.maxBytes = bytes;

            // First age at which the running total exceeded the desired
            // survivor occupancy — Censum's "Desired exceeded at age".
            if (desiredOccupancy > 0
                    && cumulative > desiredOccupancy
                    && desiredExceededAtAge == 0) {
                desiredExceededAtAge = age;
            }
        }
    }

    public SummaryData getData() {
        SummaryData out = new SummaryData("Tenuring distribution summary");
        populateHeadline(out);
        out.addTable(buildBreakdownTable());
        out.addTable(buildThresholdDistributionTable());
        return out;
    }

    private void populateHeadline(SummaryData out) {
        double pct = collections > 0
                ? 100.0 * prematurePromotions / collections
                : 0.0;

        out.addHeadline("Max tenuring threshold",   maxTenuringThreshold,        "",  "int");
        out.addHeadline("Desired survivor occupancy", desiredOccupancy,          "B", "int");
        out.addHeadline("Premature promotion count", prematurePromotions,        "",  "int");
        out.addHeadline("Collections",               collections,                "",  "int");
        out.addHeadline("Premature promotions",      pct,                        "%", "decimal3");
        // "Desired exceeded at age" is conceptually a label — we encode the
        // sentinel "Never" as 0 in the headline scalar and let the frontend
        // map that to "Never" if it wants. For now, emit 0 so the headline
        // still validates against the numeric SummaryData.Scalar contract.
        out.addHeadline("Desired exceeded at age",   desiredExceededAtAge,       "",  "int");
    }

    private SummaryData.Table buildBreakdownTable() {
        List<SummaryData.Column> columns = new ArrayList<>();
        columns.add(new SummaryData.Column("avg",   "Average occupancy", "B", "int", "right"));
        columns.add(new SummaryData.Column("total", "Total",             "B", "int", "right"));
        columns.add(new SummaryData.Column("max",   "Maximum occupancy", "B", "int", "right"));

        List<SummaryData.Row> rows = new ArrayList<>(perAge.size());
        for (Map.Entry<Integer, AgeStats> e : perAge.entrySet()) {
            AgeStats s = e.getValue();
            Map<String, Double> cells = new LinkedHashMap<>();
            if (s.countObservations > 0) {
                cells.put("avg", (double) (s.sumBytes / s.countObservations));
            }
            cells.put("total", (double) s.sumBytes);
            cells.put("max",   (double) s.maxBytes);
            String key = String.valueOf(e.getKey());
            rows.add(SummaryData.row(key, key, columns, cells));
        }
        return new SummaryData.Table(
                "tenuring-breakdown", "Tenuring breakdown", columns, rows);
    }

    private SummaryData.Table buildThresholdDistributionTable() {
        List<SummaryData.Column> columns = new ArrayList<>();
        columns.add(new SummaryData.Column("count", "Count", "", "int", "right"));

        // Show every threshold from 1..max so a column of zeros is still
        // visible — matches Censum's full-range strip table layout.
        List<SummaryData.Row> rows = new ArrayList<>(maxTenuringThreshold);
        for (int t = 1; t <= maxTenuringThreshold; t++) {
            long c = thresholdCounts.getOrDefault(t, 0L);
            Map<String, Double> cells = new LinkedHashMap<>();
            cells.put("count", (double) c);
            String key = String.valueOf(t);
            rows.add(SummaryData.row(key, key, columns, cells));
        }
        return new SummaryData.Table(
                "tenuring-threshold-distribution",
                "Tenuring threshold distribution",
                columns, rows);
    }

    @Override
    public boolean isEmpty() {
        return collections == 0;
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
