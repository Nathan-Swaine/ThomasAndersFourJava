package com.thomasandersfourjava.gaffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GafferJsonConverter {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) throws IOException {
        String csvPath = args.length > 0 ? args[0] : "matrix_characters.csv";
        String outputPath = args.length > 1 ? args[1] : "neo4j/import/matrix_characters.json";

        System.out.println("Converting CSV to Gaffer JSON...");
        System.out.println("Input: " + csvPath);
        System.out.println("Output: " + outputPath);

        GafferGraph graph = convertCsvToGaffer(csvPath);
        writeGafferJson(graph, outputPath);

        System.out.println("Conversion complete!");
        System.out.println("Entities: " + graph.getEntities().size());
        System.out.println("Edges: " + graph.getEdges().size());
    }

    public static GafferGraph convertCsvToGaffer(String csvPath) throws IOException {
        List<GafferEntity> entities = new ArrayList<>();
        List<GafferEdge> edges = new ArrayList<>();
        Set<String> seenCharacters = new HashSet<>();
        Set<String> seenActors = new HashSet<>();
        Set<String> seenMovies = new HashSet<>();

        try (BufferedReader reader = Files.newBufferedReader(Paths.get(csvPath))) {
            String headerLine = reader.readLine();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                Map<String, String> record = parseCsvLine(line);

                String character = record.get("character");
                String actor = record.get("actor");
                String actorBorn = record.get("actor_born");
                String movie = record.get("movie");
                String movieReleased = record.get("movie_released");
                String roleNotes = record.get("role_notes");

                if (character == null || character.isEmpty()) continue;

                // Create Character entity
                if (!seenCharacters.contains(character)) {
                    Map<String, Object> charProps = new HashMap<>();
                    charProps.put("name", character);
                    if (roleNotes != null && !roleNotes.isEmpty()) {
                        charProps.put("roleNotes", roleNotes);
                    }
                    entities.add(new GafferEntity("Character", character, charProps));
                    seenCharacters.add(character);
                }

                // Create Actor entity
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

                // Create Movie entity
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

                // Create ActedIn edge (Actor -> Character in Movie)
                if (actor != null && !actor.isEmpty()) {
                    Map<String, Object> edgeProps = new HashMap<>();
                    if (movie != null && !movie.isEmpty()) {
                        edgeProps.put("movie", movie);
                    }
                    edges.add(new GafferEdge("ActedIn", actor, character, edgeProps));
                }

                // Create ReleasedIn edge (Movie -> Movie's release info)
                if (movie != null && !movie.isEmpty()) {
                    Map<String, Object> releaseProps = new HashMap<>();
                    if (movieReleased != null && !movieReleased.isEmpty()) {
                        try {
                            releaseProps.put("year", Integer.parseInt(movieReleased));
                        } catch (NumberFormatException e) {
                            releaseProps.put("year", movieReleased);
                        }
                    }
                }
            }
        }

        return new GafferGraph(entities, edges);
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

    public static void writeGafferJson(GafferGraph graph, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), graph);
    }
}
