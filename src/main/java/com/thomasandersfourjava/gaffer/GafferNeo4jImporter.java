package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

public class GafferNeo4jImporter implements AutoCloseable {
    private final Driver driver;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int BATCH_SIZE = 100;

    public GafferNeo4jImporter(String uri, String user, String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public static void main(String[] args) throws IOException {
        String gafferJsonPath = args.length > 0 ? args[0] : "neo4j/import/matrix_characters.json";
        Properties properties = loadProperties();
        String uri = resolveConfigValue(properties, "neo4j.uri", "NEO4J_URI", "bolt://localhost:7687");
        String user = resolveConfigValue(properties, "neo4j.user", "NEO4J_USER", "neo4j");
        String password = resolveConfigValue(properties, "neo4j.password", "NEO4J_PASSWORD", "neo4j");
        long startTime = System.nanoTime();

        System.out.println("Starting Gaffer to Neo4j import...");
        System.out.println("Gaffer JSON: " + gafferJsonPath);
        System.out.println("Neo4j URI: " + uri);

        try (GafferNeo4jImporter importer = new GafferNeo4jImporter(uri, user, password)) {
            importer.importGafferGraph(gafferJsonPath);
        } catch (Exception e) {
            System.err.println("Import failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("Importer finished in " + elapsedMillis + " ms");
        }
    }

    public void importGafferGraph(String jsonPath) throws IOException {
        GafferGraph graph = mapper.readValue(Files.newInputStream(Paths.get(jsonPath)), GafferGraph.class);

        System.out.println("Loaded Gaffer graph: " + graph);
        System.out.println("Importing entities...");

        importEntities(graph.getEntities());
        System.out.println("Entities imported successfully!");

        System.out.println("Importing edges...");
        importEdges(graph.getEdges());
        System.out.println("Edges imported successfully!");

        System.out.println("Import complete!");
    }

    private void importEntities(List<GafferEntity> entities) {
        List<GafferEntity> batch = new ArrayList<>();

        for (GafferEntity entity : entities) {
            batch.add(entity);
            if (batch.size() >= BATCH_SIZE) {
                importEntityBatch(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            importEntityBatch(batch);
        }
    }

    private void importEntityBatch(List<GafferEntity> batch) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                for (GafferEntity entity : batch) {
                    System.out.println("Importing entity: " + entity.getGroup() + " [" + entity.getVertex() + "]");
                    String cypher = buildEntityCypher(entity);
                    tx.run(cypher, Map.of(
                            "vertex", entity.getVertex(),
                            "properties", entity.getProperties() != null ? entity.getProperties() : Map.of()
                    ));
                }
                return null;
            });
        }
    }

    private String buildEntityCypher(GafferEntity entity) {
        String label = entity.getGroup();
        StringBuilder cypher = new StringBuilder("MERGE (n:`");
        cypher.append(label).append("` {id: $vertex})");

        if (entity.getProperties() != null && !entity.getProperties().isEmpty()) {
            cypher.append(" SET ");
            boolean first = true;
            for (String key : entity.getProperties().keySet()) {
                if (!first) cypher.append(", ");
                cypher.append("n.").append(key).append(" = $properties.").append(key);
                first = false;
            }
        }

        return cypher.toString();
    }

    private void importEdges(List<GafferEdge> edges) {
        List<GafferEdge> batch = new ArrayList<>();

        for (GafferEdge edge : edges) {
            batch.add(edge);
            if (batch.size() >= BATCH_SIZE) {
                importEdgeBatch(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            importEdgeBatch(batch);
        }
    }

    private void importEdgeBatch(List<GafferEdge> batch) {
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                for (GafferEdge edge : batch) {
                    String cypher = buildEdgeCypher(edge);
                    tx.run(cypher, Map.of(
                            "source", edge.getSource(),
                            "destination", edge.getDestination(),
                            "properties", edge.getProperties() != null ? edge.getProperties() : Map.of()
                    ));
                }
                return null;
            });
        }
    }

    private String buildEdgeCypher(GafferEdge edge) {
        String relationshipType = edge.getGroup();
        StringBuilder cypher = new StringBuilder("MATCH (a {id: $source}) MATCH (b {id: $destination}) ");
        cypher.append("MERGE (a)-[r:`").append(relationshipType).append("`]->(b)");

        if (edge.getProperties() != null && !edge.getProperties().isEmpty()) {
            cypher.append(" SET ");
            boolean first = true;
            for (String key : edge.getProperties().keySet()) {
                if (!first) cypher.append(", ");
                cypher.append("r.").append(key).append(" = $properties.").append(key);
                first = false;
            }
        }

        return cypher.toString();
    }

    @Override
    public void close() {
        driver.close();
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = GafferNeo4jImporter.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load application.properties", ex);
        }
        return properties;
    }

    private static String resolveConfigValue(Properties properties, String propertyKey, String envKey, String defaultValue) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isEmpty()) {
            return systemValue;
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        String propertyValue = properties.getProperty(propertyKey);
        if (propertyValue != null && !propertyValue.isEmpty()) {
            return propertyValue;
        }

        return defaultValue;
    }
}
