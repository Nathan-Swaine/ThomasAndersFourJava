package com.thomasandersfourjava.neo4j;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.bson.Document;

public class MongoExperimentApp implements AutoCloseable {
    private final MongoClient client;
    private final MongoDatabase database;

    public MongoExperimentApp(String uri, String databaseName) {
        this.client = MongoClients.create(uri);
        this.database = client.getDatabase(databaseName);
    }

    public void run() {
        MongoCollection<Document> collection = database.getCollection("sample");
        collection.insertOne(new Document("message", "Mongo scaffold ready"));
        long count = collection.countDocuments();
        System.out.printf("Connected to MongoDB database '%s' and collection '%s' contains %d document(s).%n",
                database.getName(), collection.getNamespace().getCollectionName(), count);
    }

    @Override
    public void close() {
        client.close();
    }

    public static void main(String[] args) {
        Properties properties = loadProperties();
        String uri = System.getProperty("mongodb.uri", properties.getProperty("mongodb.uri", "mongodb://localhost:27017"));
        String databaseName = System.getProperty("mongodb.database", properties.getProperty("mongodb.database", "test"));

        try (MongoExperimentApp app = new MongoExperimentApp(uri, databaseName)) {
            app.run();
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = MongoExperimentApp.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load application.properties", ex);
        }
        return properties;
    }
}
