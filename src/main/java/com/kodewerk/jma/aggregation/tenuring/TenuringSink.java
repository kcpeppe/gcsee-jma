package com.kodewerk.jma.aggregation.tenuring;

import com.kodewerk.gcsee.event.jvm.SurvivorRecord;

/**
 * Receiver contract for tenuring data. The four tenuring aggregations
 * (summary table, average volume by age, calculated threshold over time,
 * surviving bytes per age over time) all implement this interface so a
 * single {@link TenuringAggregator} can drive any of them — GCSee's
 * reflection-based instantiation creates one aggregator per registered
 * aggregation, and each aggregator delegates the per-event work back to
 * the sink it was paired with.
 * <p>
 * The interface is deliberately narrow: a timestamp and the parsed
 * {@link SurvivorRecord}. Each aggregation projects out only the fields
 * it cares about, so the summary view never has to keep a per-event
 * point list and the over-time views never have to maintain running
 * per-age statistics.
 */
public interface TenuringSink {

    /**
     * Record one SurvivorRecord into this sink.
     *
     * @param tSec   event timestamp, seconds since the log's reference point
     * @param record the parsed survivor record (never null when called
     *               from {@link TenuringAggregator})
     */
    void record(double tSec, SurvivorRecord record);
}
