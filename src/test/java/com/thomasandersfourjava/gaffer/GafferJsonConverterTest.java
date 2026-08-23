package com.thomasandersfourjava.gaffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GafferJsonConverterTest {

    @Test
    void convertsGenericNodesAndEdgesIntoBatchedGafferGraphs() {
        List<Map<String, Object>> nodes = List.of(
                Map.of("id", "Alice", "type", "Person", "name", "Alice"),
                Map.of("id", "Bob", "type", "Person", "name", "Bob"),
                Map.of("id", "Charlie", "type", "Person", "name", "Charlie")
        );
        List<Map<String, Object>> edges = List.of(
                Map.of("source", "Alice", "target", "Bob", "type", "KNOWS", "weight", 2),
                Map.of("source", "Bob", "target", "Charlie", "type", "KNOWS", "weight", 3)
        );

        List<GafferGraph> batches = GafferJsonConverter.convertGenericGraphToBatches(nodes, edges, 1);

        assertEquals(5, batches.size());
        assertEquals("Person", batches.get(0).getEntities().get(0).getGroup());
        assertEquals("Alice", batches.get(0).getEntities().get(0).getVertex());
        assertTrue(batches.get(0).getEdges().isEmpty());
        assertEquals("Person", batches.get(2).getEntities().get(0).getGroup());
        assertEquals("Charlie", batches.get(2).getEntities().get(0).getVertex());
        assertTrue(batches.get(2).getEdges().isEmpty());
        assertTrue(batches.get(3).getEntities().isEmpty());
        assertEquals("KNOWS", batches.get(3).getEdges().get(0).getGroup());
        assertEquals("Alice", batches.get(3).getEdges().get(0).getSource());
        assertEquals("Bob", batches.get(3).getEdges().get(0).getDestination());
    }

    @Test
    void writesJsonBatchesToDisk() throws IOException {
        Path tempDir = Files.createTempDirectory("gaffer-batches");
        List<GafferGraph> batches = List.of(
                new GafferGraph(
                        List.of(new GafferEntity("Person", "Alice", Map.of("name", "Alice"))),
                        List.of(new GafferEdge("KNOWS", "Alice", "Bob", Map.of("weight", 2)))
                )
        );

        GafferJsonConverter.writeBatchedGafferJson(batches, tempDir.toString(), "person-graph");

        Path batch = tempDir.resolve("person-graph-0001.json");
        assertTrue(Files.exists(batch));
        String content = Files.readString(batch);
        assertTrue(content.contains("\"group\" : \"Person\""));
        assertTrue(content.contains("\"destination\" : \"Bob\""));
    }

    @Test
    void convertsGraphJsonWrappersContainingEdgesIntoBatchedGafferGraphs() throws IOException {
        Path tempFile = Files.createTempFile("graph", ".json");
        Files.writeString(tempFile, """
                {
                  "graph": {
                    "entities": [
                      {
                        "group": "Person",
                        "vertex": "Alice",
                        "properties": { "name": "Alice" }
                      },
                      {
                        "group": "Person",
                        "vertex": "Bob",
                        "properties": { "name": "Bob" }
                      }
                    ],
                    "edges": [
                      {
                        "group": "KNOWS",
                        "source": "Alice",
                        "destination": "Bob",
                        "properties": { "weight": 2 }
                      }
                    ]
                  }
                }
                """);

        List<GafferGraph> batches = GafferJsonConverter.convertJsonGraphToBatches(tempFile.toString(), 10);

        assertEquals(2, batches.size());
        assertEquals(2, batches.get(0).getEntities().size());
        assertTrue(batches.get(0).getEdges().isEmpty());
        assertTrue(batches.get(1).getEntities().isEmpty());
        assertEquals(1, batches.get(1).getEdges().size());
        assertEquals("Alice", batches.get(1).getEdges().get(0).getSource());
        assertEquals("Bob", batches.get(1).getEdges().get(0).getDestination());
    }
}
