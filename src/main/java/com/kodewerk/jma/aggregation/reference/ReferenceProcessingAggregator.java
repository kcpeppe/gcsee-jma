package com.kodewerk.jma.aggregation.reference;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.ReferenceGCSummary;
import com.kodewerk.gcsee.event.g1gc.G1GCPauseEvent;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.jvm.JVMEvent;

/**
 * Pulls the per-pause {@link ReferenceGCSummary} off pause-bearing
 * events and feeds {@link ReferenceProcessingAggregation}'s row
 * store. Both reference views (count and time) collate with this
 * aggregator class via {@code @Collates(...)}; GCSee instantiates one
 * aggregator per registered aggregation, so each view gets its own
 * event stream from the dispatcher.
 * <p>
 * ZGC's reference processing accounting is exposed differently and is
 * not handled here.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL})
public final class ReferenceProcessingAggregator
        extends Aggregator<ReferenceProcessingAggregation> {

    private static final double SECONDS_TO_MILLIS = 1000.0;

    public ReferenceProcessingAggregator(ReferenceProcessingAggregation aggregation) {
        super(aggregation);
        register(G1GCPauseEvent.class,           this::onG1Pause);
        register(GenerationalGCPauseEvent.class, this::onGenerationalPause);
    }

    private void onG1Pause(G1GCPauseEvent event) {
        record(event, event.getReferenceGCSummary());
    }

    private void onGenerationalPause(GenerationalGCPauseEvent event) {
        record(event, event.getReferenceGCSummary());
    }

    private void record(JVMEvent event, ReferenceGCSummary ref) {
        if (ref == null || event.getDateTimeStamp() == null) return;
        // Skip events with no reference activity at all — the parser
        // didn't find a Reference Processing block, or the pause did
        // none. A point at (t, 0, 0, 0, 0) would just be visual noise.
        int totalCount = ref.getSoftReferenceCount()
                       + ref.getWeakReferenceCount()
                       + ref.getFinalReferenceCount()
                       + ref.getPhantomReferenceCount();
        double totalMs = (ref.getSoftReferencePauseTime()
                       +  ref.getWeakReferencePauseTime()
                       +  ref.getFinalReferencePauseTime()
                       +  ref.getPhantomReferencePauseTime()) * SECONDS_TO_MILLIS;
        if (totalCount == 0 && totalMs <= 0.0) return;

        aggregation().recordEvent(new ReferenceProcessingAggregation.EventRow(
                event.getDateTimeStamp().toSeconds(),
                ref.getSoftReferenceCount(),    ref.getSoftReferencePauseTime()    * SECONDS_TO_MILLIS,
                ref.getWeakReferenceCount(),    ref.getWeakReferencePauseTime()    * SECONDS_TO_MILLIS,
                ref.getFinalReferenceCount(),   ref.getFinalReferencePauseTime()   * SECONDS_TO_MILLIS,
                ref.getPhantomReferenceCount(), ref.getPhantomReferencePauseTime() * SECONDS_TO_MILLIS));
    }
}
