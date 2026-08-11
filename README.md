# ThomasAndersFourJava

This is a project file where I am experimenting with Neo4j. I also wanted a little matrix-inspired reference to give the project a bit of personality.

## Technologies
- Docker: 4.8
- Neo4j: 5.6
- OpenJDK: 17
- WSL: 2.7

## Project scaffold
This repository now contains a Maven-based Java 17 scaffold for a Neo4j experiment. It includes a simple application entry point for Neo4j, and some docker containers / services to facilitate it.

### Project structure
- `pom.xml`: Maven build **configuration** and dependencies for Neo4j and MongoDB.
- `src/main/java/com.thomasandersfourjava/neo4j/Neo4jExperimentApp.java`: minimal Java app that connects to Neo4j and runs a simple query.
- `src/main/resources/application.properties`: default Neo4j and MongoDB connection values.
- `docker-compose.yml`: local Neo4j and MongoDB container definitions.

### Getting started
1. Start the local databases:
   docker compose up -d

2. Insert a sample document into MongoDB (the sample app writes one document into the `sample` collection):
   mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.neo4j.MongoExperimentApp

3. Run the Neo4j sample application (separate test):
   mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.neo4j.Neo4jExperimentApp

### Verify the import
- Open the Neo4j Browser at http://localhost:7474 (use credentials `neo4j/neo4jpassword`), then run:
  MATCH (s:Sample) RETURN s LIMIT 25

- For the Docker seed utility (`neo4j-init` + `wait-and-import.sh`), run:
  MATCH (n) RETURN count(n) AS remaining

- Or inspect the `sample` collection in Mongo with mongosh:
  mongosh --host localhost --port 27017
  use test
  db.sample.find().pretty()

### Notes and tips
- If running the Java apps on the host machine they connect to Dockerized services via `localhost` (the defaults in application.properties will work).
- If a connection fails: check `docker ps` and `docker logs <container>` for error details, and ensure the host firewall isn't blocking ports.
- Neo4j CSV files are mounted to `/var/lib/neo4j/import/seed`, so `LOAD CSV` paths should use `file:///seed/...`.
- If WSL Git reports `Permission denied` for `neo4j/import`, reset ownership/mode from WSL:
  `sudo chown -R $(id -u):$(id -g) neo4j/import && sudo chmod -R u+rwX,go+rX neo4j/import`
