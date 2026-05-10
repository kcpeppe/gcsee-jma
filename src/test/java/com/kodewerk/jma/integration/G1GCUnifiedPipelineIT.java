package com.kodewerk.jma.integration;


import com.kodewerk.gcsee.GCSee;
import com.kodewerk.gcsee.io.SingleGCLogFile;
import com.kodewerk.jma.channel.InMemoryDataSourceChannel;
import com.kodewerk.jma.channel.InMemoryJVMEventChannel;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class G1GCUnifiedPipelineIT {

    @Test
    void shouldCountAllG1YoungEventsFromLog() throws Exception {

        // Arrange
        GCSee gcsee = new GCSee();

        InMemoryJVMEventChannel eventChannel = new InMemoryJVMEventChannel();
        InMemoryDataSourceChannel dataChannel = new InMemoryDataSourceChannel();

        gcsee.loadJVMEventChannel(eventChannel);
        gcsee.loadDataSourceChannel(dataChannel);

        G1YoungCountAggregation aggregation = new G1YoungCountAggregation();
        gcsee.loadAggregation(aggregation);

        Path log = Path.of("./data/g1gc.log");

        // Act
        gcsee.analyze(new SingleGCLogFile(log));

        // Assert
        assertEquals(2546, aggregation.getCount());
    }
}
