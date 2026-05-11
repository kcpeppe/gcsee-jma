package com.kodewerk.jma.chart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Presentation-layer data holder for a log-summary view. Pairs with
 * {@link ScatterData} but carries tabular rather than time-series data.
 * <p>
 * A summary consists of a title, a set of headline scalars (log-level
 * aggregates shown above the tables), one or more tables (each self-
 * describing via its own column list), and a list of notes for rare-event
 * counters. Tables and columns are deliberately per-summary rather than
 * per-collector — the aggregator emits whatever shape fits the log it
 * saw, and the frontend iterates {@code tables[]} without special-casing
 * collector families.
 * <p>
 * Cells inside a row are stored sparsely as a {@code Map<columnKey, Double>}:
 * columns that don't apply to a given row (e.g. concurrent-time on a Parallel
 * row) simply have no entry, and the frontend renders an em-dash.
 */
public final class SummaryData {

    /** A single log-level scalar shown in the summary header. */
    public record Scalar(String label, double value, String unit, String format) {}

    /** Column definition for a {@link Table}. */
    public record Column(String key, String label, String unit, String format, String align) {}

    /** One row of a {@link Table}; cells are keyed by column key, sparsely populated. */
    public record Row(String key, String label, Map<String, Double> cells) {}

    /** Rare-event counter (e.g. "Back-to-back collections: 7"). */
    public record Note(String label, long count) {}

    /**
     * A self-describing table — carries its own column list so the frontend
     * needs no collector-family knowledge to render it.
     */
    public static final class Table {
        private final String key;
        private final String title;
        private final List<Column> columns;
        private final List<Row> rows;

        public Table(String key, String title, List<Column> columns, List<Row> rows) {
            this.key = key;
            this.title = title;
            this.columns = columns;
            this.rows = rows;
        }

        public String getKey()            { return key; }
        public String getTitle()          { return title; }
        public List<Column> getColumns()  { return columns; }
        public List<Row> getRows()        { return rows; }
    }

    private final String title;
    private final List<Scalar> headline = new ArrayList<>();
    private final List<Table>  tables   = new ArrayList<>();
    private final List<Note>   notes    = new ArrayList<>();

    /**
     * Detected collector family for the log this summary describes —
     * one of {@code "g1"}, {@code "cms"}, {@code "parallel"},
     * {@code "serial"}, {@code "zgc"}, or {@code "unknown"}. Drives
     * collector-specific filtering on the frontend (nav rail
     * entries, hidden charts, etc.). Defaults to {@code "unknown"}
     * until the aggregation sets it.
     */
    private String collector = "unknown";

    public SummaryData(String title) {
        this.title = title;
    }

    public void setCollector(String collector) {
        if (collector != null) this.collector = collector;
    }
    public String getCollector() { return collector; }

    public void addHeadline(String label, double value, String unit, String format) {
        headline.add(new Scalar(label, value, unit, format));
    }

    public void addTable(Table table) {
        tables.add(table);
    }

    public void addNote(String label, long count) {
        notes.add(new Note(label, count));
    }

    public String getTitle()           { return title; }
    public List<Scalar> getHeadline()  { return headline; }
    public List<Table> getTables()     { return tables; }
    public List<Note> getNotes()       { return notes; }

    public boolean isEmpty() {
        if (tables.isEmpty()) return true;
        for (Table t : tables) {
            if (!t.getRows().isEmpty()) return false;
        }
        return true;
    }

    /**
     * Convenience helper for building a row with a known column list —
     * preserves column order for renderers that might iterate on the
     * cell map. Unknown columns are dropped.
     */
    public static Row row(String key, String label, List<Column> columns,
                          Map<String, Double> cells) {
        Map<String, Double> ordered = new LinkedHashMap<>();
        for (Column c : columns) {
            Double v = cells.get(c.key());
            if (v != null) ordered.put(c.key(), v);
        }
        return new Row(key, label, ordered);
    }
}
