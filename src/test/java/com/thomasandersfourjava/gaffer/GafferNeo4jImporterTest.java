package com.thomasandersfourjava.gaffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;

class GafferNeo4jImporterTest {

    @Test
    void writeNodeShardSupportsFlatRootArrays() throws Exception {
        Path source = Files.createTempFile("gaffer-importer", ".json");
        Path shard = Path.of(source + ".shard");
        Files.writeString(source, """
                [
                  { "id": "Alice", "type": "Person", "name": "Alice" },
                  { "id": "Bob", "type": "Person", "name": "Bob" }
                ]
                """);

        try (GafferNeo4jImporter importer = new GafferNeo4jImporter(noOpDriver(), 10, false)) {
            Method writeNodeShard = GafferNeo4jImporter.class.getDeclaredMethod("writeNodeShard", String.class, String.class);
            writeNodeShard.setAccessible(true);
            writeNodeShard.invoke(importer, source.toString(), shard.toString());
        }

        String shardContent = Files.readString(shard);
        assertTrue(shardContent.contains("\"Alice\""));
        assertTrue(shardContent.contains("\"Bob\""));
    }

    @Test
    void chunkedImportKeepsNodePhaseUntilShardIsExhausted() throws Exception {
        Path source = Files.createTempFile("gaffer-chunked", ".json");
        Files.writeString(source, """
                [
                  { "id": " ", "type": "Person" },
                  { "id": "Alice", "type": "Person" },
                  { "id": "Bob", "type": "Person" }
                ]
                """);
        Path progress = Files.createTempFile("gaffer-progress", ".properties");
        Files.deleteIfExists(progress);

        GafferNeo4jImporter importer = new GafferNeo4jImporter(noOpDriver(), 10, false);
        invokeChunkedImport(importer, source, 2, progress);

        Properties state = loadProperties(progress);
        assertEquals("nodes", state.getProperty("stage"));
        assertEquals("1", state.getProperty("totalNodesImported"));
        assertTrue(Files.exists(Path.of(source + ".shard")));
    }

    @Test
    void chunkedImportRebuildsShardWhenSourceChanges() throws Exception {
        Path source = Files.createTempFile("gaffer-source", ".json");
        Files.writeString(source, """
                [
                  { "id": "Alice", "type": "Person" },
                  { "id": "Bob", "type": "Person" }
                ]
                """);
        Path progress = Files.createTempFile("gaffer-progress", ".properties");
        Files.deleteIfExists(progress);
        Path shard = Path.of(source + ".shard");

        GafferNeo4jImporter importer = new GafferNeo4jImporter(noOpDriver(), 10, false);
        invokeChunkedImport(importer, source, 1, progress);

        Files.writeString(source, """
                [
                  { "id": "Carol", "type": "Person" },
                  { "id": "Danielle", "type": "Person" }
                ]
                """);
        Files.setLastModifiedTime(source, FileTime.fromMillis(System.currentTimeMillis() + 2_000));

        invokeChunkedImport(importer, source, 1, progress);

        String shardContent = Files.readString(shard);
        assertTrue(shardContent.contains("\"Carol\""));
        assertFalse(shardContent.contains("\"Alice\""));
    }

    @Test
    void parseCsvLineTreatsMidFieldQuotesAsLiteralCharacters() throws Exception {
        try (GafferNeo4jImporter importer = new GafferNeo4jImporter(noOpDriver(), 10, false)) {
            Method method = GafferNeo4jImporter.class.getDeclaredMethod("parseCsvLine", String.class);
            method.setAccessible(true);

            @SuppressWarnings("unchecked")
            var row = (java.util.Map<String, String>) method.invoke(
                    importer, "Neo,Keanu Reeves,1964,The Matrix,1999,Neo is \"The One\"");

            assertEquals("Neo is \"The One\"", row.get("role_notes"));
        }
    }

    private static void invokeChunkedImport(GafferNeo4jImporter importer, Path source, int convertLimit, Path progress)
            throws Exception {
        Method method = GafferNeo4jImporter.class.getDeclaredMethod(
                "importChunkedJsonWithProgress", String.class, int.class, String.class);
        method.setAccessible(true);
        try {
            method.invoke(importer, source.toString(), convertLimit, progress.toString());
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private static Properties loadProperties(Path progress) throws Exception {
        Properties properties = new Properties();
        try (var input = Files.newInputStream(progress)) {
            properties.load(input);
        }
        return properties;
    }

    private static Driver noOpDriver() {
        Result result = (Result) Proxy.newProxyInstance(
                Result.class.getClassLoader(),
                new Class<?>[]{Result.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));

        TransactionContext tx = (TransactionContext) Proxy.newProxyInstance(
                TransactionContext.class.getClassLoader(),
                new Class<?>[]{TransactionContext.class},
                (proxy, method, args) -> {
                    if ("run".equals(method.getName())) {
                        return result;
                    }
                    return defaultValue(method.getReturnType());
                });

        Session session = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, args) -> {
                    if ("executeWrite".equals(method.getName())) {
                        @SuppressWarnings("unchecked")
                        TransactionCallback<Object> callback = (TransactionCallback<Object>) args[0];
                        return callback.execute(tx);
                    }
                    return defaultValue(method.getReturnType());
                });

        return (Driver) Proxy.newProxyInstance(
                Driver.class.getClassLoader(),
                new Class<?>[]{Driver.class},
                (proxy, method, args) -> {
                    if ("session".equals(method.getName())) {
                        return session;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0.0d;
        }
        if (returnType == Float.TYPE) {
            return 0.0f;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }
}
