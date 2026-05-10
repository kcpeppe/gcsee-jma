package com.kodewerk.jma.analytics.logduration;

import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.analytics.AnalyticsFinding;
import com.kodewerk.jma.analytics.Impact;

import java.util.List;

/**
 * Log-duration feature group analytics. A single check that gauges how
 * confident the rest of the analytics page can be about its findings:
 * a 20-minute log can't tell you anything about overnight memory growth,
 * a multi-day log can.
 * <p>
 * Thresholds are deliberately blunt (1 hour, 24 hours) and they match
 * how operators read GC logs in practice — anything shorter than an hour
 * is a snapshot, anything longer than a day starts catching slow
 * patterns. Sits last in the analytics list so it reads as a footer
 * that frames everything above.
 */
public final class LogDurationAnalytics {

    private static final String GROUP = "Log duration";

    /** 1 hour — below this, every other finding has low confidence. */
    private static final double ONE_HOUR_SEC = 3600.0;

    /** 24 hours — long enough to surface steady-state and slow patterns. */
    private static final double ONE_DAY_SEC = 24.0 * 3600.0;

    private LogDurationAnalytics() {}

    public static void evaluate(AnalyticsAggregation agg, List<AnalyticsFinding> out) {
        double seconds = agg.getLogDurationSec();
        if (seconds <= 0.0) {
            // No reliable duration — Diary couldn't compute a runtime
            // and we never saw enough events to span. Skip silently
            // rather than lie about confidence.
            return;
        }

        Impact impact;
        String verdict;
        String confidence;
        if (seconds < ONE_HOUR_SEC) {
            impact     = Impact.SIGNIFICANT;
            verdict    = "Log too short";
            confidence = "Confidence in every finding above is low — most behaviours "
                       + "the analytics check (memory growth, periodic patterns, "
                       + "steady-state heap stability) need more than an hour to surface.";
        } else if (seconds < ONE_DAY_SEC) {
            impact     = Impact.CONCERNING;
            verdict    = "Log under 24 hours";
            confidence = "Confidence is better but still weak. Acute issues will be "
                       + "visible, but anything tied to business cycles, overnight "
                       + "load, or slow-developing leaks may not have had time to "
                       + "appear in the data.";
        } else {
            impact     = Impact.OK;
            verdict    = "Good log length";
            confidence = "Long enough to catch steady-state behaviour, slow leaks, "
                       + "and business-cycle effects.";
        }

        String formatted = formatDuration(seconds);
        String message = verdict + ". Log spans " + formatted + ". " + confidence;

        String details = """
                A GC log's analytical value scales with how much of the JVM's \
                normal operating range it captures. Roughly:
                  • Under 1 hour — startup-dominated. JIT compilation, class \
                loading and connection-pool warmup distort almost everything. \
                Useful for reproducing a specific incident, not for steady-state \
                conclusions.
                  • 1–24 hours — covers the working day. Acute problems \
                (allocation spikes, full GCs, kernel pressure) show up reliably; \
                slow patterns may not.
                  • Over 24 hours — covers diurnal load, batch-job windows, slow \
                memory growth. The trend tests in this report (Mann-Kendall on \
                heap occupancy, periodic-System.gc() detection) gain real \
                statistical power here.

                If the log is shorter than the issue you're investigating, \
                consider re-running with -Xlog:gc*:file=...:utctime,uptime \
                across a longer window — the file is small (a few MB per day) \
                and there's no observable performance cost.""";

        out.add(new AnalyticsFinding(GROUP, "Log duration",
                impact, message, details));
    }

    /**
     * Human-readable duration formatter — picks the largest unit that
     * makes the number readable. Returns shapes like "45m 12s",
     * "3h 14m", "2d 17h". Trailing zero-units are suppressed.
     */
    private static String formatDuration(double seconds) {
        long total = (long) Math.floor(seconds);
        long days     = total / 86400;
        long hours    = (total % 86400) / 3600;
        long minutes  = (total % 3600) / 60;
        long secs     = total % 60;

        if (days > 0)  return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }
}
