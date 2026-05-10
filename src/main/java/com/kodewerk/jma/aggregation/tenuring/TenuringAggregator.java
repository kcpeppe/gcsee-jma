package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.aggregator.Aggregates;
import com.kodewerk.gcsee.aggregator.Aggregator;
import com.kodewerk.gcsee.aggregator.EventSource;
import com.kodewerk.gcsee.event.jvm.SurvivorRecord;
import com.kodewerk.gcsee.time.DateTimeStamp;
import com.kodewerk.jma.aggregation.JmaAggregation;

/**
 * Single aggregator for every tenuring view in the project. Listens on
 * {@link EventSource#SURVIVOR} (the parser channel that emits
 * {@link SurvivorRecord} events) and dispatches each record to the
 * concrete aggregation it was paired with via the {@code @Collates}
 * annotation.
 * <p>
 * Each registered tenuring aggregation extends {@link JmaAggregation} and
 * implements {@link TenuringSink}. GCSee instantiates one aggregator per
 * registered aggregation by walking this class's declared constructors
 * and matching by parameter type — the {@code JmaAggregation} parameter
 * resolves for every concrete tenuring aggregation. The runtime cast to
 * {@code TenuringSink} is enforced at construction so the failure mode
 * is "won't load" rather than "ClassCastException at first event".
 */
@Aggregates(EventSource.SURVIVOR)
public final class TenuringAggregator extends Aggregator<JmaAggregation> {

    public TenuringAggregator(JmaAggregation aggregation) {
        super(aggregation);
        if (!(aggregation instanceof TenuringSink)) {
            throw new IllegalArgumentException(
                    "TenuringAggregator requires an aggregation that implements TenuringSink; got "
                    + aggregation.getClass().getName());
        }
        register(SurvivorRecord.class, this::onSurvivorRecord);
    }

    private void onSurvivorRecord(SurvivorRecord record) {
        DateTimeStamp ts = record.getDateTimeStamp();
        if (ts == null) return;
        ((TenuringSink) aggregation()).record(ts.toSeconds(), record);
    }
}
