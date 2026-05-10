package com.kodewerk.jma.aggregation.mmu;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimum Mutator Utilization across three rolling time windows
 * (1 minute, 5 minutes, 15 minutes), modelled after Linux load-average
 * conventions. For each sample time {@code t} and window length
 * {@code W}, the value is
 * {@code (W − sum_of_pauses_in_(t − W, t]) / W × 100}.
 * <p>
 * Three series, sampled at 60-second steps over the log timeline. We
 * only emit a point once the trailing window is fully populated — a
 * 15-minute MMU before 15 minutes of log data has elapsed would be
 * misleading. The aggregator simply records every pause event; the
 * sliding-window math runs at {@code getData()} time so the cost is
 * paid once per request rather than on every event.
 */
@Collates(MmuAggregator.class)
public final class MmuAggregation extends JmaAggregation {

    /** Step (seconds) between consecutive MMU sample points. */
    private static final double STEP_SEC = 60.0;

    /** Window lengths in seconds and their human labels. */
    private static final double WIN_1M_SEC  = 60.0;
    private static final double WIN_5M_SEC  = 300.0;
    private static final double WIN_15M_SEC = 900.0;

    /** Pause-event tuples: {@code (startSec, durationSec)}. Insertion-ordered in time. */
    private final List<double[]> pauseEvents = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public MmuAggregation() {}

    public void recordPause(double startSec, double durationSec) {
        pauseEvents.add(new double[] { startSec, durationSec });
    }

    public XYSeriesData getData() {
        double duration = getLogDurationOrSpan();
        if (duration < WIN_1M_SEC || pauseEvents.isEmpty()) {
            return new XYSeriesData(
                    "time (s)", "% mutator", "%",
                    java.util.List.of(
                            new XYSeriesData.Series("1m",  "1 min",  "#fb8c00", java.util.List.of()),
                            new XYSeriesData.Series("5m",  "5 min",  "#1e88e5", java.util.List.of()),
                            new XYSeriesData.Series("15m", "15 min", "#43a047", java.util.List.of())));
        }

        double startMin = pauseEvents.get(0)[0];

        List<XYSeriesData.Point> p1  = new ArrayList<>();
        List<XYSeriesData.Point> p5  = new ArrayList<>();
        List<XYSeriesData.Point> p15 = new ArrayList<>();

        for (double t = startMin + STEP_SEC; t <= startMin + duration + 1e-6; t += STEP_SEC) {
            // Each window needs the trailing W seconds populated; below
            // that threshold the sample isn't representative. Skip.
            if (t - startMin >= WIN_1M_SEC) {
                p1.add(new XYSeriesData.Point(t, mutatorUtilizationPct(t, WIN_1M_SEC)));
            }
            if (t - startMin >= WIN_5M_SEC) {
                p5.add(new XYSeriesData.Point(t, mutatorUtilizationPct(t, WIN_5M_SEC)));
            }
            if (t - startMin >= WIN_15M_SEC) {
                p15.add(new XYSeriesData.Point(t, mutatorUtilizationPct(t, WIN_15M_SEC)));
            }
        }

        return new XYSeriesData(
                "time (s)", "% mutator", "%",
                java.util.List.of(
                        new XYSeriesData.Series("1m",  "1 min",  "#fb8c00", p1),
                        new XYSeriesData.Series("5m",  "5 min",  "#1e88e5", p5),
                        new XYSeriesData.Series("15m", "15 min", "#43a047", p15)));
    }

    /**
     * Mutator-utilization percentage in the trailing window
     * {@code (t − windowSec, t]}. Linear scan over the pause list — for
     * a typical log this is small and the per-call cost is negligible.
     * If a future log is large enough that this dominates, switch to a
     * shared two-pointer pass that advances all three windows
     * together.
     */
    private double mutatorUtilizationPct(double t, double windowSec) {
        double windowStart = t - windowSec;
        double pauseSum = 0.0;
        for (double[] p : pauseEvents) {
            double startSec = p[0];
            double endSec   = startSec + p[1];
            if (endSec <= windowStart) continue;
            if (startSec >= t)         break;
            // Clip the pause to the window in case it straddles a boundary.
            double clippedStart = Math.max(startSec, windowStart);
            double clippedEnd   = Math.min(endSec,   t);
            pauseSum += (clippedEnd - clippedStart);
        }
        if (pauseSum > windowSec) pauseSum = windowSec;
        return (1.0 - pauseSum / windowSec) * 100.0;
    }

    /** Diary's runtime estimate, falling back to first-to-last event span. */
    private double getLogDurationOrSpan() {
        double diary = getEstimatedRuntimeSeconds();
        if (diary > 0.0) return diary;
        if (pauseEvents.isEmpty()) return 0.0;
        double[] first = pauseEvents.get(0);
        double[] last  = pauseEvents.get(pauseEvents.size() - 1);
        return (last[0] + last[1]) - first[0];
    }

    @Override
    public boolean isEmpty() {
        return pauseEvents.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
