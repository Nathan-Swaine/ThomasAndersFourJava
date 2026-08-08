# ThomasAndersFourJava

This is a project file where I am experimenting with Neo4j. I also wanted a little matrix-inspired reference to give the project a bit of personality.

## Technologies
- Docker: 4.8
- Neo4j: 5.6
- OpenJDK: 17
- WSL: 2.7

## Project scaffold
This repository now contains a Maven-based Java 17 scaffold for a Neo4j experiment. It includes a simple application entry point, a Docker Compose setup for a local Neo4j instance, and default connection settings.

### Project structure
- `pom.xml`: Maven build configuration and Neo4j Java driver dependency.
- `src/main/java/com/thomasandersfourjava/neo4j/Neo4jExperimentApp.java`: minimal Java app that connects to Neo4j and runs a simple query.
- `src/main/resources/application.properties`: default Neo4j connection values.
- `docker-compose.yml`: local Neo4j container definition.

### Getting started
1. Start Neo4j locally:
   `docker compose up -d`
2. Run the sample application:
   `mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.neo4j.Neo4jExperimentApp`
3. If you want to override the connection settings, pass Java system properties:
   `mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.neo4j.Neo4jExperimentApp -Dneo4j.uri=bolt://localhost:7687 -Dneo4j.user=neo4j -Dneo4j.password=neo4j`

### Notes
- The default credentials are `neo4j/neo4j`.
- The application will print a simple Neo4j query result once the database is reachable.