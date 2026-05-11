package com.kodewerk.jma.aggregation.summary;

import com.kodewerk.gcsee.aggregator.Collates;
import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.chart.SummaryData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Log-summary view: a single tabular rollup per GC log, covering every
 * collector family from Serial back in JDK 1.4.2 through generational
 * ZGC. Produces one {@link SummaryData} with a headline scalar
 * block, one or more tables (always a stop-the-world pauses table,
 * optionally a concurrent-phases table when concurrent events were
 * seen), and a list of notes for rare-event counters (heap resizes,
 * back-to-back collections, etc.).
 * <p>
 * Row keys are the GCSee event class simple name ({@code DefNew},
 * {@code G1Young}, {@code InitialMark}, etc.) so the grouping matches the
 * type-based dispatch GCSee already uses internally. Columns are
 * collector-aware: the concurrent table carries an "Aborted" column on
 * CMS logs, and the concurrent table as a whole is only emitted when
 * any concurrent events appeared. G1's to-space-exhausted total is a
 * log-level note rather than a per-row column — the original attempt
 * to make it a column was always sparse and didn't carry useful
 * per-row information.
 * <p>
 * On ZGC logs the headline drops Full-GC and concurrent-time scalars
 * (generational ZGC has no Full GC in steady state and its concurrent
 * work doesn't contribute to the application-throughput formula) and
 * adds an allocation-stall counter — the ZGC-specific "heap too small"
 * signal. Each ZGCCollection folds into one row keyed by its event
 * simple name (ZGCYoungCollection / ZGCOldCollection / ZGCFullCollection)
 * with the three sub-pauses (mark-start, mark-end, relocate-start)
 * rolled together into the row's totals; see
 * {@link SummaryAggregator#onZgcCycle}.
 */
@Collates(SummaryAggregator.class)
public final class SummaryAggregation extends JmaAggregation {

    /** Per-row STW accumulator. Fields are in GCSee's native units. */
    static final class StwRow {
        final String label;
        long   invocations           = 0;
        double totalPauseSec         = 0.0;
        double maxPauseSec           = 0.0;
        double totalBytesAllocatedMb = 0.0;
        double totalBytesRecoveredMb = 0.0;
        double lastStartSec          = Double.NaN;
        double sumIntervalsSec       = 0.0;
        long   intervalCount         = 0;

        StwRow(String label) { this.label = label; }
    }

    /** Per-row concurrent accumulator. */
    static final class ConcurrentRow {
        final String label;
        long   invocations = 0;
        double totalWallSec = 0.0;
        double totalCpuSec  = 0.0;
        double maxWallSec   = 0.0;
        long   aborted      = 0;

        ConcurrentRow(String label) { this.label = label; }
    }

    // Insertion-ordered so the frontend shows rows in the order they were
    // first seen in the log (typically young-before-old which reads well).
    private final Map<String, StwRow> stwRows = new LinkedHashMap<>();
    private final Map<String, ConcurrentRow> concurrentRows = new LinkedHashMap<>();

    // Log-level counters surfaced as notes on the summary.
    // Note: SystemGC is tracked only via its own row in the STW pauses
    // table — we deliberately don't maintain a separate headline counter
    // because it would duplicate (and can disagree with) the table row.
    private long heapResizeCount      = 0;
    private long backToBackCount      = 0;
    private long g1InitialMarkCount   = 0;
    private long concurrentAborted    = 0;
    private long toSpaceExhaustedCount = 0;
    /** ZGC: collections triggered by an allocation stall (GCCause.ALLOC_STALL). */
    private long zgcAllocationStallCount = 0;

    // Collector-family flags — used at emit time to decide which
    // headlines, tables, and columns to include. Flags are set by the
    // matching aggregator as soon as it identifies a representative
    // event from each family; {@link #detectCollector()} resolves
    // them into the canonical short name the frontend filters on.
    private boolean anyG1Seen       = false;
    private boolean anyCmsSeen      = false;
    private boolean anyParallelSeen = false;
    private boolean anySerialSeen   = false;
    private boolean anyZgcSeen      = false;

    /** Required by the GCSee module SPI. */
    public SummaryAggregation() {}

    // ----- Aggregator-side mutation API -----

    StwRow stwRow(String key, String label) {
        return stwRows.computeIfAbsent(key, k -> new StwRow(label));
    }

    ConcurrentRow concurrentRow(String key, String label) {
        return concurrentRows.computeIfAbsent(key, k -> new ConcurrentRow(label));
    }

    void noteHeapResize()        { heapResizeCount++; }
    void noteBackToBack()        { backToBackCount++; }
    void noteG1InitialMark()     { g1InitialMarkCount++; }
    void noteConcurrentAbort()   { concurrentAborted++; }
    void noteToSpaceExhausted()  { toSpaceExhaustedCount++; }
    void noteAllocationStall()   { zgcAllocationStallCount++; }
    void markG1Seen()            { anyG1Seen = true; }
    void markCmsSeen()           { anyCmsSeen = true; }
    void markParallelSeen()      { anyParallelSeen = true; }
    void markSerialSeen()        { anySerialSeen = true; }
    void markZgcSeen()           { anyZgcSeen = true; }

    /**
     * Resolve the observed-event flags into a single collector-family
     * tag the frontend filters on. Priority order reflects specificity:
     * ZGC and G1 events are unique to their collectors, CMS detection
     * requires a CMS-only marker (so it overrides "Parallel" if both
     * somehow appear), Parallel/Serial fall out from PS-prefixed and
     * DefNew events respectively.
     */
    String detectCollector() {
        if (anyZgcSeen)      return "zgc";
        if (anyG1Seen)       return "g1";
        if (anyCmsSeen)      return "cms";
        if (anyParallelSeen) return "parallel";
        if (anySerialSeen)   return "serial";
        return "unknown";
    }

    // ----- View-side read API -----

    public SummaryData getData() {
        SummaryData out = new SummaryData("Log summary");
        String collector = detectCollector();
        out.setCollector(collector);
        // Headline scalars vary by collector family. Parallel and Serial
        // share a tighter set tailored to their stop-the-world-only
        // behaviour; G1, CMS, ZGC, and the unknown-fallback case use
        // the original headline layout that includes concurrent-time
        // and MMU columns. The Collection Type Breakdown (the STW
        // table) is reused as-is across collectors.
        if ("parallel".equals(collector) || "serial".equals(collector)) {
            populateParallelSerialHeadline(out);
        } else if ("zgc".equals(collector)) {
            populateZgcHeadline(out);
        } else {
            populateHeadline(out);
        }
        out.addTable(buildStwTable());
        if (!concurrentRows.isEmpty()) {
            out.addTable(buildConcurrentTable());
        }
        populateNotes(out);
        return out;
    }

    /**
     * Wall-clock duration of the JVM run that produced this log.
     * <p>
     * Computed by {@code Diary} during the diarizer pre-pass and forwarded
     * onto every {@code Aggregation} via the {@code JVMTermination} event,
     * so we just delegate. The Diary algorithm accounts for log truncation
     * (rotation, missing head) using {@code ZERO_GCID} when the log is
     * unified and an ε/K heuristic otherwise — both producing a runtime
     * that's correct to within the precision of the inputs.
     */
    private double logDurationSec() {
        return estimatedRuntime();
    }

    private double totalPauseSec() {
        double t = 0.0;
        for (StwRow r : stwRows.values()) t += r.totalPauseSec;
        return t;
    }

    private double totalConcurrentWallSec() {
        double t = 0.0;
        for (ConcurrentRow r : concurrentRows.values()) t += r.totalWallSec;
        return t;
    }

    private double totalAllocatedMb() {
        double t = 0.0;
        for (StwRow r : stwRows.values()) t += r.totalBytesAllocatedMb;
        return t;
    }

    private double totalRecoveredMb() {
        double t = 0.0;
        for (StwRow r : stwRows.values()) t += r.totalBytesRecoveredMb;
        return t;
    }

    private double fullGcPauseSec() {
        double t = 0.0;
        for (Map.Entry<String, StwRow> e : stwRows.entrySet()) {
            if (isFullGcKey(e.getKey())) t += e.getValue().totalPauseSec;
        }
        return t;
    }

    private static boolean isFullGcKey(String key) {
        // Keys come from the event class simple name — see GCCategoryMapper.
        return key.equals("FullGC")
            || key.equals("PSFullGC")
            || key.equals("G1FullGC")
            || key.equals("SystemGC")
            || key.equals("ConcurrentModeFailure")
            || key.equals("ConcurrentModeInterrupted");
    }

    private void populateHeadline(SummaryData out) {
        double logDur       = logDurationSec();
        double totalPause   = totalPauseSec();
        double totalConcur  = totalConcurrentWallSec();
        double allocatedMb  = totalAllocatedMb();
        double recoveredMb  = totalRecoveredMb();

        double pctPaused   = logDur > 0.0 ? 100.0 * totalPause / logDur : 0.0;
        double pctConcur   = logDur > 0.0 ? 100.0 * totalConcur / logDur : 0.0;
        double mmu         = logDur > 0.0 ? 1.0 - (totalPause / logDur) : 1.0;
        double appTime     = Math.max(0.0, logDur - totalPause);
        double avgAllocRate = appTime > 0.0 ? allocatedMb / appTime : 0.0;
        double avgRecovRate = logDur > 0.0 ? recoveredMb / logDur : 0.0;
        // Application throughput: pause-based. Concurrent time does consume CPU
        // but the user's thread isn't paused for it, so we keep the simple
        // (1 - pause/runtime) formulation here.
        double appThroughput = logDur > 0.0 ? 100.0 * (1.0 - totalPause / logDur) : 100.0;

        out.addHeadline("Log duration",            logDur,         "s",    "decimal2");
        out.addHeadline("MMU",                     mmu,            "",     "decimal3");
        out.addHeadline("Total pause",             totalPause,     "s",    "decimal2");
        out.addHeadline("% paused",                pctPaused,      "%",    "decimal2");
        out.addHeadline("Total concurrent",        totalConcur,    "s",    "decimal2");
        out.addHeadline("% concurrent",            pctConcur,      "%",    "decimal2");
        out.addHeadline("kB allocated",            allocatedMb,    "MB",   "int");
        out.addHeadline("Application throughput",  appThroughput,  "%",    "decimal2");
        out.addHeadline("Avg allocation rate",     avgAllocRate,   "MB/s", "decimal1");
        out.addHeadline("Full-GC pause",           fullGcPauseSec(), "s",  "decimal2");
        out.addHeadline("Avg recovery rate",       avgRecovRate,   "MB/s", "decimal1");
    }

    /**
     * Headline scalars for the Parallel and Serial collectors. Neither
     * runs concurrent-phase work, so the headline drops the
     * concurrent / MMU readouts the generic version carries and
     * adds the Full-GC-to-total-pause ratio (a quick "how much of
     * your pause budget did the worst events eat?" gauge).
     * <p>
     * {@code SystemGC} pauses are counted into the total / full-GC
     * pause time per the project convention — a {@code System.gc()}
     * call lands in the STW pauses table as its own row, but its
     * pause time still contributes to the aggregate ratio.
     */
    private void populateParallelSerialHeadline(SummaryData out) {
        double logDur       = logDurationSec();
        double totalPause   = totalPauseSec();
        double allocatedMb  = totalAllocatedMb();
        double recoveredMb  = totalRecoveredMb();
        double fullGcPause  = fullGcPauseSec();

        double appTime      = Math.max(0.0, logDur - totalPause);
        double avgAllocRate = appTime > 0.0 ? allocatedMb / appTime : 0.0;
        double avgRecovRate = logDur > 0.0 ? recoveredMb / logDur : 0.0;
        double appThroughput = logDur > 0.0
                ? 100.0 * (1.0 - totalPause / logDur)
                : 100.0;
        double fullToTotalRatio = totalPause > 0.0
                ? 100.0 * fullGcPause / totalPause
                : 0.0;

        out.addHeadline("Total allocated",             allocatedMb,    "MB",   "int");
        out.addHeadline("Total recovered",             recoveredMb,    "MB",   "int");
        out.addHeadline("Avg allocation rate",         avgAllocRate,   "MB/s", "decimal1");
        out.addHeadline("Avg recovery rate",           avgRecovRate,   "MB/s", "decimal1");
        out.addHeadline("Back-to-back collections",    backToBackCount, "",    "int");
        out.addHeadline("Total pause time",            totalPause,     "s",    "decimal2");
        out.addHeadline("Full-GC pause time",          fullGcPause,    "s",    "decimal2");
        out.addHeadline("Full-GC / total pause",       fullToTotalRatio, "%",  "decimal2");
        out.addHeadline("Application throughput",      appThroughput,  "%",    "decimal2");
    }

    /**
     * Headline scalars for generational ZGC. ZGC has no Full GC in
     * normal operation (Full collections are an emergency fallback —
     * surfaced as a note if the row exists rather than as a headline),
     * its STW work is partitioned across three sub-pauses per cycle so
     * the row-level "Max" already shows worst-case pause magnitude, and
     * the dominant operational concern is sustained allocation pressure
     * rather than full-heap reclamation. The headline therefore focuses
     * on cycle volume by type, application throughput (target 99.9%),
     * and the allocation-stall counter — the ZGC-specific "heap too
     * small" signal.
     */
    private void populateZgcHeadline(SummaryData out) {
        double logDur     = logDurationSec();
        double totalPause = totalPauseSec();
        double appThroughput = logDur > 0.0
                ? 100.0 * (1.0 - totalPause / logDur)
                : 100.0;
        double avgCycleSec = 0.0;
        long cycleCount = 0;
        for (StwRow r : stwRows.values()) cycleCount += r.invocations;
        if (cycleCount > 0) avgCycleSec = totalPause / cycleCount;

        long youngCycles = 0;
        long oldCycles   = 0;
        long fullCycles  = 0;
        for (Map.Entry<String, StwRow> e : stwRows.entrySet()) {
            String key = e.getKey();
            long invocations = e.getValue().invocations;
            switch (key) {
                case "ZGCYoungCollection": youngCycles += invocations; break;
                case "ZGCOldCollection":   oldCycles   += invocations; break;
                case "ZGCFullCollection":  fullCycles  += invocations; break;
                default: /* defensive — should not happen for ZGC logs */ break;
            }
        }

        out.addHeadline("Log duration",             logDur,                "s",  "decimal2");
        out.addHeadline("Young cycles",             (double) youngCycles,  "",   "int");
        out.addHeadline("Old cycles",               (double) oldCycles,    "",   "int");
        if (fullCycles > 0) {
            // Show only when present; generational ZGC should not run Full
            // collections in steady state, so a non-zero is a signal worth
            // putting on the headline rather than burying in a note.
            out.addHeadline("Full cycles",          (double) fullCycles,   "",   "int");
        }
        out.addHeadline("Total pause time",         totalPause,            "s",  "decimal2");
        out.addHeadline("Avg pause / cycle",        avgCycleSec * 1000.0,  "ms", "decimal2");
        out.addHeadline("Application throughput",   appThroughput,         "%",  "decimal3");
        out.addHeadline("Allocation stalls",        (double) zgcAllocationStallCount, "", "int");
    }

    private SummaryData.Table buildStwTable() {
        List<SummaryData.Column> columns = new ArrayList<>();
        columns.add(new SummaryData.Column("count",      "Count",      "",     "int",      "right"));
        columns.add(new SummaryData.Column("totalSec",   "Total",      "s",    "decimal2", "right"));
        columns.add(new SummaryData.Column("avgMs",      "Avg",        "ms",   "decimal1", "right"));
        columns.add(new SummaryData.Column("maxMs",      "Max",        "ms",   "decimal1", "right"));
        columns.add(new SummaryData.Column("pctPaused",  "% paused",   "%",    "decimal2", "right"));
        columns.add(new SummaryData.Column("intervalS",  "Interval",   "s",    "decimal2", "right"));
        columns.add(new SummaryData.Column("allocMb",    "Alloc",      "MB",   "int",      "right"));
        columns.add(new SummaryData.Column("recoveredMb","Recovered",  "MB",   "int",      "right"));

        double logDur = logDurationSec();
        List<SummaryData.Row> rows = new ArrayList<>(stwRows.size());
        for (Map.Entry<String, StwRow> e : stwRows.entrySet()) {
            StwRow r = e.getValue();
            Map<String, Double> cells = new LinkedHashMap<>();
            cells.put("count",       (double) r.invocations);
            cells.put("totalSec",    r.totalPauseSec);
            if (r.invocations > 0) {
                cells.put("avgMs", (r.totalPauseSec / r.invocations) * 1000.0);
                cells.put("maxMs", r.maxPauseSec * 1000.0);
            }
            if (logDur > 0.0) {
                cells.put("pctPaused", 100.0 * r.totalPauseSec / logDur);
            }
            if (r.intervalCount > 0) {
                cells.put("intervalS", r.sumIntervalsSec / r.intervalCount);
            }
            cells.put("allocMb",     r.totalBytesAllocatedMb);
            cells.put("recoveredMb", r.totalBytesRecoveredMb);
            rows.add(SummaryData.row(e.getKey(), r.label, columns, cells));
        }
        return new SummaryData.Table("stw-pauses", "Stop-the-world pauses", columns, rows);
    }

    private SummaryData.Table buildConcurrentTable() {
        List<SummaryData.Column> columns = new ArrayList<>();
        columns.add(new SummaryData.Column("count",   "Count",  "",  "int",      "right"));
        columns.add(new SummaryData.Column("wallSec", "Wall",   "s", "decimal2", "right"));
        columns.add(new SummaryData.Column("cpuSec",  "CPU",    "s", "decimal2", "right"));
        columns.add(new SummaryData.Column("avgSec",  "Avg",    "s", "decimal2", "right"));
        columns.add(new SummaryData.Column("maxSec",  "Max",    "s", "decimal2", "right"));
        columns.add(new SummaryData.Column("pctConcurrent", "% concurrent",
                                           "%", "decimal2", "right"));
        if (anyCmsSeen) {
            columns.add(new SummaryData.Column("aborted", "Aborted",
                                               "", "int", "right"));
        }

        double logDur = logDurationSec();
        List<SummaryData.Row> rows = new ArrayList<>(concurrentRows.size());
        for (Map.Entry<String, ConcurrentRow> e : concurrentRows.entrySet()) {
            ConcurrentRow r = e.getValue();
            Map<String, Double> cells = new LinkedHashMap<>();
            cells.put("count",   (double) r.invocations);
            cells.put("wallSec", r.totalWallSec);
            cells.put("cpuSec",  r.totalCpuSec);
            if (r.invocations > 0) {
                cells.put("avgSec", r.totalWallSec / r.invocations);
                cells.put("maxSec", r.maxWallSec);
            }
            if (logDur > 0.0) {
                cells.put("pctConcurrent", 100.0 * r.totalWallSec / logDur);
            }
            if (anyCmsSeen) {
                cells.put("aborted", (double) r.aborted);
            }
            rows.add(SummaryData.row(e.getKey(), r.label, columns, cells));
        }
        return new SummaryData.Table("concurrent-phases", "Concurrent phases", columns, rows);
    }

    private void populateNotes(SummaryData out) {
        out.addNote("Heap resized",               heapResizeCount);
        out.addNote("Back-to-back collections",   backToBackCount);
        if (anyG1Seen) {
            out.addNote("G1 initial-mark cycles", g1InitialMarkCount);
            out.addNote("To-space exhausted",     toSpaceExhaustedCount);
        }
        if (anyCmsSeen) {
            out.addNote("CMS concurrent aborts",  concurrentAborted);
        }
        if (anyZgcSeen) {
            out.addNote("Allocation stalls",      zgcAllocationStallCount);
        }
    }

    /**
     * Public counter used by the ZGC analytics group. Lives here rather
     * than in {@link com.kodewerk.jma.analytics.AnalyticsAggregation}
     * because the summary aggregator is the one already pattern-matching
     * on {@code GCCause.ALLOC_STALL}; duplicating the detection in two
     * places would invite drift.
     */
    public long getZgcAllocationStallCount() {
        return zgcAllocationStallCount;
    }

    @Override
    public boolean isEmpty() {
        return stwRows.isEmpty() && concurrentRows.isEmpty();
    }

    @Override
    public boolean hasWarning() {
        return false;
    }
}
