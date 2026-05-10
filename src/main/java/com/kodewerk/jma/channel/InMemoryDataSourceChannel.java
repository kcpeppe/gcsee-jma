package com.kodewerk.jma.channel;

import com.kodewerk.gcsee.message.DataSourceChannel;
import com.kodewerk.gcsee.message.DataSourceParser;

/**
 * In-memory binding of {@link DataSourceChannel} — the channel on which
 * raw GC log lines are published for the parsers to consume.
 */
public final class InMemoryDataSourceChannel
        extends InMemoryChannel<String, DataSourceParser>
        implements DataSourceChannel {
}
