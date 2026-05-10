package com.kodewerk.jma.aggregation.cause;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.CategoryBarData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * GC-cause histogram, categorised by garbage-collection type. One
 * x-axis bar per distinct GC type seen in the log (Young, FullGC,
 * Mixed, ZGCMajorYoung, …), each bar a stack whose segments are the
 * per-cause counts that landed in that type.
 * <p>
 * Modelled after Censum's {@code GCCauseReport}: the aggregator reports
 * {@code (gcTypeLabel, causeLabel)} tuples with a count of one each;
 * this class accumulates the union of cause labels (first-seen order
 * across the whole log, which drives stack order and colour) and emits
 * a {@link CategoryBarData} at serialization time.
 * <p>
 * Cause and GC-type names are deliberately not baked in. We just take
 * whatever {@code GarbageCollectionTypes.getLabel()} and
 * {@code GCCause.getLabel()} return, so a future GCSee that adds new
 * causes/types lights up additional bars or stack segments without a
 * code change here.
 * <p>
 * Empty if no events were recorded — i.e. only the synthetic JVM
 * termination event was seen.
 */
@Collates(GCCauseAggregator.class)
public final class GCCauseAggregation extends JmaAggregation {

    /**
     * Reused colour palette — first-seen cause gets the first colour,
     * second-seen the second, and so on, wrapping if a log has more
     * distinct causes than entries here. Same shape as the G1-phase
     * palette but a different set so the two charts don't visually
     * collide on the page.
     */
    private static final String[] PALETTE = {
            "#1e88e5", // blue
            "#fb8c00", // orange
            "#43a047", // green
            "#e53935", // red
            "#8e24aa", // purple
            "#00897b", // teal
            "#fdd835", // yellow
            "#6d4c41", // brown
            "#3949ab", // indigo
            "#d81b60", // pink
            "#7cb342", // light green
            "#546e7a"  // blue-grey
    };

    /**
     * Counts keyed first by GC-type label (outer key, becomes an x-axis
     * bar) then by cause label (inner key, becomes a stack series).
     * Iteration order on both maps preserves first-seen order.
     */
    private final Map<String, Map<String, Long>> countsByTypeAndCause = new LinkedHashMap<>();

    /** Union of cause labels seen, first-seen order — drives series order. */
    private final LinkedHashSet<String> causesSeen = new LinkedHashSet<>();

    /** Required by the GCSee module SPI. */
    public GCCauseAggregation() {}

    void recordCause(String gcTypeLabel, String causeLabel) {
        causesSeen.add(causeLabel);
        countsByTypeAndCause
                .computeIfAbsent(gcTypeLabel, k -> new LinkedHashMap<>())
                .merge(causeLabel, 1L, Long::sum);
    }

    public CategoryBarData getData() {
        List<CategoryBarData.Series> series = new ArrayList<>(causesSeen.size());
        int i = 0;
        for (String cause : causesSeen) {
            series.add(new CategoryBarData.Series(cause, cause, PALETTE[i % PALETTE.length]));
            i++;
        }
        CategoryBarData out = new CategoryBarData(
                "collection type", "count", "events", series);
        for (Map.Entry<String, Map<String, Long>> e : countsByTypeAndCause.entrySet()) {
            Map<String, Long> perCause = e.getValue();
            double[] values = new double[causesSeen.size()];
            int idx = 0;
            for (String cause : causesSeen) {
                Long c = perCause.get(cause);
                values[idx++] = (c != null ? c : 0L);
            }
            out.add(e.getKey(), values);
        }
        return out;
    }

    @Override
    public boolean isEmpty() {
        return countsByTypeAndCause.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
