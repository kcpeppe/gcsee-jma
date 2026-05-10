package com.kodewerk.jma.aggregation.gctime;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.time.DateTimeStamp;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.GCCategoryMapper;

/**
 * Computes the per-event "% time in GC" series. Tracks the previous
 * pause start time and emits one point per new pause as
 * {@code 100 × duration / (thisStart − prevStart)}.
 * <p>
 * Pause-event selection mirrors the pause-time chart: G1 + Generational
 * pause-bearing events one-for-one, and ZGC's three sub-pauses inside
 * each {@code ZGCCollection} (mark-start, mark-end, relocate-start) so
 * the resulting series is consistent with the pause-time scatter.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class PercentTimeInGcAggregator extends Aggregator<PercentTimeInGcAggregation> {

    /** Start of the previous recorded pause (seconds), or NaN if none yet. */
    private double prevStartSec = Double.NaN;

    public PercentTimeInGcAggregator(PercentTimeInGcAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onPause);
        register(GenerationalGCPauseEvent.class, this::onPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onPause(JVMEvent event) {
        double startSec  = event.getDateTimeStamp().toSeconds();
        double durationS = event.getDuration();
        sample(event, startSec, durationS);
    }

    /**
     * ZGC reports a single Collection event that wraps three STW
     * sub-pauses, each with its own start time and duration. We treat
     * them as three independent samples so the chart matches what the
     * pause-time view shows for ZGC.
     */
    private void onZgcCycle(ZGCCollection event) {
        sampleZgc(event, event.getPauseMarkStartTimeStamp(),    event.getPauseMarkStartDuration());
        sampleZgc(event, event.getPauseMarkEndTimeStamp(),      event.getPauseMarkEndDuration());
        sampleZgc(event, event.getPauseRelocateStartTimeStamp(), event.getPauseRelocateStartDuration());
    }

    private void sampleZgc(ZGCCollection event, DateTimeStamp ts, double durationSec) {
        if (ts == null || durationSec <= 0.0) return;
        sample(event, ts.toSeconds(), durationSec);
    }

    private void sample(JVMEvent event, double startSec, double durationSec) {
        if (!Double.isNaN(prevStartSec)) {
            double interval = startSec - prevStartSec;
            // interval can dip slightly negative on an out-of-order ZGC
            // sub-pause; skip rather than emit an absurd %.
            if (interval > 0.0 && durationSec >= 0.0) {
                GCCategory category = GCCategoryMapper.categoryOf(event);
                if (category != null) {
                    double pct = 100.0 * durationSec / interval;
                    aggregation().recordPercent(category, startSec, pct);
                }
            }
        }
        prevStartSec = startSec;
    }
}
