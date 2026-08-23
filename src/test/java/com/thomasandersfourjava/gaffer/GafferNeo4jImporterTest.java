package com.thomasandersfourjava.gaffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GafferNeo4jImporterTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    @Test
    void writeNodeShardSupportsFlatRootArrays() throws Exception {
        Path tempDir = Files.createTempDirectory("gaffer-importer");
        Path source = tempDir.resolve("source.json");
        Path shard = tempDir.resolve("source.json.shard");
        Files.writeString(source, """
                [
                  { "id": "Alice", "type": "Person", "name": "Alice" },
                  { "id": "Bob", "type": "Person", "name": "Bob" }
                ]
                """);

        try (GafferNeo4jImporter importer = new GafferNeo4jImporter("bolt://localhost:7687", "neo4j", "neo4j")) {
            Method writeNodeShard = GafferNeo4jImporter.class.getDeclaredMethod("writeNodeShard", String.class, String.class);
            writeNodeShard.setAccessible(true);
            writeNodeShard.invoke(importer, source.toString(), shard.toString());
        }

        List<Map<String, Object>> nodes = MAPPER.readValue(Files.readString(shard), LIST_OF_MAPS);
        assertEquals(2, nodes.size());
        assertEquals("Alice", nodes.get(0).get("id"));
        assertEquals("Bob", nodes.get(1).get("id"));
    }
}
