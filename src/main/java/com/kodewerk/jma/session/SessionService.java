package com.kodewerk.jma.session;

import com.kodewerk.gcsee.GCSee;
import com.kodewerk.gcsee.aggregator.Aggregation;
import com.kodewerk.gcsee.io.SingleGCLogFile;
import com.kodewerk.gcsee.jvm.JavaVirtualMachine;
import org.jboss.logging.Logger;
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
import com.kodewerk.jma.channel.InMemoryDataSourceChannel;
import com.kodewerk.jma.channel.InMemoryJVMEventChannel;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caches a {@link Session} per uploaded GC log. A session is created by
 * streaming the upload into a file in the configured upload directory and
 * running {@link GCSee#analyze}; the resulting {@link JavaVirtualMachine}
 * is cached for subsequent view lookups.
 * <p>
 * The list of aggregations registered on every GCSee run is fixed here —
 * this is the project's "view catalogue". Each new view only needs to be
 * added once in {@link #registerAggregations}.
 */
@ApplicationScoped
public class SessionService {

    private static final Logger LOG = Logger.getLogger(SessionService.class);

    @ConfigProperty(name = "gcsee-jma.upload-dir")
    String uploadDir;

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(Path.of(uploadDir));
    }

    /**
     * Stream the upload to disk, run GCSee, and cache the resulting JVM.
     *
     * @param originalFilename optional display name for the upload
     * @param input            request body; caller retains ownership and closes it
     * @return a freshly-created session
     */
    public Session createSession(String originalFilename, InputStream input) throws IOException {
        String id = UUID.randomUUID().toString();
        Path target = Path.of(uploadDir, id + ".log");
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);

        GCSee gcsee = new GCSee();
        // Supply synchronous in-memory channels so GCSee never falls back to
        // its gcsee-vertx default (Vert.x 5, incompatible with Quarkus 3.34).
        InMemoryJVMEventChannel eventChannel = new InMemoryJVMEventChannel();
        InMemoryDataSourceChannel dataChannel = new InMemoryDataSourceChannel();
        gcsee.loadJVMEventChannel(eventChannel);
        gcsee.loadDataSourceChannel(dataChannel);
        registerAggregations(gcsee);

        JavaVirtualMachine jvm;
        try {
            jvm = gcsee.analyze(new SingleGCLogFile(target));
        } catch (Throwable t) {
            // analyze() shouldn't throw on a malformed log line, but if it
            // does we want the stack trace in the server log instead of a
            // mysteriously short chart.
            LOG.errorf(t, "GCSee.analyze failed for %s", target);
            throw new IOException("Failed to analyze " + target, t);
        }
        if (eventChannel.getListenerFailureCount() > 0) {
            LOG.warnf("Event channel swallowed %d listener exception(s) during analyze of %s; first was %s",
                      eventChannel.getListenerFailureCount(), target,
                      eventChannel.getFirstListenerFailure());
        }
        if (dataChannel.getListenerFailureCount() > 0) {
            LOG.warnf("DataSource channel swallowed %d listener exception(s) during analyze of %s; first was %s",
                      dataChannel.getListenerFailureCount(), target,
                      dataChannel.getFirstListenerFailure());
        }
        // Diagnostic: how many times did analyze() ask each channel to
        // close itself? If close() fires on the data-source channel before
        // the event-source channel has finished (or fires on the event
        // channel mid-stream), our no-op close() has just kept the
        // pipeline alive past it — but it's worth knowing the count for
        // future GCSee version upgrades.
        LOG.infof("analyze done for %s — close() invocations: data=%d, event=%d",
                  target, dataChannel.getCloseCallCount(), eventChannel.getCloseCallCount());

        Session session = new Session(id, originalFilename, target, jvm, Instant.now());
        sessions.put(id, session);
        return session;
    }

    public Optional<Session> get(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public boolean delete(String id) {
        Session removed = sessions.remove(id);
        if (removed == null) return false;
        try {
            Files.deleteIfExists(removed.logPath());
        } catch (IOException ignore) {
            // tmp cleanup is best-effort
        }
        return true;
    }

    /**
     * Pull a specific aggregation off a session's JVM. Returns {@code null}
     * if the aggregation was not produced for this log (GCSee decides based
     * on which event sources appeared in the log).
     */
    public <A extends JmaAggregation> A getAggregation(Session session, Class<A> type) {
        Optional<A> opt = session.jvm().getAggregation(type);
        return opt.orElse(null);
    }

    /** Central list of aggregations the UI knows how to render. */
    private void registerAggregations(GCSee gcsee) {
        for (Aggregation a : defaultAggregations()) {
            gcsee.loadAggregation(a);
        }
    }

    private Aggregation[] defaultAggregations() {
        return new Aggregation[] {
                new HeapOccupancyAggregation(),
                new HeapOccupancyBeforeAggregation(),
                new HeapOccupancyBeforePercentAggregation(),
                new HeapOccupancyAfterPercentAggregation(),
                new MetaspaceOccupancyAggregation(),
                new PauseTimeAggregation(),
                new PercentTimeInGcAggregation(),
                new MmuAggregation(),
                new ConcurrentPhaseAggregation(),
                new CpuBreakoutAggregation(),
                new AllocationRateAggregation(),
                new AllocationPerCollectionAggregation(),
                new RecoveredAggregation(),
                new SummaryAggregation(),
                new G1PhaseAggregation(),
                new G1PhasePercentAggregation(),
                new G1PreCollectionAggregation(),
                new G1PreCollectionPercentAggregation(),
                new G1CollectionAggregation(),
                new G1CollectionPercentAggregation(),
                new G1PostCollectionAggregation(),
                new G1PostCollectionPercentAggregation(),
                new G1OtherCollectionAggregation(),
                new G1OtherCollectionPercentAggregation(),
                new G1RegionsBeforeAggregation(),
                new G1RegionsAfterAggregation(),
                new G1RegionsBeforePercentAggregation(),
                new G1RegionsAfterPercentAggregation(),
                new TenuringSummaryAggregation(),
                new TenuringVolumeAggregation(),
                new TenuringThresholdAggregation(),
                new TenuringByAgeAggregation(),
                new GCCauseAggregation(),
                new ReferenceCountAggregation(),
                new ReferenceTimeAggregation(),
                new SafepointDurationAggregation(),
                new SafepointCauseAggregation(),
                new AnalyticsAggregation()
        };
    }
}
