package com.kodewerk.jma.chart;

import com.kodewerk.gcsee.event.g1gc.G1Cleanup;
import com.kodewerk.gcsee.event.g1gc.G1FullGC;
import com.kodewerk.gcsee.event.g1gc.G1Mixed;
import com.kodewerk.gcsee.event.g1gc.G1Remark;
import com.kodewerk.gcsee.event.g1gc.G1Young;
import com.kodewerk.gcsee.event.g1gc.G1YoungInitialMark;
import com.kodewerk.gcsee.event.generational.CMSRemark;
import com.kodewerk.gcsee.event.generational.ConcurrentModeFailure;
import com.kodewerk.gcsee.event.generational.ConcurrentModeInterrupted;
import com.kodewerk.gcsee.event.generational.FullGC;
import com.kodewerk.gcsee.event.generational.GenerationalGCPauseEvent;
import com.kodewerk.gcsee.event.generational.InitialMark;
import com.kodewerk.gcsee.event.generational.PSFullGC;
import com.kodewerk.gcsee.event.generational.SystemGC;
import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.event.zgc.ZGCFullCollection;
import com.kodewerk.gcsee.event.zgc.ZGCOldCollection;
import com.kodewerk.gcsee.event.zgc.ZGCYoungCollection;

/**
 * Maps a GCSee {@link JVMEvent} to a {@link GCCategory} for chart colouring.
 * Returns {@code null} if the event does not belong on a GC-categorised view
 * (e.g. concurrent-phase events, safepoints, non-GC JVM records).
 * <p>
 * Subtype order matters: G1 subtypes that extend {@code G1Young} (namely
 * {@link G1Mixed} and {@link G1YoungInitialMark}) are checked first, so that
 * they are not swallowed by the {@code G1Young} catch-all. Generational
 * failure modes (concurrent-mode failure/interrupted) are classified as
 * {@code FULL} because they escalate into a full collection.
 */
public final class GCCategoryMapper {

    private GCCategoryMapper() {}

    public static GCCategory categoryOf(JVMEvent event) {
        if (event == null) return null;

        // ---- G1 family (most specific first — Mixed and InitialMark both extend G1Young)
        if (event instanceof G1Mixed)               return GCCategory.MIXED;
        if (event instanceof G1YoungInitialMark)    return GCCategory.YOUNG_INITIAL_MARK;
        if (event instanceof G1Young)               return GCCategory.YOUNG;
        if (event instanceof G1FullGC)              return GCCategory.FULL;  // covers G1SystemGC, G1FullGCNES
        if (event instanceof G1Remark)              return GCCategory.TENURED;
        if (event instanceof G1Cleanup)             return GCCategory.TENURED;

        // ---- Generational family (failure-mode subclasses before the catch-all)
        if (event instanceof ConcurrentModeFailure)     return GCCategory.FULL;
        if (event instanceof ConcurrentModeInterrupted) return GCCategory.FULL;
        if (event instanceof FullGC)                    return GCCategory.FULL;
        if (event instanceof PSFullGC)                  return GCCategory.FULL;
        if (event instanceof SystemGC)                  return GCCategory.FULL;
        if (event instanceof InitialMark)               return GCCategory.TENURED;
        if (event instanceof CMSRemark)                 return GCCategory.TENURED;
        // Every other GenerationalGCPauseEvent subclass (DefNew, ParNew,
        // ParNewPromotionFailed, PSYoungGen, YoungGC) is a young collection.
        if (event instanceof GenerationalGCPauseEvent)  return GCCategory.YOUNG;

        // ---- ZGC family
        if (event instanceof ZGCFullCollection)     return GCCategory.FULL;
        if (event instanceof ZGCOldCollection)      return GCCategory.TENURED;
        if (event instanceof ZGCYoungCollection)    return GCCategory.YOUNG;

        // Shenandoah is stub-grade in GCSee 0.1.2 — classify cycles as YOUNG
        // for now; heap-occupancy view will skip them because the cycle has
        // no occupancy accessor.
        return null;
    }
}
