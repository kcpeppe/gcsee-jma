package com.kodewerk.jma.chart;

/**
 * Visual grouping of a data point on a chart. Exists purely so every chart
 * paints the same kind of event with the same colour. Not to be confused
 * with GCSee's {@code GarbageCollectionTypes} enum, which enumerates log
 * grammar terms — this enum is the coarser UI rollup used for colouring.
 * <p>
 * Mapping from a GCSee event subtype to a {@link GCCategory} lives in
 * {@link GCCategoryMapper}.
 */
public enum GCCategory {

    /** The committed (or max) heap size line — a capacity measurement. */
    HEAP_CAPACITY,

    /**
     * The committed (or max) metaspace size line — capacity measurement
     * on the metaspace-occupancy chart. Distinct from {@link #HEAP_CAPACITY}
     * so legends and series labels read sensibly per chart, even though
     * both are conceptually "the capacity line".
     */
    METASPACE_CAPACITY,

    /** Young / minor collection (ParNew, G1 young, Parallel Scavenge, etc.). */
    YOUNG,

    /** G1 young pause that also initiates a concurrent marking cycle. */
    YOUNG_INITIAL_MARK,

    /** G1 mixed collection — young regions plus some old regions. */
    MIXED,

    /** Tenured / old-generation pause (e.g. CMS initial-mark, CMS remark). */
    TENURED,

    /** Full / stop-the-world whole-heap collection. */
    FULL,

    /** Concurrent-phase event (typically not stop-the-world). */
    CONCURRENT,

    /**
     * Derived metric: bytes allocated per unit time between consecutive
     * collections. Not a collection type itself — it lives here because
     * every scatter series needs a category for colouring.
     */
    ALLOCATION_RATE;
}
