package com.thomasandersfourjava.neo4j;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class MongoToNeoImporter implements AutoCloseable {
    private final Driver neoDriver;
    private final MongoClient mongoClient;
    private final MongoDatabase mongoDatabase;

    public MongoToNeoImporter(String neoUri, String neoUser, String neoPassword, String mongoUri, String mongoDbName) {
        this.neoDriver = GraphDatabase.driver(neoUri, AuthTokens.basic(neoUser, neoPassword));
        this.mongoClient = MongoClients.create(mongoUri);
        this.mongoDatabase = mongoClient.getDatabase(mongoDbName);
    }

    public void importSampleCollection(String collectionName) {
        MongoCollection<Document> collection = mongoDatabase.getCollection(collectionName);
        List<Document> docs = new ArrayList<>();
        collection.find().into(docs);

        if (docs.isEmpty()) {
            System.out.println("Mongo collection is empty — nothing to import.");
            return;
        }

        try (Session session = neoDriver.session()) {
            for (Document doc : docs) {
                final String message = doc.getString("message");
                final String id = doc.getObjectId("_id").toHexString();

                session.writeTransaction(new TransactionWork<Void>() {
                    @Override
                    public Void execute(Transaction tx) {
                        tx.run("MERGE (s:Sample {mongoId: $id}) SET s.message = $message", 
                                org.neo4j.driver.Values.parameters("id", id, "message", message));
                        return null;
                    }
                });

                System.out.printf("Imported Mongo document _id=%s into Neo4j node Sample(mongoId=%s).%n", id, id);
            }
        }
    }

    @Override
    public void close() {
        try {
            mongoClient.close();
        } finally {
            neoDriver.close();
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = MongoToNeoImporter.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load application.properties", ex);
        }
        return properties;
    }

    public static void main(String[] args) {
        Properties p = loadProperties();
        String neoUri = System.getProperty("neo4j.uri", p.getProperty("neo4j.uri", "bolt://localhost:7687"));
        String neoUser = System.getProperty("neo4j.user", p.getProperty("neo4j.user", "neo4j"));
        String neoPass = System.getProperty("neo4j.password", p.getProperty("neo4j.password", "neo4j"));

        String mongoUri = System.getProperty("mongodb.uri", p.getProperty("mongodb.uri", "mongodb://localhost:27017"));
        String mongoDb = System.getProperty("mongodb.database", p.getProperty("mongodb.database", "test"));

        try (MongoToNeoImporter importer = new MongoToNeoImporter(neoUri, neoUser, neoPass, mongoUri, mongoDb)) {
            importer.importSampleCollection("sample");
        }
    }
}
