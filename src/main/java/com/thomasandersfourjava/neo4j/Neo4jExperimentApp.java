package com.thomasandersfourjava.neo4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;

public class Neo4jExperimentApp implements AutoCloseable {
    private final Driver driver;

    public Neo4jExperimentApp(String uri, String user, String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    public void run() {
        try (Session session = driver.session()) {
            Result result = session.run("RETURN 'Neo4j scaffold ready' AS message");
            Record record = result.single();
            System.out.println(record.get("message").asString());
        }
    }

    @Override
    public void close() {
        driver.close();
    }

    public static void main(String[] args) {
        Properties properties = loadProperties();
        String uri = System.getProperty("neo4j.uri", properties.getProperty("neo4j.uri", "bolt://localhost:7687"));
        String user = System.getProperty("neo4j.user", properties.getProperty("neo4j.user", "neo4j"));
        String password = System.getProperty("neo4j.password", properties.getProperty("neo4j.password", "neo4j"));

        try (Neo4jExperimentApp app = new Neo4jExperimentApp(uri, user, password)) {
            app.run();
        }
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try (InputStream stream = Neo4jExperimentApp.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load application.properties", ex);
        }
        return properties;
    }
}
