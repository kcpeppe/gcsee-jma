package com.kodewerk.jma.rest;

import com.kodewerk.jma.aggregation.JmaAggregation;
import com.kodewerk.jma.aggregation.allocation.AllocationPerCollectionAggregation;
import com.kodewerk.jma.aggregation.allocation.AllocationRateAggregation;
import com.kodewerk.jma.aggregation.concurrent.ConcurrentPhaseAggregation;
import com.kodewerk.jma.aggregation.cpu.CpuBreakoutAggregation;
import com.kodewerk.jma.aggregation.gctime.PercentTimeInGcAggregation;
import com.kodewerk.jma.aggregation.mmu.MmuAggregation;
import com.kodewerk.jma.aggregation.recovered.RecoveredAggregation;
import com.kodewerk.jma.aggregation.reference.ReferenceCountAggregation;
import com.kodewerk.jma.aggregation.reference.ReferenceTimeAggregation;
import com.kodewerk.jma.aggregation.safepoint.SafepointCauseAggregation;
import com.kodewerk.jma.aggregation.safepoint.SafepointDurationAggregation;
import com.kodewerk.jma.aggregation.cause.GCCauseAggregation;
import com.kodewerk.jma.aggregation.g1phase.G1PhaseAggregation;
import com.kodewerk.jma.aggregation.g1phase.G1PhasePercentAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1CollectionAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1CollectionPercentAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1OtherCollectionAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1OtherCollectionPercentAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1PostCollectionAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1PostCollectionPercentAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1PreCollectionAggregation;
import com.kodewerk.jma.aggregation.g1subphase.G1PreCollectionPercentAggregation;
import com.kodewerk.jma.aggregation.g1region.G1RegionsAfterAggregation;
import com.kodewerk.jma.aggregation.g1region.G1RegionsAfterPercentAggregation;
import com.kodewerk.jma.aggregation.g1region.G1RegionsBeforeAggregation;
import com.kodewerk.jma.aggregation.g1region.G1RegionsBeforePercentAggregation;
import com.kodewerk.jma.aggregation.heap.HeapOccupancyAfterPercentAggregation;
import com.kodewerk.jma.aggregation.heap.HeapOccupancyAggregation;
import com.kodewerk.jma.aggregation.heap.HeapOccupancyBeforeAggregation;
import com.kodewerk.jma.aggregation.heap.HeapOccupancyBeforePercentAggregation;
import com.kodewerk.jma.aggregation.metaspace.MetaspaceOccupancyAggregation;
import com.kodewerk.jma.aggregation.pause.PauseTimeAggregation;
import com.kodewerk.jma.aggregation.summary.SummaryAggregation;
import com.kodewerk.jma.analytics.AnalyticsAggregation;
import com.kodewerk.jma.aggregation.tenuring.TenuringByAgeAggregation;
import com.kodewerk.jma.aggregation.tenuring.TenuringSummaryAggregation;
import com.kodewerk.jma.aggregation.tenuring.TenuringThresholdAggregation;
import com.kodewerk.jma.aggregation.tenuring.TenuringVolumeAggregation;
import com.kodewerk.jma.session.Session;
import com.kodewerk.jma.session.SessionService;
import com.kodewerk.jma.view.View;
import com.kodewerk.jma.view.Views;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * REST surface for the GC-log session / views workflow.
 * <ul>
 *   <li>{@code POST /sessions} — body is a raw GC log
 *       ({@code application/octet-stream} or {@code text/plain}); optional
 *       {@code X-Filename} header for display. Returns the new session id
 *       and metadata.</li>
 *   <li>{@code GET /sessions/{id}/views/heap-occupancy} — the heap
 *       capacity + occupancy-after-collection scatter.</li>
 *   <li>{@code GET /sessions/{id}/views/metaspace-occupancy} — the
 *       metaspace counterpart of {@code heap-occupancy}: committed
 *       size line plus occupancy-after-collection coloured by category.</li>
 *   <li>{@code GET /sessions/{id}/views/pause-time} — stop-the-world
 *       pause durations coloured by collection category.</li>
 *   <li>{@code GET /sessions/{id}/views/allocation-rate} — derived
 *       application allocation rate between collections.</li>
 *   <li>{@code GET /sessions/{id}/views/summary} — tabular log summary
 *       (headline scalars, per-GC-type rows, rare-event notes).</li>
 *   <li>{@code GET /sessions/{id}/views/g1-phases} — stacked-bar breakout
 *       of each G1 pause into pre-evacuate / evacuate / post-evacuate /
 *       other (ms). Empty for non-G1 logs.</li>
 *   <li>{@code GET /sessions/{id}/views/g1-phase-percent} — same phase
 *       breakout rescaled per event so each bar sums to 100% (stacked
 *       area on a linear seconds axis). Empty for non-G1 logs.</li>
 *   <li>{@code GET /sessions/{id}/views/g1-regions-before} — stacked-bar
 *       count of regions per type (eden / survivor / old / humongous /
 *       archive) as they stood before each G1 pause. Empty for
 *       non-G1 logs.</li>
 *   <li>{@code GET /sessions/{id}/views/g1-regions-after} — same shape as
 *       {@code g1-regions-before} but using the post-collection counts.</li>
 *   <li>{@code GET /sessions/{id}/views/g1-regions-before-percent} —
 *       region mix entering each pause as a percentage of total regions
 *       (each stack sums to 100%).</li>
 *   <li>{@code GET /sessions/{id}/views/g1-regions-after-percent} —
 *       region mix after each pause, rescaled to 100%.</li>
 *   <li>{@code GET /sessions/{id}/views/tenuring-summary} — Censum-style
 *       tenuring distribution summary: headline scalars plus the
 *       per-age breakdown and threshold-distribution tables.</li>
 *   <li>{@code GET /sessions/{id}/views/tenuring-volume} — average
 *       bytes-at-age (KB) plotted as one scatter point per age.</li>
 *   <li>{@code GET /sessions/{id}/views/tenuring-threshold} — the
 *       calculated tenuring threshold per young pause, plotted over time.</li>
 *   <li>{@code GET /sessions/{id}/views/tenuring-over-time} — surviving
 *       bytes per age over time, one series per age.</li>
 *   <li>{@code GET /sessions/{id}/views/gc-cause} — categorical
 *       stacked-bar histogram of GC causes grouped by collection type
 *       (one bar per type, stack segments per cause).</li>
 *   <li>{@code GET /sessions/{id}/views/analytics} — derived analytic
 *       findings grouped by feature area, each tagged OK / CONCERNING /
 *       SIGNIFICANT with explanatory text and remediation suggestions.</li>
 *   <li>{@code DELETE /sessions/{id}} — drop a session and its temp log.</li>
 * </ul>
 * View endpoints return a {@link View} whose {@code status} field tells
 * the UI whether the log supported the analysis; the wrapped aggregation
 * carries the chart data directly.
 */
