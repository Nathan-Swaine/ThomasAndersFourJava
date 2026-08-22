package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class GafferJsonConverter {
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final int DEFAULT_CONVERT_LIMIT = -1;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        Properties properties = loadProperties();
        int configuredBatchSize = resolveBatchSize(properties);
        int configuredConvertLimit = resolveConvertLimit(properties);

        if (args.length == 0) {
            runCsvConversion("matrix_characters.csv", "neo4j/import/matrix_characters.json", configuredConvertLimit);
            return;
        }

        String inputPath = args[0];
        String outputPath = args.length > 1 ? args[1] : inferDefaultOutputPath(inputPath);
        int batchSize = configuredBatchSize;
        int convertLimit = configuredConvertLimit;

        if (args.length > 2 && !args[2].isBlank()) {
            batchSize = parseBatchSize(args[2]);
        }
        if (args.length > 3 && !args[3].isBlank()) {
            convertLimit = parseConvertLimit(args[3]);
        }

        if (inputPath.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            runCsvConversion(inputPath, outputPath, convertLimit);
            return;
        }

        List<GafferGraph> batches = convertJsonGraphToBatches(inputPath, batchSize, convertLimit);
        writeBatchedGafferJson(batches, outputPath, "gaffer-batch");

        System.out.println("Conversion complete!");
        System.out.println("Generated batches: " + batches.size());
    }

    private static void runCsvConversion(String csvPath, String outputPath, int convertLimit) throws IOException {
        System.out.println("Converting CSV to Gaffer JSON...");
        System.out.println("Input: " + csvPath);
        System.out.println("Output: " + outputPath);
        if (convertLimit > 0) {
            System.out.println("Conversion row limit: " + convertLimit);
        }

        GafferGraph graph = convertCsvToGaffer(csvPath, convertLimit);
        writeGafferJson(graph, outputPath);

        System.out.println("Conversion complete!");
        System.out.println("Entities: " + graph.getEntities().size());
        System.out.println("Edges: " + graph.getEdges().size());
    }

    public static int parseBatchSize(String batchSizeValue) {
        if (batchSizeValue == null || batchSizeValue.trim().isEmpty()) {
            return DEFAULT_BATCH_SIZE;
        }

        try {
            int parsed = Integer.parseInt(batchSizeValue);
            return parsed > 0 ? parsed : DEFAULT_BATCH_SIZE;
        } catch (NumberFormatException e) {
            if (batchSizeValue.startsWith("--batch-size=")) {
                return parseBatchSize(batchSizeValue.substring("--batch-size=".length()));
            }
            if (batchSizeValue.startsWith("-b")) {
                return parseBatchSize(batchSizeValue.substring(2));
            }
            return DEFAULT_BATCH_SIZE;
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (var stream = GafferJsonConverter.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load application.properties", e);
        }
        return properties;
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

        return DEFAULT_BATCH_SIZE;
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

    private static int parseConvertLimit(String limitValue) {
        if (limitValue == null || limitValue.trim().isEmpty()) {
            return DEFAULT_CONVERT_LIMIT;
        }
        try {
            int parsed = Integer.parseInt(limitValue.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("Conversion limit must be a positive integer. Value: " + limitValue);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid conversion limit. Expected an integer, got: " + limitValue, e);
        }
    }

    public static GafferGraph convertCsvToGaffer(String csvPath) throws IOException {
        return convertCsvToGaffer(csvPath, DEFAULT_CONVERT_LIMIT);
    }

    public static GafferGraph convertCsvToGaffer(String csvPath, int convertLimit) throws IOException {
        List<GafferEntity> entities = new ArrayList<>();
        List<GafferEdge> edges = new ArrayList<>();
        Set<String> seenCharacters = new HashSet<>();
        Set<String> seenActors = new HashSet<>();
        Set<String> seenMovies = new HashSet<>();
        int recordsProcessed = 0;

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvPath))) {
            String headerLine = reader.readLine();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (convertLimit > 0 && recordsProcessed >= convertLimit) {
                    break;
                }
                recordsProcessed++;

                Map<String, String> record = parseCsvLine(line);

                String character = record.get("character");
                String actor = record.get("actor");
                String actorBorn = record.get("actor_born");
                String movie = record.get("movie");
                String movieReleased = record.get("movie_released");
                String roleNotes = record.get("role_notes");

                if (character == null || character.isEmpty()) continue;

                if (!seenCharacters.contains(character)) {
                    Map<String, Object> charProps = new HashMap<>();
                    charProps.put("name", character);
                    if (roleNotes != null && !roleNotes.isEmpty()) {
                        charProps.put("roleNotes", roleNotes);
                    }
                    entities.add(new GafferEntity("Character", character, charProps));
                    seenCharacters.add(character);
                }

                if (actor != null && !actor.isEmpty() && !seenActors.contains(actor)) {
                    Map<String, Object> actorProps = new HashMap<>();
                    actorProps.put("name", actor);
                    if (actorBorn != null && !actorBorn.isEmpty()) {
                        try {
                            actorProps.put("birthYear", Integer.parseInt(actorBorn));
                        } catch (NumberFormatException e) {
                            actorProps.put("birthYear", actorBorn);
                        }
                    }
                    entities.add(new GafferEntity("Actor", actor, actorProps));
                    seenActors.add(actor);
                }

                if (movie != null && !movie.isEmpty() && !seenMovies.contains(movie)) {
                    Map<String, Object> movieProps = new HashMap<>();
                    movieProps.put("title", movie);
                    if (movieReleased != null && !movieReleased.isEmpty()) {
                        try {
                            movieProps.put("releaseYear", Integer.parseInt(movieReleased));
                        } catch (NumberFormatException e) {
                            movieProps.put("releaseYear", movieReleased);
                        }
                    }
                    entities.add(new GafferEntity("Movie", movie, movieProps));
                    seenMovies.add(movie);
                }

                if (actor != null && !actor.isEmpty()) {
                    Map<String, Object> edgeProps = new HashMap<>();
                    if (movie != null && !movie.isEmpty()) {
                        edgeProps.put("movie", movie);
                    }
                    edges.add(new GafferEdge("ActedIn", actor, character, edgeProps));
                }
            }
        }

        return new GafferGraph(entities, edges);
    }

    public static List<GafferGraph> convertJsonGraphToBatches(String jsonPath, int batchSize) throws IOException {
        return convertJsonGraphToBatches(jsonPath, batchSize, DEFAULT_CONVERT_LIMIT);
    }

    public static List<GafferGraph> convertJsonGraphToBatches(String jsonPath, int batchSize, int convertLimit) throws IOException {
        Object rootValue = mapper.readValue(Files.newInputStream(Paths.get(jsonPath)), Object.class);
        List<Map<String, Object>> nodes;
        List<Map<String, Object>> edges;

        if (rootValue instanceof List<?> rootList) {
            nodes = new ArrayList<>();
            edges = new ArrayList<>();
            for (Object item : rootList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> casted = (Map<String, Object>) map;
                    if (containsNodeLikeMetadata(casted)) {
                        nodes.add(casted);
                    } else if (containsEdgeLikeMetadata(casted)) {
                        edges.add(casted);
                    }
                }
            }
        } else {
            Map<String, Object> root = resolveGraphRoot((Map<String, Object>) rootValue);
            nodes = extractNamedList(root, "nodes", "entities", "vertices", "people");
            edges = extractNamedList(root, "edges", "relationships", "links");

            if (nodes.isEmpty() && !root.containsKey("id") && !root.containsKey("vertex")) {
                throw new IllegalArgumentException("No node list found in JSON input. Expected a 'nodes' or 'entities' array.");
            }

            if (nodes.isEmpty() && root.containsKey("id")) {
                nodes = Collections.singletonList(root);
            }

            if (edges.isEmpty() && root.containsKey("source") && root.containsKey("target")) {
                edges = Collections.singletonList(root);
            }
        }

        List<Map<String, Object>> limitedNodes = applyLimit(nodes, convertLimit);
        List<Map<String, Object>> limitedEdges = applyLimit(edges, convertLimit);
        return convertGenericGraphToBatches(limitedNodes, limitedEdges, batchSize);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveGraphRoot(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return new HashMap<>();
        }

        Object graph = root.get("graph");
        if (graph instanceof Map<?, ?> graphMap) {
            Map<String, Object> graphRoot = (Map<String, Object>) graphMap;
            if (containsGraphCollections(graphRoot)) {
                return graphRoot;
            }
        }

        return root;
    }

    private static boolean containsGraphCollections(Map<String, Object> root) {
        return root.containsKey("nodes")
                || root.containsKey("entities")
                || root.containsKey("vertices")
                || root.containsKey("people")
                || root.containsKey("edges")
                || root.containsKey("relationships")
                || root.containsKey("links");
    }

    public static List<GafferGraph> convertGenericGraphToBatches(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, int batchSize) {
        List<Map<String, Object>> normalizedNodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        List<Map<String, Object>> normalizedEdges = edges == null ? new ArrayList<>() : new ArrayList<>(edges);
        int effectiveBatchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;

        int nodeCount = normalizedNodes.size();
        int edgeCount = normalizedEdges.size();
        int totalBatches = Math.max((nodeCount + effectiveBatchSize - 1) / effectiveBatchSize,
                (edgeCount + effectiveBatchSize - 1) / effectiveBatchSize);

        List<GafferGraph> batches = new ArrayList<>();
        for (int i = 0; i < totalBatches; i++) {
            int nodeStart = i * effectiveBatchSize;
            int edgeStart = i * effectiveBatchSize;

            List<GafferEntity> entities = new ArrayList<>();
            for (int j = nodeStart; j < normalizedNodes.size() && j < nodeStart + effectiveBatchSize; j++) {
                GafferEntity entity = toEntity(normalizedNodes.get(j));
                if (entity != null) {
                    entities.add(entity);
                }
            }

            List<GafferEdge> entityEdges = new ArrayList<>();
            for (int j = edgeStart; j < normalizedEdges.size() && j < edgeStart + effectiveBatchSize; j++) {
                GafferEdge edge = toEdge(normalizedEdges.get(j));
                if (edge != null) {
                    entityEdges.add(edge);
                }
            }

            if (entities.isEmpty() && entityEdges.isEmpty()) {
                continue;
            }

            batches.add(new GafferGraph(entities, entityEdges));
        }

        return batches;
    }

    public static void writeGafferJson(GafferGraph graph, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), graph);
    }

    public static void writeBatchedGafferJson(List<GafferGraph> batches, String outputPath, String baseFileName) throws IOException {
        if (batches == null || batches.isEmpty()) {
            return;
        }

        if (batches.size() == 1 && outputPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
            writeGafferJson(batches.get(0), outputPath);
            return;
        }

        Path outputDirectory = Paths.get(outputPath);
        if (outputPath.toLowerCase(Locale.ROOT).endsWith(".json")) {
            outputDirectory = outputDirectory.getParent() == null ? Paths.get(".") : outputDirectory.getParent();
        }
        Files.createDirectories(outputDirectory);

        String filePrefix = baseFileName == null || baseFileName.trim().isEmpty() ? "gaffer-batch" : baseFileName.trim();
        for (int i = 0; i < batches.size(); i++) {
            String fileName = String.format(Locale.ROOT, "%s-%04d.json", filePrefix, i + 1);
            Path target = outputDirectory.resolve(fileName);
            writeGafferJson(batches.get(i), target.toString());
        }
    }

    private static List<Map<String, Object>> extractNamedList(Map<String, Object> root, String... names) {
        if (root == null || root.isEmpty()) {
            return new ArrayList<>();
        }

        for (String name : names) {
            Object value = root.get(name);
            if (value != null) {
                return asMapList(value);
            }
        }

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        if (value == null) {
            return new ArrayList<>();
        }

        if (value instanceof List<?> list) {
            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    normalized.add((Map<String, Object>) map);
                }
            }
            return normalized;
        }

        if (value instanceof Map<?, ?> map) {
            return Collections.singletonList((Map<String, Object>) map);
        }

        return new ArrayList<>();
    }

    private static List<Map<String, Object>> applyLimit(List<Map<String, Object>> values, int convertLimit) {
        if (values == null || values.isEmpty() || convertLimit <= 0 || values.size() <= convertLimit) {
            return values == null ? new ArrayList<>() : values;
        }
        return new ArrayList<>(values.subList(0, convertLimit));
    }

    private static boolean containsNodeLikeMetadata(Map<String, Object> map) {
        return map.containsKey("id") || map.containsKey("vertex") || map.containsKey("name") || map.containsKey("value");
    }

    private static boolean containsEdgeLikeMetadata(Map<String, Object> map) {
        return map.containsKey("source") || map.containsKey("from") || map.containsKey("target") || map.containsKey("to");
    }

    @SuppressWarnings("unchecked")
    private static GafferEntity toEntity(Map<String, Object> node) {
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
            properties.putAll((Map<String, Object>) map);
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

    @SuppressWarnings("unchecked")
    private static GafferEdge toEdge(Map<String, Object> edge) {
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
            properties.putAll((Map<String, Object>) map);
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

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !(value instanceof String s && s.isBlank())) {
                return value;
            }
        }
        return null;
    }

    private static Map<String, String> parseCsvLine(String line) {
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
                } else if (inQuotes) {
                    inQuotes = false;
                } else if (currentField.length() == 0) {
                    inQuotes = true;
                } else {
                    currentField.append(c);
                }
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

    private static String inferDefaultOutputPath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            return "neo4j/import/gaffer-batches";
        }
        String trimmed = inputPath.trim();
        return trimmed.endsWith(".json") ? trimmed.replaceFirst("\\.json$", "-gaffer.json") : "neo4j/import/gaffer-batches";
    }
}
