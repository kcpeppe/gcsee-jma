package com.kodewerk.jma.session;

import com.kodewerk.gcsee.jvm.JavaVirtualMachine;

import java.nio.file.Path;
import java.time.Instant;

/**
 * One uploaded GC log plus the {@link JavaVirtualMachine} produced when
 * GCSee analysed it. Aggregations are pulled off the JVM on demand — they
 * are cheap to hold and we want every view of the same log to be
 * consistent with every other view.
 * <p>
 * Sessions are immutable after the analysis step: the REST layer either
 * creates a new session or deletes an existing one, never mutates in place.
 */
public record Session(String id,
                      String originalFilename,
                      Path   logPath,
                      JavaVirtualMachine jvm,
                      Instant createdAt) {}
