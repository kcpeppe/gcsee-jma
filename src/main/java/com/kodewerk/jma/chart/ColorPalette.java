package com.kodewerk.jma.chart;

import java.util.EnumMap;
import java.util.Map;

/**
 * Canonical colour for each {@link GCCategory}. One place to change the
 * scheme; every chart in the UI picks up the change automatically.
 * <p>
 * Defaults (intentionally dark for legibility on a white background):
 * <ul>
 *   <li>HEAP_CAPACITY      — black</li>
 *   <li>METASPACE_CAPACITY — black (renders on its own chart)</li>
 *   <li>YOUNG              — dark green</li>
 *   <li>YOUNG_INITIAL_MARK — dark magenta</li>
 *   <li>MIXED              — dark goldenrod</li>
 *   <li>TENURED            — dark blue</li>
 *   <li>FULL               — dark blue (same family as TENURED)</li>
 *   <li>CONCURRENT         — dim grey</li>
 *   <li>ALLOCATION_RATE    — firebrick (derived metric, distinct from every collection)</li>
 * </ul>
 */
public final class ColorPalette {

    private static final Map<GCCategory, String> COLORS;
    static {
        Map<GCCategory, String> m = new EnumMap<>(GCCategory.class);
        m.put(GCCategory.HEAP_CAPACITY,      "#000000");
        m.put(GCCategory.METASPACE_CAPACITY, "#000000");
        m.put(GCCategory.YOUNG,              "#006400");
        m.put(GCCategory.YOUNG_INITIAL_MARK, "#8B008B");
        m.put(GCCategory.MIXED,              "#B8860B");
        m.put(GCCategory.TENURED,            "#00008B");
        m.put(GCCategory.FULL,               "#00008B");
        m.put(GCCategory.CONCURRENT,         "#696969");
        m.put(GCCategory.ALLOCATION_RATE,    "#B22222");
        COLORS = Map.copyOf(m);
    }

    private ColorPalette() {}

    public static String of(GCCategory category) {
        String c = COLORS.get(category);
        if (c == null) {
            throw new IllegalStateException("No colour defined for " + category);
        }
        return c;
    }
}
