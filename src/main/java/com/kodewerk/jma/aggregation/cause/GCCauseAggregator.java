package com.kodewerk.jma.aggregation.cause;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.GCCause;
import com.kodewerk.gcsee.event.GCEvent;
import com.kodewerk.gcsee.event.GarbageCollectionTypes;

/**
 * Walks every {@link GCEvent} the dispatcher fans out and feeds the
 * {@code (gcType, cause)} pair into {@link GCCauseAggregation}. Same
 * subscription set as Censum's {@code GCCauseAggregator} — every
 * collector on the dispatcher's radar contributes — so concurrent
 * events that happen to carry a cause (initial-mark, remark, etc.) are
 * counted alongside stop-the-world collections.
 * <p>
 * Not every {@link GCEvent} subtype carries a cause. Censum's report
 * substitutes {@link GCCause#UNKNOWN_GCCAUSE} for {@code null}; we do
 * the same so the chart never silently drops events. Likewise a missing
 * GC type becomes {@link GarbageCollectionTypes#Unknown}, which keeps a
 * stray event from collapsing the bucket map.
 */
@Aggregates({EventSource.G1GC, EventSource.GENERATIONAL, EventSource.ZGC})
public final class GCCauseAggregator extends Aggregator<GCCauseAggregation> {

    public GCCauseAggregator(GCCauseAggregation aggregation) {
        super(aggregation);
        register(GCEvent.class, this::onCollection);
    }

    private void onCollection(GCEvent event) {
        GarbageCollectionTypes type = event.getGarbageCollectionType();
        GCCause cause              = event.getGCCause();
        String typeLabel  = (type  != null ? type  : GarbageCollectionTypes.Unknown).getLabel();
        String causeLabel = (cause != null ? cause : GCCause.UNKNOWN_GCCAUSE).getLabel();
        aggregation().recordCause(typeLabel, causeLabel);
    }
}
