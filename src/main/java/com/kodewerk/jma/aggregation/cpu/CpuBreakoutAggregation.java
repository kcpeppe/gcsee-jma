package com.kodewerk.jma.aggregation.cpu;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.XYSeriesData;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-pause CPU breakout — three series (User, Kernel, Wall clock)
 * sourced from each pause event's {@code [Times: user= sys= real= ]}
 * line. Plotted as a scatter on a seconds y-axis so an operator can
 * eyeball:
 * <ul>
 *   <li>How parallel the pause was — user time roughly equals
 *       (worker count) × wall on a healthy parallel collector.</li>
 *   <li>Whether kernel time is unusually high — see the CPU
 *       analytic for a categorical verdict on the same data.</li>
 *   <li>Wall-clock outliers that don't match the user / kernel
 *       sums — typical sign of thread descheduling.</li>
 * </ul>
 * Series colours match the analytic conventions: green for user,
 * red for kernel (high = bad), blue for wall.
 */
@Collates(CpuBreakoutAggregator.class)
public final class CpuBreakoutAggregation extends JmaAggregation {

    private final List<XYSeriesData.Point> userPoints   = new ArrayList<>();
    private final List<XYSeriesData.Point> kernelPoints = new ArrayList<>();
    private final List<XYSeriesData.Point> wallPoints   = new ArrayList<>();

    /** Required by the GCSee module SPI. */
    public CpuBreakoutAggregation() {}

    public void recordCpuSample(double tSec,
                                double userSec,
                                double kernelSec,
                                double wallSec) {
        userPoints.add(new XYSeriesData.Point(tSec, userSec));
        kernelPoints.add(new XYSeriesData.Point(tSec, kernelSec));
        wallPoints.add(new XYSeriesData.Point(tSec, wallSec));
    }

    public XYSeriesData getData() {
        return new XYSeriesData(
                "time (s)", "CPU time (s)", "s",
                java.util.List.of(
                        new XYSeriesData.Series("user",   "User",       "#43a047", userPoints),
                        new XYSeriesData.Series("kernel", "Kernel",     "#e53935", kernelPoints),
                        new XYSeriesData.Series("wall",   "Wall clock", "#1e88e5", wallPoints)));
    }

    @Override
    public boolean isEmpty() {
        return userPoints.isEmpty() && kernelPoints.isEmpty() && wallPoints.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
