package com.kodewerk.jma.aggregation.mmu;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.zgc.ZGCCollection;
import com.kodewerk.gcsee.time.DateTimeStamp;

/**
 * Collects every stop-the-world pause for {@link MmuAggregation}'s
 * sliding-window mutator-utilization computation. Same pause-selection
 * logic as the existing pause-time view: G1 + Generational pauses
 * one-for-one, ZGC sub-pauses as three independent samples per cycle.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class MmuAggregator extends Aggregator<MmuAggregation> {

    public MmuAggregator(MmuAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
        register(ZGCCollection.class,            this::onZgcCycle);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        recordPause(event.getDateTimeStamp(), event.getDuration());
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        recordPause(event.getDateTimeStamp(), event.getDuration());
    }

    private void onZgcCycle(ZGCCollection event) {
        recordPause(event.getPauseMarkStartTimeStamp(),    event.getPauseMarkStartDuration());
        recordPause(event.getPauseMarkEndTimeStamp(),      event.getPauseMarkEndDuration());
        recordPause(event.getPauseRelocateStartTimeStamp(), event.getPauseRelocateStartDuration());
    }

    private void recordPause(DateTimeStamp ts, double durationSec) {
        if (ts == null || durationSec <= 0.0) return;
        aggregation().recordPause(ts.toSeconds(), durationSec);
    }
}