@Path("/sessions")
@Produces(MediaType.APPLICATION_JSON)
public class SessionsResource {

    @Inject
    SessionService sessions;

    public record SessionSummary(String id, String filename) {
        static SessionSummary from(Session s) {
            return new SessionSummary(s.id(), s.originalFilename());
        }
    }

    @POST
    @Consumes({MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN})
    public Response create(@HeaderParam("X-Filename") String filename,
                           InputStream body) throws IOException {
        Session created = sessions.createSession(filename, body);
        return Response.status(Response.Status.CREATED)
                       .entity(SessionSummary.from(created))
                       .build();
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        return sessions.delete(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("{id}/views/heap-occupancy")
    public Response heapOccupancy(@PathParam("id") String id) {
        return viewFor(id, HeapOccupancyAggregation.class,
                       "heap-occupancy", "Heap size and occupancy after collection");
    }

    @GET
    @Path("{id}/views/heap-occupancy-before")
    public Response heapOccupancyBefore(@PathParam("id") String id) {
        return viewFor(id, HeapOccupancyBeforeAggregation.class,
                       "heap-occupancy-before",
                       "Heap size and occupancy before collection");
    }

    @GET
    @Path("{id}/views/heap-occupancy-before-percent")
    public Response heapOccupancyBeforePercent(@PathParam("id") String id) {
        return viewFor(id, HeapOccupancyBeforePercentAggregation.class,
                       "heap-occupancy-before-percent",
                       "% heap occupancy before collection");
    }

    @GET
    @Path("{id}/views/heap-occupancy-after-percent")
    public Response heapOccupancyAfterPercent(@PathParam("id") String id) {
        return viewFor(id, HeapOccupancyAfterPercentAggregation.class,
                       "heap-occupancy-after-percent",
                       "% heap occupancy after collection");
    }

    @GET
    @Path("{id}/views/metaspace-occupancy")
    public Response metaspaceOccupancy(@PathParam("id") String id) {
        return viewFor(id, MetaspaceOccupancyAggregation.class,
                       "metaspace-occupancy", "Metaspace size and occupancy after collection");
    }

    @GET
    @Path("{id}/views/pause-time")
    public Response pauseTime(@PathParam("id") String id) {
        return viewFor(id, PauseTimeAggregation.class,
                       "pause-time", "Stop-the-world pause time");
    }

    @GET
    @Path("{id}/views/percent-time-in-gc")
    public Response percentTimeInGc(@PathParam("id") String id) {
        return viewFor(id, PercentTimeInGcAggregation.class,
                       "percent-time-in-gc", "% time in GC over time");
    }

    @GET
    @Path("{id}/views/mmu")
    public Response mmu(@PathParam("id") String id) {
        return viewFor(id, MmuAggregation.class,
                       "mmu", "Minimum mutator utilization");
    }

    @GET
    @Path("{id}/views/concurrent-phase-duration")
    public Response concurrentPhaseDuration(@PathParam("id") String id) {
        return viewFor(id, ConcurrentPhaseAggregation.class,
                       "concurrent-phase-duration",
                       "Concurrent phase durations");
    }

    @GET
    @Path("{id}/views/cpu-breakouts")
    public Response cpuBreakouts(@PathParam("id") String id) {
        return viewFor(id, CpuBreakoutAggregation.class,
                       "cpu-breakouts", "CPU breakouts");
    }

    @GET
    @Path("{id}/views/allocation-rate")
    public Response allocationRate(@PathParam("id") String id) {
        return viewFor(id, AllocationRateAggregation.class,
                       "allocation-rate", "Application allocation rate");
    }

    @GET
    @Path("{id}/views/allocation-per-collection")
    public Response allocationPerCollection(@PathParam("id") String id) {
        return viewFor(id, AllocationPerCollectionAggregation.class,
                       "allocation-per-collection",
                       "Allocated between collections");
    }

    @GET
    @Path("{id}/views/recovered")
    public Response recovered(@PathParam("id") String id) {
        return viewFor(id, RecoveredAggregation.class,
                       "recovered", "Recovered by each collection");
    }

    @GET
    @Path("{id}/views/summary")
    public Response summary(@PathParam("id") String id) {
        return viewFor(id, SummaryAggregation.class,
                       "summary", "Log summary");
    }

    @GET
    @Path("{id}/views/g1-phases")
    public Response g1Phases(@PathParam("id") String id) {
        return viewFor(id, G1PhaseAggregation.class,
                       "g1-phases", "G1 phase breakout");
    }

    @GET
    @Path("{id}/views/g1-phase-percent")
    public Response g1PhasePercent(@PathParam("id") String id) {
        return viewFor(id, G1PhasePercentAggregation.class,
                       "g1-phase-percent", "G1 phase breakout (% of pause)");
    }

    @GET
    @Path("{id}/views/g1-pre-collection")
    public Response g1PreCollection(@PathParam("id") String id) {
        return viewFor(id, G1PreCollectionAggregation.class,
                       "g1-pre-collection",
                       "G1 Pre-Collection sub-phases");
    }

    @GET
    @Path("{id}/views/g1-pre-collection-percent")
    public Response g1PreCollectionPercent(@PathParam("id") String id) {
        return viewFor(id, G1PreCollectionPercentAggregation.class,
                       "g1-pre-collection-percent",
                       "G1 Pre-Collection sub-phases (% of phase)");
    }

    @GET
    @Path("{id}/views/g1-collection")
    public Response g1Collection(@PathParam("id") String id) {
        return viewFor(id, G1CollectionAggregation.class,
                       "g1-collection",
                       "G1 Collection sub-phases");
    }

    @GET
    @Path("{id}/views/g1-collection-percent")
    public Response g1CollectionPercent(@PathParam("id") String id) {
        return viewFor(id, G1CollectionPercentAggregation.class,
                       "g1-collection-percent",
                       "G1 Collection sub-phases (% of phase)");
    }

    @GET
    @Path("{id}/views/g1-post-collection")
    public Response g1PostCollection(@PathParam("id") String id) {
        return viewFor(id, G1PostCollectionAggregation.class,
                       "g1-post-collection",
                       "G1 Post-Collection sub-phases");
    }

    @GET
    @Path("{id}/views/g1-post-collection-percent")
    public Response g1PostCollectionPercent(@PathParam("id") String id) {
        return viewFor(id, G1PostCollectionPercentAggregation.class,
                       "g1-post-collection-percent",
                       "G1 Post-Collection sub-phases (% of phase)");
    }

    @GET
    @Path("{id}/views/g1-other-collection")
    public Response g1OtherCollection(@PathParam("id") String id) {
        return viewFor(id, G1OtherCollectionAggregation.class,
                       "g1-other-collection",
                       "G1 Other sub-phases");
    }

    @GET
    @Path("{id}/views/g1-other-collection-percent")
    public Response g1OtherCollectionPercent(@PathParam("id") String id) {
        return viewFor(id, G1OtherCollectionPercentAggregation.class,
                       "g1-other-collection-percent",
                       "G1 Other sub-phases (% of phase)");
    }

    @GET
    @Path("{id}/views/g1-regions-before")
    public Response g1RegionsBefore(@PathParam("id") String id) {
        return viewFor(id, G1RegionsBeforeAggregation.class,
                       "g1-regions-before", "G1 regions before collection");
    }

    @GET
    @Path("{id}/views/g1-regions-after")
    public Response g1RegionsAfter(@PathParam("id") String id) {
        return viewFor(id, G1RegionsAfterAggregation.class,
                       "g1-regions-after", "G1 regions after collection");
    }

    @GET
    @Path("{id}/views/g1-regions-before-percent")
    public Response g1RegionsBeforePercent(@PathParam("id") String id) {
        return viewFor(id, G1RegionsBeforePercentAggregation.class,
                       "g1-regions-before-percent",
                       "G1 regions before collection (% of total)");
    }

    @GET
    @Path("{id}/views/g1-regions-after-percent")
    public Response g1RegionsAfterPercent(@PathParam("id") String id) {
        return viewFor(id, G1RegionsAfterPercentAggregation.class,
                       "g1-regions-after-percent",
                       "G1 regions after collection (% of total)");
    }

    @GET
    @Path("{id}/views/tenuring-summary")
    public Response tenuringSummary(@PathParam("id") String id) {
        return viewFor(id, TenuringSummaryAggregation.class,
                       "tenuring-summary", "Tenuring distribution summary");
    }

    @GET
    @Path("{id}/views/tenuring-volume")
    public Response tenuringVolume(@PathParam("id") String id) {
        return viewFor(id, TenuringVolumeAggregation.class,
                       "tenuring-volume", "Tenuring distribution");
    }

    @GET
    @Path("{id}/views/tenuring-threshold")
    public Response tenuringThreshold(@PathParam("id") String id) {
        return viewFor(id, TenuringThresholdAggregation.class,
                       "tenuring-threshold", "Calculated tenuring thresholds");
    }

    @GET
    @Path("{id}/views/tenuring-over-time")
    public Response tenuringOverTime(@PathParam("id") String id) {
        return viewFor(id, TenuringByAgeAggregation.class,
                       "tenuring-over-time", "Tenuring over time");
    }

    @GET
    @Path("{id}/views/gc-cause")
    public Response gcCause(@PathParam("id") String id) {
        return viewFor(id, GCCauseAggregation.class,
                       "gc-cause", "GC cause by collection type");
    }

    @GET
    @Path("{id}/views/reference-count")
    public Response referenceCount(@PathParam("id") String id) {
        return viewFor(id, ReferenceCountAggregation.class,
                       "reference-count", "Reference processing count");
    }

    @GET
    @Path("{id}/views/reference-time")
    public Response referenceTime(@PathParam("id") String id) {
        return viewFor(id, ReferenceTimeAggregation.class,
                       "reference-time", "Reference processing time");
    }

    @GET
    @Path("{id}/views/safepoint-duration")
    public Response safepointDuration(@PathParam("id") String id) {
        return viewFor(id, SafepointDurationAggregation.class,
                       "safepoint-duration", "Safepoint duration");
    }

    @GET
    @Path("{id}/views/safepoint-cause")
    public Response safepointCause(@PathParam("id") String id) {
        return viewFor(id, SafepointCauseAggregation.class,
                       "safepoint-cause", "Safepoint cause counts");
    }

    @GET
    @Path("{id}/views/analytics")
    public Response analytics(@PathParam("id") String id) {
        return viewFor(id, AnalyticsAggregation.class,
                       "analytics", "Analytics");
    }

    private <A extends JmaAggregation> Response viewFor(String sessionId,
                                                        Class<A> aggregationType,
                                                        String viewId,
                                                        String title) {
        return sessions.get(sessionId)
                .map(s -> Response.ok(Views.of(viewId, title,
                                               sessions.getAggregation(s, aggregationType))).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
