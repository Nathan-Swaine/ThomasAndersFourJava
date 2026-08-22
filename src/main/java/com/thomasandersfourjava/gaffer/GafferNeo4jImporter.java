package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

public class GafferNeo4jImporter implements AutoCloseable {
    private final Driver driver;
    private final int batchSize;
    private final boolean inlineProgressEnabled;
    private final Set<String> preparedEntityLabels = new HashSet<>();
    private static final int DEFAULT_CONVERT_LIMIT = -1;
    private static final int DEFAULT_CONVERT_OFFSET = 0;
    private static final String DEFAULT_PROGRESS_STAGE = "nodes";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public GafferNeo4jImporter(String uri, String user, String password) {
        this(uri, user, password, 100, false);
    }

    public GafferNeo4jImporter(String uri, String user, String password, int batchSize) {
        this(uri, user, password, batchSize, false);
    }

    public GafferNeo4jImporter(String uri, String user, String password, int batchSize, boolean inlineProgressEnabled) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password), buildDriverConfig());
        this.batchSize = Math.max(batchSize, 1);
        this.inlineProgressEnabled = inlineProgressEnabled;
    }

    private static Config buildDriverConfig() {
        return Config.builder()
                .withMaxConnectionPoolSize(1)
                .withConnectionAcquisitionTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .withMaxConnectionLifetime(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    public static void main(String[] args) throws IOException {
        String sourcePath = args.length > 0 ? args[0] : "neo4j/import/matrix_characters.json";
        Properties properties = loadProperties();
        String uri = resolveConfigValue(properties, "neo4j.uri", "NEO4J_URI", "bolt://localhost:7687");
        String user = resolveConfigValue(properties, "neo4j.user", "NEO4J_USER", "neo4j");
        String password = resolveConfigValue(properties, "neo4j.password", "NEO4J_PASSWORD", "neo4j");
        int batchSize = resolveBatchSize(properties);
        int convertLimit = resolveConvertLimit(properties);
        int convertOffset = resolveConvertOffset(properties);
        boolean hasExplicitConvertOffset = hasConfiguredValue(properties, "gaffer.convert.offset", "GAFFER_CONVERT_OFFSET");
        String progressFile = resolveOptionalConfigValue(properties, "gaffer.progress.file", "GAFFER_PROGRESS_FILE");
        ImportMode importMode = resolveImportMode(properties);
        boolean inlineProgressEnabled = resolveInlineProgress(properties);
        long startTime = System.nanoTime();

        System.out.println("Starting Gaffer to Neo4j import...");
        System.out.println("Source: " + sourcePath);
        System.out.println("Neo4j URI: " + uri);
        System.out.println("Batch size: " + batchSize);
        if (convertOffset > 0) {
            System.out.println("Conversion offset (skip): " + convertOffset);
        }
        if (convertLimit > 0) {
            System.out.println("Conversion limit: " + convertLimit);
        }
        if (importMode != ImportMode.ALL) {
            System.out.println("Import mode: " + importMode.configValue);
        }
        if (progressFile != null && !progressFile.isBlank()) {
            System.out.println("Progress file: " + progressFile);
        }
        System.out.println("Progress display: " + (inlineProgressEnabled ? "inline" : "line"));

        try (GafferNeo4jImporter importer = new GafferNeo4jImporter(uri, user, password, batchSize, inlineProgressEnabled)) {
            if (shouldUseChunkProgress(sourcePath, convertLimit, hasExplicitConvertOffset, progressFile, importMode)) {
                importer.importChunkedJsonWithProgress(sourcePath, convertLimit, progressFile);
            } else {
                importer.importFromPath(sourcePath, convertOffset, convertLimit, importMode);
            }
        } catch (Exception e) {
<<<<<<< Updated upstream
            throw new IllegalStateException("Import failed", e);
        }
=======
            System.err.println("Import failed: " + e.getMessage());
            e.printStackTrace();
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            throw new IllegalStateException("Import failed for source: " + sourcePath, e);
>>>>>>> Stashed changes
        } finally {
            long elapsedMillis = (System.nanoTime() - startTime) / 1_000_000;
            System.out.println("Importer finished in " + formatDuration(elapsedMillis));
        }
    }

    private static String formatDuration(long elapsedMillis) {
        long totalSeconds = elapsedMillis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private ImportStats importFromPath(String sourcePath, int convertOffset, int convertLimit, ImportMode importMode) throws IOException {
        String lowered = sourcePath.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".csv")) {
            return importCsvStream(sourcePath, convertOffset, convertLimit, importMode);
        }
        if (lowered.endsWith(".json")) {
            return importJsonStream(sourcePath, convertOffset, convertLimit, importMode);
        }
        throw new IllegalArgumentException("Unsupported import source file type: " + sourcePath);
    }

    /** Convenience overload with no offset (starts from the beginning). */
    private ImportStats importFromPath(String sourcePath, int convertLimit) throws IOException {
        return importFromPath(sourcePath, 0, convertLimit, ImportMode.ALL);
    }

    private void importEntities(List<GafferEntity> entities) {
        List<GafferEntity> batch = new ArrayList<>();

        for (GafferEntity entity : entities) {
            batch.add(entity);
            if (batch.size() >= batchSize) {
                importEntityBatch(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            importEntityBatch(batch);
        }
    }

    private void importEntityBatch(List<GafferEntity> batch) {
        Set<String> labelsInBatch = new LinkedHashSet<>();
        for (GafferEntity entity : batch) {
            labelsInBatch.add(resolveEntityLabel(entity));
        }
        for (String label : labelsInBatch) {
            ensureEntityIdConstraint(label);
        }

        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                Map<String, List<Map<String, Object>>> rowsByLabel = new LinkedHashMap<>();
                for (GafferEntity entity : batch) {
                    rowsByLabel.computeIfAbsent(resolveEntityLabel(entity), ignored -> new ArrayList<>())
                            .add(Map.of(
                                    "vertex", entity.getVertex(),
                                    "properties", entity.getProperties() != null ? entity.getProperties() : Map.of()
                            ));
                }

                for (Map.Entry<String, List<Map<String, Object>>> entry : rowsByLabel.entrySet()) {
                    tx.run(buildEntityBatchCypher(entry.getKey()), Map.of("rows", entry.getValue()));
                }
                return null;
            });
        }
    }

    private void ensureEntityIdConstraint(String label) {
        String resolvedLabel = normalizeGraphIdentifier(label, "Entity");
        if (!preparedEntityLabels.add(resolvedLabel)) {
            return;
        }

<<<<<<< Updated upstream
        if (entity.getProperties() != null && !entity.getProperties().isEmpty()) {
            cypher.append(" SET n += $properties");
=======
        String constraintName = buildEntityIdConstraintName(resolvedLabel);
        String cypher = "CREATE CONSTRAINT " + quoteIdentifier(constraintName)
                + " IF NOT EXISTS FOR (n:" + quoteIdentifier(resolvedLabel) + ") REQUIRE n.id IS UNIQUE";
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run(cypher);
                return null;
            });
        }
    }

    private String buildEntityIdConstraintName(String label) {
        StringBuilder normalized = new StringBuilder();
        boolean previousWasUnderscore = false;
        for (int i = 0; i < label.length(); i++) {
            char c = Character.toLowerCase(label.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasUnderscore = false;
                continue;
            }
            if (!previousWasUnderscore) {
                normalized.append('_');
                previousWasUnderscore = true;
            }
>>>>>>> Stashed changes
        }

        int start = 0;
        int end = normalized.length();
        while (start < end && normalized.charAt(start) == '_') {
            start++;
        }
        while (end > start && normalized.charAt(end - 1) == '_') {
            end--;
        }

        String sanitized = start < end ? normalized.substring(start, end) : "entity";
        if (sanitized.length() > 40) {
            sanitized = sanitized.substring(0, 40);
        }
        return "gaffer_" + sanitized + "_id_unique_" + Integer.toUnsignedString(label.hashCode(), 36);
    }

    private String buildEntityBatchCypher(String label) {
        return "UNWIND $rows AS row MERGE (n:" + quoteIdentifier(label) + " {id: row.vertex}) SET n += row.properties";
    }

    private void importEdges(List<GafferEdge> edges) {
        List<GafferEdge> batch = new ArrayList<>();

        for (GafferEdge edge : edges) {
            batch.add(edge);
            if (batch.size() >= batchSize) {
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
                Map<String, List<Map<String, Object>>> rowsByRelationshipType = new LinkedHashMap<>();
                for (GafferEdge edge : batch) {
                    rowsByRelationshipType.computeIfAbsent(resolveRelationshipType(edge), ignored -> new ArrayList<>())
                            .add(Map.of(
                                    "source", edge.getSource(),
                                    "destination", edge.getDestination(),
                                    "properties", edge.getProperties() != null ? edge.getProperties() : Map.of()
                            ));
                }

                for (Map.Entry<String, List<Map<String, Object>>> entry : rowsByRelationshipType.entrySet()) {
                    tx.run(buildEdgeBatchCypher(entry.getKey()), Map.of("rows", entry.getValue()));
                }
                return null;
            });
        }
    }

    private String buildEdgeBatchCypher(String relationshipType) {
        return "UNWIND $rows AS row MATCH (a {id: row.source}) MATCH (b {id: row.destination}) "
                + "MERGE (a)-[r:" + quoteIdentifier(relationshipType) + "]->(b) SET r += row.properties";
    }

    private String resolveEntityLabel(GafferEntity entity) {
        return normalizeGraphIdentifier(entity != null ? entity.getGroup() : null, "Entity");
    }

    private String resolveRelationshipType(GafferEdge edge) {
        return normalizeGraphIdentifier(edge != null ? edge.getGroup() : null, "RelatedTo");
    }

    private String normalizeGraphIdentifier(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String quoteIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private ImportStats importJsonStream(String jsonPath, int convertOffset, int convertLimit, ImportMode importMode) throws IOException {
        System.out.println("Streaming JSON import from: " + jsonPath);
        StreamBatchAccumulator accumulator = new StreamBatchAccumulator(resolveEntityImportTarget(convertLimit));

        try (InputStream input = Files.newInputStream(Paths.get(jsonPath));
             JsonParser parser = mapper.getFactory().createParser(input)) {
            JsonToken firstToken = parser.nextToken();
            if (firstToken == null) {
                System.out.println("JSON file is empty. Nothing to import.");
                return new ImportStats(0, 0);
            }
            if (firstToken == JsonToken.START_OBJECT) {
                importJsonObjectRoot(parser, accumulator, convertOffset, convertLimit, importMode);
            } else if (firstToken == JsonToken.START_ARRAY) {
                importJsonArrayRoot(parser, accumulator, convertOffset, convertLimit, importMode);
            } else {
                throw new IllegalArgumentException("Unsupported JSON root token: " + firstToken);
            }
        }

        accumulator.flush();
        System.out.println("JSON import complete. Entities: " + accumulator.entitiesImported + ", Edges: " + accumulator.edgesImported);
        return new ImportStats(accumulator.entitiesImported, accumulator.edgesImported);
    }

    private void importJsonObjectRoot(
            JsonParser parser,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) throws IOException {
        importJsonObjectContents(parser, accumulator, convertOffset, convertLimit, importMode);
    }

    private void importJsonObjectContents(
            JsonParser parser,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                break;
            }

            if (isEntityFieldName(fieldName)) {
                consumeEntitySection(parser, valueToken, accumulator, convertOffset, convertLimit, importMode);
                continue;
            }
            if (isEdgeFieldName(fieldName)) {
                consumeEdgeSection(parser, valueToken, accumulator, convertOffset, convertLimit, importMode);
                continue;
            }
            if (isGraphFieldName(fieldName) && valueToken == JsonToken.START_OBJECT) {
                importJsonObjectContents(parser, accumulator, convertOffset, convertLimit, importMode);
                continue;
            }
            parser.skipChildren();
        }
    }

    private void importJsonArrayRoot(
            JsonParser parser,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) throws IOException {
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.START_OBJECT) {
                parser.skipChildren();
                continue;
            }
            // Determine which counter applies for offset/limit checks.
            boolean isNodeLike = importMode != ImportMode.EDGES_ONLY;
            int seen = isNodeLike ? accumulator.entitiesSeen : accumulator.edgesSeen;
            if (isUnderOffset(seen, convertOffset) || isOverLimit(seen, convertOffset, convertLimit)) {
                parser.skipChildren();
                if (isNodeLike) {
                    accumulator.entitiesSeen++;
                } else {
                    accumulator.edgesSeen++;
                }
                continue;
            }
            Map<String, Object> map = parser.readValueAs(MAP_TYPE);
            processGenericJsonObject(map, accumulator, convertOffset, convertLimit, importMode);
        }
    }

    private void consumeEntitySection(
            JsonParser parser,
            JsonToken valueToken,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) throws IOException {
        if (importMode == ImportMode.EDGES_ONLY) {
            parser.skipChildren();
            return;
        }
        if (valueToken == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }
                if (isUnderOffset(accumulator.entitiesSeen, convertOffset)) {
                    accumulator.entitiesSeen++;
                    parser.skipChildren();
                    continue;
                }
                if (isOverLimit(accumulator.entitiesSeen, convertOffset, convertLimit)) {
                    // Limit reached — skip this object and drain the rest of the array without
                    // reading each item individually.
                    parser.skipChildren();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parser.skipChildren();
                    }
                    return;
                }
                Map<String, Object> map = parser.readValueAs(MAP_TYPE);
                GafferEntity entity = toEntity(map);
                if (entity != null) {
                    accumulator.addEntity(entity);
                }
                accumulator.entitiesSeen++;
            }
            return;
        }

        if (valueToken == JsonToken.START_OBJECT && !isUnderOffset(accumulator.entitiesSeen, convertOffset)
                && !isOverLimit(accumulator.entitiesSeen, convertOffset, convertLimit)) {
            Map<String, Object> map = parser.readValueAs(MAP_TYPE);
            GafferEntity entity = toEntity(map);
            if (entity != null) {
                accumulator.addEntity(entity);
            }
            accumulator.entitiesSeen++;
            return;
        }

        parser.skipChildren();
    }

    private void consumeEdgeSection(
            JsonParser parser,
            JsonToken valueToken,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) throws IOException {
        if (importMode == ImportMode.ENTITIES_ONLY) {
            parser.skipChildren();
            return;
        }
        if (valueToken == JsonToken.START_ARRAY) {
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() != JsonToken.START_OBJECT) {
                    parser.skipChildren();
                    continue;
                }
                if (isUnderOffset(accumulator.edgesSeen, convertOffset) || isOverLimit(accumulator.edgesSeen, convertOffset, convertLimit)) {
                    accumulator.edgesSeen++;
                    parser.skipChildren();
                    continue;
                }
                Map<String, Object> map = parser.readValueAs(MAP_TYPE);
                GafferEdge edge = toEdge(map);
                if (edge != null) {
                    accumulator.addEdge(edge);
                }
                accumulator.edgesSeen++;
            }
            return;
        }

        if (valueToken == JsonToken.START_OBJECT && !isUnderOffset(accumulator.edgesSeen, convertOffset)
                && !isOverLimit(accumulator.edgesSeen, convertOffset, convertLimit)) {
            Map<String, Object> map = parser.readValueAs(MAP_TYPE);
            GafferEdge edge = toEdge(map);
            if (edge != null) {
                accumulator.addEdge(edge);
            }
            accumulator.edgesSeen++;
            return;
        }

        parser.skipChildren();
    }

    private void processGenericJsonObject(
            Map<String, Object> map,
            StreamBatchAccumulator accumulator,
            int convertOffset,
            int convertLimit,
            ImportMode importMode) {
        if (map == null || map.isEmpty()) {
            return;
        }
        if (importMode != ImportMode.EDGES_ONLY
                && containsNodeLikeMetadata(map)
                && !isUnderOffset(accumulator.entitiesSeen, convertOffset)
                && !isOverLimit(accumulator.entitiesSeen, convertOffset, convertLimit)) {
            GafferEntity entity = toEntity(map);
            if (entity != null) {
                accumulator.addEntity(entity);
            }
            accumulator.entitiesSeen++;
            return;
        }
        if (importMode != ImportMode.ENTITIES_ONLY
                && containsEdgeLikeMetadata(map)
                && !isUnderOffset(accumulator.edgesSeen, convertOffset)
                && !isOverLimit(accumulator.edgesSeen, convertOffset, convertLimit)) {
            GafferEdge edge = toEdge(map);
            if (edge != null) {
                accumulator.addEdge(edge);
            }
            accumulator.edgesSeen++;
        }
    }

    private ImportStats importCsvStream(String csvPath, int convertOffset, int convertLimit, ImportMode importMode) throws IOException {
        System.out.println("Streaming CSV import from: " + csvPath);
        StreamBatchAccumulator accumulator = new StreamBatchAccumulator(resolveEntityImportTarget(convertLimit));
        int rowsProcessed = 0;

        try (BufferedReader reader = Files.newBufferedReader(Path.of(csvPath))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                if (isUnderOffset(rowsProcessed, convertOffset)) {
                    rowsProcessed++;
                    continue;
                }
                if (isOverLimit(rowsProcessed, convertOffset, convertLimit)) {
                    break;
                }
                rowsProcessed++;
                Map<String, String> record = parseCsvLine(line);
                appendCsvRecord(record, accumulator, importMode);
            }
        }

        accumulator.flush();
        System.out.println("CSV import complete. Rows: " + rowsProcessed
                + ", Entities: " + accumulator.entitiesImported
                + ", Edges: " + accumulator.edgesImported);
        return new ImportStats(accumulator.entitiesImported, accumulator.edgesImported);
    }

    private void appendCsvRecord(Map<String, String> record, StreamBatchAccumulator accumulator, ImportMode importMode) {
        if (record == null || record.isEmpty()) {
            return;
        }

        String character = record.get("character");
        if (character == null || character.isEmpty()) {
            return;
        }

        String actor = record.get("actor");
        String actorBorn = record.get("actor_born");
        String movie = record.get("movie");
        String movieReleased = record.get("movie_released");
        String roleNotes = record.get("role_notes");

        if (importMode != ImportMode.EDGES_ONLY) {
            Map<String, Object> characterProps = new HashMap<>();
            characterProps.put("name", character);
            if (roleNotes != null && !roleNotes.isEmpty()) {
                characterProps.put("roleNotes", roleNotes);
            }
            accumulator.addEntity(new GafferEntity("Character", character, characterProps));

            if (actor != null && !actor.isEmpty()) {
                Map<String, Object> actorProps = new HashMap<>();
                actorProps.put("name", actor);
                if (actorBorn != null && !actorBorn.isEmpty()) {
                    try {
                        actorProps.put("birthYear", Integer.parseInt(actorBorn));
                    } catch (NumberFormatException ignored) {
                        actorProps.put("birthYear", actorBorn);
                    }
                }
                accumulator.addEntity(new GafferEntity("Actor", actor, actorProps));
            }

            if (movie != null && !movie.isEmpty()) {
                Map<String, Object> movieProps = new HashMap<>();
                movieProps.put("title", movie);
                if (movieReleased != null && !movieReleased.isEmpty()) {
                    try {
                        movieProps.put("releaseYear", Integer.parseInt(movieReleased));
                    } catch (NumberFormatException ignored) {
                        movieProps.put("releaseYear", movieReleased);
                    }
                }
                accumulator.addEntity(new GafferEntity("Movie", movie, movieProps));
            }
        }

        if (importMode != ImportMode.ENTITIES_ONLY && actor != null && !actor.isEmpty()) {
            Map<String, Object> edgeProps = new HashMap<>();
            if (movie != null && !movie.isEmpty()) {
                edgeProps.put("movie", movie);
            }
            accumulator.addEdge(new GafferEdge("ActedIn", actor, character, edgeProps));
        }
    }

    private Map<String, String> parseCsvLine(String line) {
        Map<String, String> record = new HashMap<>();
        String[] headers = {"character", "actor", "actor_born", "movie", "movie_released", "role_notes"};

        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int fieldIndex = 0;

        for (int i = 0; i < line.length() && fieldIndex < headers.length; i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    currentField.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                record.put(headers[fieldIndex], currentField.toString().trim());
                currentField = new StringBuilder();
                fieldIndex++;
            } else {
                currentField.append(c);
            }
        }

        if (fieldIndex < headers.length) {
            record.put(headers[fieldIndex], currentField.toString().trim());
        }
        return record;
    }

    private GafferEntity toEntity(Map<String, Object> node) {
        if (node == null) {
            return null;
        }

        Object idValue = firstNonNull(node.get("id"), node.get("vertex"), node.get("name"), node.get("value"));
        if (idValue == null) {
            return null;
        }

        String vertex = String.valueOf(idValue);
        if (vertex.isBlank()) {
            return null;
        }

        String group = String.valueOf(firstNonNull(node.get("group"), node.get("type"), node.get("label"), "Entity"));
        Map<String, Object> properties = new HashMap<>();

        Object explicitProperties = node.get("properties");
        if (explicitProperties instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    properties.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Object> entry : node.entrySet()) {
            String key = entry.getKey();
            if ("id".equals(key) || "vertex".equals(key) || "group".equals(key) || "type".equals(key)
                    || "label".equals(key) || "properties".equals(key)) {
                continue;
            }
            properties.putIfAbsent(key, entry.getValue());
        }

        return new GafferEntity(group, vertex, properties);
    }

    private GafferEdge toEdge(Map<String, Object> edge) {
        if (edge == null) {
            return null;
        }

        Object source = firstNonNull(edge.get("source"), edge.get("from"), edge.get("src"), edge.get("start"));
        Object destination = firstNonNull(edge.get("target"), edge.get("to"), edge.get("destination"), edge.get("end"));
        if (source == null || destination == null) {
            return null;
        }

        String group = String.valueOf(firstNonNull(edge.get("group"), edge.get("type"), edge.get("label"), "RelatedTo"));
        Map<String, Object> properties = new HashMap<>();

        Object explicitProperties = edge.get("properties");
        if (explicitProperties instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    properties.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        for (Map.Entry<String, Object> entry : edge.entrySet()) {
            String key = entry.getKey();
            if ("source".equals(key) || "from".equals(key) || "src".equals(key) || "start".equals(key)
                    || "target".equals(key) || "to".equals(key) || "destination".equals(key) || "end".equals(key)
                    || "group".equals(key) || "type".equals(key) || "label".equals(key) || "properties".equals(key)) {
                continue;
            }
            properties.putIfAbsent(key, entry.getValue());
        }

        return new GafferEdge(group, String.valueOf(source), String.valueOf(destination), properties);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !(value instanceof String s && s.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private boolean containsNodeLikeMetadata(Map<String, Object> map) {
        return map.containsKey("id") || map.containsKey("vertex") || map.containsKey("name") || map.containsKey("value");
    }

    private boolean containsEdgeLikeMetadata(Map<String, Object> map) {
        return map.containsKey("source") || map.containsKey("from") || map.containsKey("target")
                || map.containsKey("to") || map.containsKey("destination");
    }

    private boolean isEntityFieldName(String fieldName) {
        return "entities".equals(fieldName) || "nodes".equals(fieldName) || "vertices".equals(fieldName) || "people".equals(fieldName);
    }

    private boolean isEdgeFieldName(String fieldName) {
        return "edges".equals(fieldName) || "relationships".equals(fieldName) || "links".equals(fieldName);
    }

    private boolean isGraphFieldName(String fieldName) {
        return "graph".equals(fieldName);
    }

    private boolean isUnderOffset(int seen, int convertOffset) {
        return convertOffset > 0 && seen < convertOffset;
    }

    private boolean isOverLimit(int seen, int convertOffset, int convertLimit) {
        return convertLimit > 0 && (seen - convertOffset) >= convertLimit;
    }

    private boolean isOverLimit(int seen, int convertLimit) {
        return isOverLimit(seen, 0, convertLimit);
    }

    private long resolveEntityImportTarget(int convertLimit) {
        if (convertLimit > 0) {
            return convertLimit;
        }
        return -1;
    }

    @Override
    public void close() {
        // Use closeAsync to avoid blocking on Netty's shutdownGracefully quiet period
        // (which has a hardcoded 15-second timeout in the driver's connection pool).
        // The Netty event loop threads are daemon threads, so the JVM will exit cleanly
        // without needing to wait for them to finish.
        driver.closeAsync();
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

    private static int resolveBatchSize(Properties properties) {
        String systemValue = System.getProperty("gaffer.batch.size");
        if (systemValue != null && !systemValue.isEmpty()) {
            return parseBatchSize(systemValue);
        }

        String envValue = System.getenv("GAFFER_BATCH_SIZE");
        if (envValue != null && !envValue.isEmpty()) {
            return parseBatchSize(envValue);
        }

        String propertyValue = properties.getProperty("gaffer.batch.size");
        if (propertyValue != null && !propertyValue.isEmpty()) {
            return parseBatchSize(propertyValue);
        }

        return 100;
    }

    private static int resolveConvertLimit(Properties properties) {
        String systemValue = System.getProperty("gaffer.convert.limit");
        if (systemValue != null && !systemValue.isEmpty()) {
            return parseConvertLimit(systemValue);
        }

        String envValue = System.getenv("GAFFER_CONVERT_LIMIT");
        if (envValue != null && !envValue.isEmpty()) {
            return parseConvertLimit(envValue);
        }

        String propertyValue = properties.getProperty("gaffer.convert.limit");
        if (propertyValue != null && !propertyValue.isEmpty()) {
            return parseConvertLimit(propertyValue);
        }

        return DEFAULT_CONVERT_LIMIT;
    }

    private static int resolveConvertOffset(Properties properties) {
        String systemValue = System.getProperty("gaffer.convert.offset");
        if (systemValue != null && !systemValue.isEmpty()) {
            return parseConvertOffset(systemValue);
        }

        String envValue = System.getenv("GAFFER_CONVERT_OFFSET");
        if (envValue != null && !envValue.isEmpty()) {
            return parseConvertOffset(envValue);
        }

        String propertyValue = properties.getProperty("gaffer.convert.offset");
        if (propertyValue != null && !propertyValue.isEmpty()) {
            return parseConvertOffset(propertyValue);
        }

        return DEFAULT_CONVERT_OFFSET;
    }

    private static boolean resolveInlineProgress(Properties properties) {
        String configuredValue = resolveOptionalConfigValue(properties, "gaffer.progress.inline", "GAFFER_PROGRESS_INLINE");
        if (configuredValue == null || configuredValue.isBlank()) {
            return false;
        }
        return parseBooleanConfig(configuredValue, "gaffer.progress.inline");
    }

    private static ImportMode resolveImportMode(Properties properties) {
        String configuredValue = resolveOptionalConfigValue(properties, "gaffer.import.mode", "GAFFER_IMPORT_MODE");
        if (configuredValue == null || configuredValue.isBlank()) {
            return ImportMode.ALL;
        }
        return ImportMode.fromConfigValue(configuredValue);
    }

    private static String resolveOptionalConfigValue(Properties properties, String propertyKey, String envKey) {
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

        return null;
    }

    private static boolean hasConfiguredValue(Properties properties, String propertyKey, String envKey) {
        return resolveOptionalConfigValue(properties, propertyKey, envKey) != null;
    }

    private static boolean shouldUseChunkProgress(
            String sourcePath,
            int convertLimit,
            boolean hasExplicitConvertOffset,
            String progressFile,
            ImportMode importMode) {
        return sourcePath != null
                && sourcePath.toLowerCase(Locale.ROOT).endsWith(".json")
                && convertLimit > 0
                && !hasExplicitConvertOffset
                && progressFile != null
                && !progressFile.isBlank()
                && importMode == ImportMode.ALL;
    }

    private void importChunkedJsonWithProgress(String sourcePath, int convertLimit, String progressFile) throws IOException {
        String shardPath = sourcePath + ".shard";
        boolean shardExists = Files.exists(Paths.get(shardPath));

        ChunkProgressState state = loadChunkProgress(sourcePath, progressFile);
        if (state.isDone()) {
            System.out.println("Chunked JSON import already complete for " + sourcePath);
            return;
        }

        if (state.isEdgePhase()) {
            System.out.println("Node chunks complete; importing relationships for " + sourcePath);
            importFromPath(sourcePath, 0, DEFAULT_CONVERT_LIMIT, ImportMode.EDGES_ONLY);
            saveChunkProgress(progressFile, state.complete());
            deleteShard(shardPath);
            return;
        }

        if (!shardExists) {
            // First run: write the shard (nodes only, edges excluded — we read edges from original).
            // The shard is a flat JSON array of node objects: [{...},{...},...]
            // This makes it trivial to seek into by byte offset on subsequent runs.
            System.out.println("Building node shard from source (one-time operation)...");
            writeNodeShard(sourcePath, shardPath);
            System.out.println("Node shard written: " + shardPath);
        }

        System.out.println("Nodes imported so far: " + state.totalNodesImported
                + " | Shard byte offset: " + state.shardNodesByteOffset);

        // Seek into the shard at the saved byte offset and import the next chunk.
        ShardImportResult result = importFromShardAtOffset(shardPath, state.shardNodesByteOffset, convertLimit);

        if (result.stats.entitiesImported < convertLimit) {
            System.out.println("Final node chunk complete. Total nodes: "
                    + (state.totalNodesImported + result.stats.entitiesImported) + ". Importing relationships.");
            importFromPath(sourcePath, 0, DEFAULT_CONVERT_LIMIT, ImportMode.EDGES_ONLY);
            saveChunkProgress(progressFile,
                    state.advanceNodes(Math.toIntExact(result.stats.entitiesImported), result.nextByteOffset)
                         .advanceToEdges().complete());
            deleteShard(shardPath);
            return;
        }

        saveChunkProgress(progressFile,
                state.advanceNodes(Math.toIntExact(result.stats.entitiesImported), result.nextByteOffset));
    }

    private record ShardImportResult(ImportStats stats, long nextByteOffset) {}

    /**
     * Seeks into the shard file at {@code byteOffset}, reads up to {@code limit} node objects,
     * imports them, and returns the byte offset immediately after the last object read.
     * The shard is a flat JSON array: [{...},{...},...].
     * At byteOffset=0 the full array is parsed normally.
     * At byteOffset>0 we are positioned mid-array (after a closing '}'); a synthetic '[' is
     * prepended so Jackson sees a valid array context for the remaining bytes.
     */
    private ShardImportResult importFromShardAtOffset(String shardPath, long byteOffset, int limit) throws IOException {
        StreamBatchAccumulator accumulator = new StreamBatchAccumulator(resolveEntityImportTarget(limit));
        System.out.println("Streaming JSON import from: " + shardPath + " at offset " + byteOffset);
        long endOffset = byteOffset;

        try (FileChannel channel = FileChannel.open(Paths.get(shardPath), StandardOpenOption.READ)) {
            channel.position(byteOffset);
            InputStream channelStream = Channels.newInputStream(channel);

            InputStream parseStream;
            int syntheticPrefix;
            int[] delimBytesSkipped = {0};
            if (byteOffset > 0) {
                // We're positioned right after the last '}'. The next bytes are typically ",{..."
                // Skip the comma/whitespace separator, then prepend '[' for a valid array context.
                channelStream = skipLeadingDelimiters(channelStream, delimBytesSkipped);
                parseStream = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(new byte[]{'['}), channelStream);
                syntheticPrefix = 1;
            } else {
                // Offset 0: the file starts with '[' — parse as normal array.
                parseStream = channelStream;
                syntheticPrefix = 0;
            }

            try (JsonParser parser = mapper.getFactory().createParser(parseStream)) {
                parser.nextToken(); // consume '[' (either real or synthetic)
                JsonToken token;
                while ((token = parser.nextToken()) != null) {
                    if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) break;
                    if (token != JsonToken.START_OBJECT) { parser.skipChildren(); continue; }
                    if (isOverLimit(accumulator.entitiesSeen, 0, limit)) break;
                    Map<String, Object> map = parser.readValueAs(MAP_TYPE);
                    // Record position right after this object.
                    // Parser offset includes the synthetic '[' prefix but not the delimiter bytes skipped before it.
                    long afterObject = parser.getCurrentLocation().getByteOffset() - syntheticPrefix + delimBytesSkipped[0];
                    endOffset = byteOffset + afterObject;
                    GafferEntity entity = toEntity(map);
                    if (entity != null) accumulator.addEntity(entity);
                    accumulator.entitiesSeen++;
                }
            } catch (com.fasterxml.jackson.core.io.JsonEOFException ignored) {
                // Hit end of stream mid-array — treat as exhausted.
            }
        }

        accumulator.flush();
        System.out.println("JSON import complete. Entities: " + accumulator.entitiesImported
                + ", Edges: " + accumulator.edgesImported);
        return new ShardImportResult(
                new ImportStats(accumulator.entitiesImported, accumulator.edgesImported),
                endOffset);
    }

    /**
     * Writes a flat JSON array of all node objects from {@code sourcePath} to {@code shardPath}.
     * Format: [{node1},{node2},...] — no wrapper object, one array of nodes.
     * This format allows byte-offset seeking for subsequent chunked reads.
     */
    private void writeNodeShard(String sourcePath, String shardPath) throws IOException {
        Path tempPath = Paths.get(shardPath + ".tmp");
        try (OutputStream out = Files.newOutputStream(tempPath);
             JsonGenerator gen = mapper.getFactory().createGenerator(out)) {
            gen.writeStartArray();
            try (InputStream input = Files.newInputStream(Paths.get(sourcePath));
                 JsonParser parser = mapper.getFactory().createParser(input)) {
                if (parser.nextToken() != JsonToken.START_OBJECT) return;
                copyNodeObjectsFromJsonObject(parser, gen);
            }
            gen.writeEndArray();
        }
        Files.move(tempPath, Paths.get(shardPath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void copyNodeObjectsFromJsonObject(JsonParser parser, JsonGenerator gen) throws IOException {
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            JsonToken valueToken = parser.nextToken();
            if (valueToken == null) {
                break;
            }

            if (isEntityFieldName(fieldName) && valueToken == JsonToken.START_ARRAY) {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    gen.copyCurrentStructure(parser);
                }
                continue;
            }

            if (isGraphFieldName(fieldName) && valueToken == JsonToken.START_OBJECT) {
                copyNodeObjectsFromJsonObject(parser, gen);
                continue;
            }

            parser.skipChildren();
        }
    }

    private static void deleteShard(String shardPath) {
        try {
            Files.deleteIfExists(Paths.get(shardPath));
        } catch (IOException ignored) {
            // Best-effort cleanup
        }
    }

    /**
     * Reads and discards leading comma and whitespace bytes from the stream,
     * returning the stream positioned at the first non-delimiter byte.
     * The number of bytes consumed is returned via a 1-element int array (out param).
     */
    private static InputStream skipLeadingDelimiters(InputStream in, int[] bytesSkipped) throws IOException {
        java.io.PushbackInputStream pb = new java.io.PushbackInputStream(in, 4);
        int skipped = 0;
        int b;
        while ((b = pb.read()) != -1) {
            if (b != ',' && b != ' ' && b != '\t' && b != '\n' && b != '\r') {
                pb.unread(b);
                break;
            }
            skipped++;
        }
        bytesSkipped[0] = skipped;
        return pb;
    }

    private static int parseConvertOffset(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException("gaffer.convert.offset must be a non-negative integer");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid gaffer.convert.offset value: " + value, e);
        }
    }

    private static int parseConvertLimit(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("gaffer.convert.limit must be a positive integer");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid gaffer.convert.limit value: " + value, e);
        }
    }

    private static int parseBatchSize(String value) {
        try {
            int batchSize = Integer.parseInt(value.trim());
            return Math.max(batchSize, 1);
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static boolean parseBooleanConfig(String value, String configName) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid " + configName + " value: " + value + " (expected true or false)");
    }

    private class StreamBatchAccumulator {
        private final List<GafferEntity> entityBatch = new ArrayList<>();
        private final List<GafferEdge> edgeBatch = new ArrayList<>();
        private final long entityImportTarget;
        private final long entityBatchTarget;
        private final boolean useInlineProgress;
        private long entitiesImported;
        private long entityBatchesImported;
        private long edgesImported;
        private int entitiesSeen;
        private int edgesSeen;
        private int inlineProgressMessageLength;
        private boolean inlineProgressActive;

        private StreamBatchAccumulator(long entityImportTarget) {
            this.entityImportTarget = entityImportTarget;
            this.entityBatchTarget = entityImportTarget > 0
                    ? ((entityImportTarget + batchSize - 1) / batchSize)
                    : -1;
            this.useInlineProgress = inlineProgressEnabled;
        }

        private void addEntity(GafferEntity entity) {
            if (entity == null) {
                return;
            }
            entityBatch.add(entity);
            if (entityBatch.size() >= batchSize) {
                importEntityBatch(entityBatch);
                entitiesImported += entityBatch.size();
                entityBatch.clear();
                logEntityProgress();
            }
        }

        private void addEdge(GafferEdge edge) {
            if (edge == null) {
                return;
            }
            edgeBatch.add(edge);
            if (edgeBatch.size() >= batchSize) {
                importEdges(edgeBatch);
                edgesImported += edgeBatch.size();
                edgeBatch.clear();
            }
        }

        private void flush() {
            if (!entityBatch.isEmpty()) {
                importEntityBatch(entityBatch);
                entitiesImported += entityBatch.size();
                entityBatch.clear();
                logEntityProgress();
            }
            if (!edgeBatch.isEmpty()) {
                importEdges(edgeBatch);
                edgesImported += edgeBatch.size();
                edgeBatch.clear();
            }
            finishInlineProgress();
        }

        private void logEntityProgress() {
            entityBatchesImported++;
            String message;
            if (entityBatchTarget > 0) {
                message = "Entity batch progress: " + entityBatchesImported + "/" + entityBatchTarget
                        + " | Entity import progress: " + entitiesImported + "/" + entityImportTarget;
            } else {
                message = "Entity batch progress: " + entityBatchesImported + "/?"
                        + " | Entity import progress: " + entitiesImported + "/?";
            }
            if (useInlineProgress) {
                writeInlineProgress(message);
                return;
            }
            System.out.println(message);
        }

        private void writeInlineProgress(String message) {
            int trailingPadding = Math.max(0, inlineProgressMessageLength - message.length());
            System.out.print("\r" + message + " ".repeat(trailingPadding));
            System.out.flush();
            inlineProgressMessageLength = message.length();
            inlineProgressActive = true;
        }

        private void finishInlineProgress() {
            if (!inlineProgressActive) {
                return;
            }
            System.out.println();
            inlineProgressMessageLength = 0;
            inlineProgressActive = false;
        }
    }

    private enum ImportMode {
        ALL("all"),
        ENTITIES_ONLY("entities"),
        EDGES_ONLY("edges");

        private final String configValue;

        ImportMode(String configValue) {
            this.configValue = configValue;
        }

        private static ImportMode fromConfigValue(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "", "all" -> ALL;
                case "entities", "nodes", "entities-only", "nodes-only" -> ENTITIES_ONLY;
                case "edges", "relationships", "edges-only", "relationships-only" -> EDGES_ONLY;
                default -> throw new IllegalArgumentException("Unsupported gaffer.import.mode value: " + value);
            };
        }
    }

    public static final class ImportStats {
        private final long entitiesImported;
        private final long edgesImported;

        private ImportStats(long entitiesImported, long edgesImported) {
            this.entitiesImported = entitiesImported;
            this.edgesImported = edgesImported;
        }
    }

    private static final class ChunkProgressState {
        private final String fingerprint;
        private final int totalNodesImported;
        /** Byte offset into the shard file's nodes array where the next unimported node starts. 0 = beginning. */
        private final long shardNodesByteOffset;
        private final String stage;

        private ChunkProgressState(String fingerprint, int totalNodesImported, long shardNodesByteOffset, String stage) {
            this.fingerprint = fingerprint;
            this.totalNodesImported = totalNodesImported;
            this.shardNodesByteOffset = shardNodesByteOffset;
            this.stage = stage;
        }

        private boolean isEdgePhase() {
            return "edges".equals(stage);
        }

        private boolean isDone() {
            return "done".equals(stage);
        }

        private ChunkProgressState advanceNodes(int addedNodes, long newByteOffset) {
            return new ChunkProgressState(fingerprint, totalNodesImported + addedNodes, newByteOffset, DEFAULT_PROGRESS_STAGE);
        }

        private ChunkProgressState advanceToEdges() {
            return new ChunkProgressState(fingerprint, totalNodesImported, 0, "edges");
        }

        private ChunkProgressState complete() {
            return new ChunkProgressState(fingerprint, totalNodesImported, 0, "done");
        }
    }

    private static ChunkProgressState loadChunkProgress(String sourcePath, String progressFile) throws IOException {
        Path source = Paths.get(sourcePath);
        String currentFingerprint = fileFingerprint(source);
        Path progressPath = Paths.get(progressFile);
        if (!Files.exists(progressPath)) {
            return new ChunkProgressState(currentFingerprint, 0, 0, DEFAULT_PROGRESS_STAGE);
        }

        Properties stateProperties = new Properties();
        try (InputStream input = Files.newInputStream(progressPath)) {
            stateProperties.load(input);
        }

        String storedFingerprint = stateProperties.getProperty("fingerprint");
        if (!currentFingerprint.equals(storedFingerprint)) {
            return new ChunkProgressState(currentFingerprint, 0, 0, DEFAULT_PROGRESS_STAGE);
        }

        int totalNodesImported = parseProgressOffset(stateProperties.getProperty("totalNodesImported", "0"));
        long shardNodesByteOffset = parseProgressLong(stateProperties.getProperty("shardNodesByteOffset", "0"));
        String stage = stateProperties.getProperty("stage", DEFAULT_PROGRESS_STAGE);
        return new ChunkProgressState(currentFingerprint, totalNodesImported, shardNodesByteOffset, stage);
    }

    private static void saveChunkProgress(String progressFile, ChunkProgressState state) throws IOException {
        Path progressPath = Paths.get(progressFile);
        Path parent = progressPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Properties stateProperties = new Properties();
        stateProperties.setProperty("fingerprint", state.fingerprint);
        stateProperties.setProperty("totalNodesImported", String.valueOf(state.totalNodesImported));
        stateProperties.setProperty("shardNodesByteOffset", String.valueOf(state.shardNodesByteOffset));
        stateProperties.setProperty("stage", state.stage);

        try (OutputStream output = Files.newOutputStream(progressPath)) {
            stateProperties.store(output, "Chunked Gaffer import progress");
        }
    }

    private static String fileFingerprint(Path source) throws IOException {
        return Files.size(source) + ":" + Files.getLastModifiedTime(source).toMillis();
    }

    private static int parseProgressOffset(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException("Progress offset must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid progress offset value: " + value, e);
        }
    }

    private static long parseProgressLong(String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException("Progress long value must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid progress long value: " + value, e);
        }
    }
}
