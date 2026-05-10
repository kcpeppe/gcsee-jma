package com.kodewerk.jma.aggregation.cpu;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.CPUSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;

/**
 * Pulls the per-pause {@link CPUSummary} off pause-bearing events and
 * feeds three points into {@link CpuBreakoutAggregation} — user,
 * kernel, and wall-clock seconds. Mirrors the pause selection used by
 * the CPU analytic so the chart and the analytic agree on which
 * events contributed.
 * <p>
 * ZGC's CPU accounting is split across phases rather than reported as
 * a single per-cycle summary, so it is not handled here. If a future
 * ZGC view ships a unified CPU summary we can add a third subscription
 * the same way.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL})
public final class CpuBreakoutAggregator extends Aggregator<CpuBreakoutAggregation> {

    public CpuBreakoutAggregator(CpuBreakoutAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        record(event, event.getCpuSummary());
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        record(event, event.getCpuSummary());
    }

    private void record(JVMEvent event, CPUSummary cpu) {
        if (cpu == null || event.getDateTimeStamp() == null) return;
        double user   = cpu.getUser();
        double kernel = cpu.getKernel();
        double wall   = cpu.getWallClock();
        // Skip events with no CPU data at all — the parser couldn't find
        // the [Times: ...] line. A point at (t, 0, 0, 0) would just be
        // visual noise.
        if (user <= 0.0 && kernel <= 0.0 && wall <= 0.0) return;
        aggregation().recordCpuSample(
                event.getDateTimeStamp().toSeconds(), user, kernel, wall);
    }
}
