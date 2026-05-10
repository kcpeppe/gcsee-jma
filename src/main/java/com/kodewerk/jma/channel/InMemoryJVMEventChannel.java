package com.kodewerk.jma.channel;

import com.kodewerk.gcsee.event.jvm.JVMEvent;
import com.kodewerk.gcsee.message.*;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryJVMEventChannel
    //    extends InMemoryChannel
        implements JVMEventChannel {

    private static final Logger LOG = Logger.getLogger(com.kodewerk.jma.channel.InMemoryChannel.class);

    private final ConcurrentMap<ChannelName, List<JVMEventChannelListener>> byChannel = new ConcurrentHashMap<>();
    private volatile Throwable firstListenerFailure;
    private volatile int listenerFailureCount;
    private volatile int closeCallCount;

    @Override
    public void registerListener(JVMEventChannelListener listener) {
        byChannel.computeIfAbsent(listener.channel(), k -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    @Override
    public void publish(ChannelName channel, JVMEvent message) {
        List<JVMEventChannelListener> listeners = byChannel.get(channel);
        if (listeners == null) return;
        for (ChannelListener<JVMEvent> listener : listeners) {
            try {
                System.out.println("----------------------------------------");
                System.out.println(message.toString());
                listener.receive(message);
            } catch (Throwable t) {
                listenerFailureCount++;
                if (firstListenerFailure == null) {
                    firstListenerFailure = t;
                    LOG.debugf(t, "Listener %s threw on channel %s while receiving %s",
                            listener.getClass().getName(), channel,
                            message == null ? "null" : message.getClass().getName());
                }
            }
        }
    }

    @Override
    public void close() {
        closeCallCount++;
        if (LOG.isDebugEnabled()) {
            LOG.debugf("close() #%d on %s - %d listener bucket(s) retained", closeCallCount, getClass().getSimpleName(), Integer.valueOf(byChannel.size()));
        }
    }

    /**
     * Total number of listener exceptions caught during the lifetime of this channel.
     */
    public int getListenerFailureCount() {
        return listenerFailureCount;
    }

    /**
     * First exception caught (if any), for crash diagnostics.
     */
    public Throwable getFirstListenerFailure() {
        return firstListenerFailure;
    }

    /**
     * How many times GCSee invoked {@code close()} during this channel's lifetime.
     */
    public int getCloseCallCount() {
        return closeCallCount;

    }
}
