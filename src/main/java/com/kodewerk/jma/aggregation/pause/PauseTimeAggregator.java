package com.kodewerk.jma.aggregation.pause;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Captures stop-the-world pause durations for every supported collector
 * family and feeds them into a single {@link PauseTimeAggregation}.
 * <p>
 * Event selection:
 * <ul>
 *   <li><b>G1</b> — every subtype of {@code G1GCPauseEvent} (young, mixed,
 *       initial-mark, remark, cleanup, full).</li>
 *   <li><b>Generational</b> — every subtype of
 *       {@code GenerationalGCPauseEvent} (ParNew, DefNew, PSYoungGen,
 *       CMS initial-mark / remark, full GC, concurrent-mode failures).</li>
 *   <li><b>ZGC</b> — the three STW sub-pauses on a {@code ZGCCollection}
 *       (mark-start, mark-end, relocate-start). The overall cycle duration
 *       would misrepresent pause cost because ZGC is largely concurrent.</li>
 * </ul>
 * All durations are converted from seconds (GCSee's native unit) to
 * milliseconds before being recorded.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class PauseTimeAggregator extends Aggregator<PauseTimeAggregation> {

    private static final double SECONDS_TO_MILLIS = 1000.0;

    public PauseTimeAggregator(PauseTimeAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        recordStandardPause(event);
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        recordStandardPause(event);
    }

    /** Records one point using {@code event.getDuration()} directly. */
    private void recordStandardPause(JVMEvent event) {
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        double t       = event.getDateTimeStamp().toSeconds();
        double pauseMs = event.getDuration() * SECONDS_TO_MILLIS;
        if (pauseMs <= 0.0) return;

        aggregation().recordPause(category, t, pauseMs);
    }

    /**
     * ZGC exposes three STW sub-pauses on each cycle, each with its own
     * timestamp. We record them individually so pause-time outliers are
     * attributable to a specific phase rather than hidden in a cycle sum.
     */
    private void onZgcCycle(ZGCCollection event) {
        GCCategory category = GCCategoryMapper.categoryOf(event);
        if (category == null) return;

        recordZgcSubPause(category, event.getPauseMarkStartTimeStamp(),    event.getPauseMarkStartDuration());
        recordZgcSubPause(category, event.getPauseMarkEndTimeStamp(),      event.getPauseMarkEndDuration());
        recordZgcSubPause(category, event.getPauseRelocateStartTimeStamp(), event.getPauseRelocateStartDuration());
    }

    private void recordZgcSubPause(GCCategory category,
                                   com.kodewerk.gcsee.time.DateTimeStamp when,
                                   double seconds) {
        if (when == null || seconds <= 0.0) return;
        aggregation().recordPause(category, when.toSeconds(), seconds * SECONDS_TO_MILLIS);
    }
}
