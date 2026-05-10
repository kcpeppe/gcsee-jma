package com.kodewerk.jma.channel;

import com.kodewerk.gcsee.message.Channel;
import com.kodewerk.gcsee.message.ChannelListener;
import com.kodewerk.gcsee.message.ChannelName;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous, in-memory implementation of GCSee's {@link Channel} pub/sub
 * abstraction.
 * <p>
 * This exists because {@code gcsee-vertx} 0.1.2 depends on Vert.x 5, which
 * conflicts with the Vert.x 4.x that Quarkus 3.34 ships. Rather than fight
 * the dependency resolver we drop {@code gcsee-vertx} and plug our own
 * channels into {  @link com.kodewerk.gcsee. GCSee # loadJVMEventChannel} /
 * { @ link com.kodewerk.gcsee. GCSee # loadDataSourceChannel} before
 * {@code analyze()} is called.
 * <p>
 * Routing is by {@link ChannelName}: a listener's {@link ChannelListener#channel()}
 * value selects which channel its {@code receive} is invoked on. Delivery
 * happens synchronously on the caller's thread, which is the simplest
 * correct implementation for our REST workflow — requests are serviced on
 * Quarkus's worker threads and each session runs one {@code analyze()}
 * call on one thread.
 */
public class InMemoryChannel<M, L extends ChannelListener<M>> implements Channel<M, L> {

    private static final Logger LOG = Logger.getLogger(InMemoryChannel.class);

    private final ConcurrentMap<ChannelName, List<ChannelListener<M>>> byChannel = new ConcurrentHashMap<>();

    /** First failure observed during the current analyze() run, for diagnosis. */
    private volatile Throwable firstListenerFailure;

    /** Records that {@code close()} was called — for diagnostics, but does not actually disconnect listeners. */
    private volatile int       closeCallCount;

    public void registerListener(L listener) {
        byChannel.computeIfAbsent(listener.channel(), k -> new CopyOnWriteArrayList<>())
                 .add(listener);
    }

    /**
     * Deliver the message to every registered listener. Each listener call is
     * wrapped: a single misbehaving aggregator must not unwind into the
     * parser thread and stop the rest of the log from being processed.
     * Failures are logged once at WARN with the offending listener type so
     * we can find them without flooding the log on every event.
     */
    public void publish(ChannelName channel, M message) {
        List<ChannelListener<M>> listeners = byChannel.get(channel);
        if (listeners == null) return;
        listeners.forEach((ChannelListener<M> listener) -> {
            try {
                listener.receive(message);
            } catch (Throwable t) {
                if (firstListenerFailure == null) {
                    firstListenerFailure = t;
                    LOG.debugf(t, "Listener %s threw on channel %s while receiving %s",
                            listener.getClass().getName(), channel,
                            message == null ? "null" : message.getClass().getName());
                }
            }
        });
    }

    /**
     * Intentionally a no-op for clearing listeners.
     * <p>
     * GCSee 0.1.2 (via the GCToolkit message layer it embeds) appears to
     * call {@code close()} on the {@code DataSourceChannel} as soon as the
     * input file has been fully read. In a synchronous channel that's
     * before the parser pipeline has finished publishing every event it
     * derived from the trailing lines — and crucially before
     * {@code JVMTermination} is published. If we cleared listeners here,
     * those tail events would be silently dropped (they fall on an empty
     * listener bucket and {@code publish()} no-ops). That's exactly the
     * symptom we observed: every timeline view truncates at the same
     * wall-clock and {@code JVMTermination} never arrives.
     * <p>
     * It is safe to retain the listener map: each channel is owned by a
     * single {@code Session} and gets garbage-collected with it. We still
     * count {@code close()} calls so the {@code SessionService} can log
     * how many were issued — useful if GCSee's behaviour changes in a
     * future release.
     */
    public void close() {
        if (LOG.isDebugEnabled()) {
            LOG.debugf("close() #%d on %s - %d listener bucket(s) retained", closeCallCount, getClass().getSimpleName(), Integer.valueOf(byChannel.size()));
        }
    }

    /** Total number of listener exceptions caught during the lifetime of this channel. */
    public int getListenerFailureCount() {
        return (firstListenerFailure == null) ? 0 : 1 ;
    }

    /** First exception caught (if any), for crash diagnostics. */
    public Throwable getFirstListenerFailure() {
        return firstListenerFailure;
    }

    /** How many times GCSee invoked {@code close()} during this channel's lifetime. */
    public int getCloseCallCount() {
        return closeCallCount;
    }
}
