package com.kodewerk.jma.aggregation.zgc;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.GCCategory;
import com.kodewerk.jma.chart.ScatterData;

/**
 * ZGC live-set time series sampled at the start of each cycle's relocate
 * phase. Each ZGCCollection carries a {@code ZGCLiveSummary} with three
 * snapshots — markEnd, relocateStart, relocateEnd — taken at the
 * corresponding cycle boundaries. relocate-start is the "what's live
 * after marking, before relocation" moment, and the gap between it and
 * relocate-end shows what relocation reclaimed.
 * <p>
 * Coloured by {@link GCCategory} so Young / Old / Full cycles are
 * visually separable in the same way every other ZGC heap chart is.
 */
@Collates(ZgcLiveAtRelocateStartAggregator.class)
public final class ZgcLiveAtRelocateStartAggregation extends JmaAggregation {

    private final ScatterData data = new ScatterData(
            "time (s)", "live (MB)", "MB");

    private int    eventsSeen        = 0;
    private double firstEventTime    = Double.NaN;
    private double lastEventTime     = Double.NaN;
    private int    invalidSampleCount = 0;
    private double lastInvalidTime   = Double.NaN;

    /** Required by the GCSee module SPI. */
    public ZgcLiveAtRelocateStartAggregation() {}

    public void recordLive(GCCategory category, double tSec, double mb) {
        data.add(category, tSec, mb);
    }

    public void noteEventSeen(double tSec) {
        eventsSeen++;
        if (Double.isNaN(firstEventTime)) firstEventTime = tSec;
        lastEventTime = tSec;
    }

    public void noteInvalidSample(double tSec) {
        invalidSampleCount++;
        lastInvalidTime = tSec;
    }

    public ScatterData getData() { return data; }

    public int    getEventsSeen()        { return eventsSeen; }
    public double getFirstEventTime()    { return firstEventTime; }
    public double getLastEventTime()     { return lastEventTime; }
    public int    getInvalidSampleCount(){ return invalidSampleCount; }
    public double getLastInvalidTime()   { return lastInvalidTime; }

    @Override
    public boolean isEmpty() { return data.isEmpty(); }

    @Override
    public boolean hasWarning() { return invalidSampleCount > 0; }

    @Override
    public String getWarningMessage() {
        return invalidSampleCount > 0
                ? "One or more ZGC cycles did not carry a valid live-summary and were skipped."
                : null;
    }
}
