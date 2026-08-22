# ThomasAndersFourJava

This is a project file where I am experimenting with Neo4j. I also wanted a little matrix-inspired reference to give the project a bit of personality.

## Technologies
- Docker: 4.8
- Neo4j: 5.6
- Gaffer
- OpenJDK: 17
- WSL: 2.7

## Project scaffold
This repository now contains a Maven-based Java 17 scaffold for a Neo4j experiment. It includes a simple application entry point for Neo4j, and some docker containers / services to facilitate it.

### Project structure
- `pom.xml`: Maven build **configuration** and dependencies for Neo4j and Jackson.
- `src/main/java/com.thomasandersfourjava/neo4j/Neo4jExperimentApp.java`: minimal Java app that connects to Neo4j and runs a simple query.
- `src/main/java/com.thomasandersfourjava/gaffer/`: Gaffer integration package
  - `GafferEntity.java`: Represents Gaffer entities (nodes)
  - `GafferEdge.java`: Represents Gaffer edges (relationships)
  - `GafferGraph.java`: Container for entities and edges
  - `GafferJsonConverter.java`: Converts CSV data to Gaffer JSON format
  - `GafferNeo4jImporter.java`: Batch importer that streams Gaffer data into Neo4j
- `src/main/resources/application.properties`: default Neo4j and MongoDB connection values.
- `project.env`: centralized Docker/runtime configuration for Neo4j + Gaffer services.
- `docker-compose.yml`: local Neo4j and Gaffer pipeline container definitions.
- `Dockerfile`: Multi-stage build for the Gaffer pipeline container.
- `run-gaffer-pipeline.sh`: container startup script that streams CSV/JSON data into Neo4j via the Gaffer importer.
- `teardown.sh`: tears down the Gaffer stack and removes volumes.
- `matrix_characters.csv`: source data used for conversion.

### Getting started
1. Start the local databases:
   Edit `project.env` first if you want to change connection settings, import directory, batch size, or conversion limits.
   docker compose up -d

2. On `docker compose up -d`, `neo4j-init` runs in incremental mode by default (`RESET_GRAPH=false`) and imports only changed `.cypher`, `.json`, and `.csv` files found in `neo4j/import`.
   - File state is tracked in `neo4j/import/.import-state/manifest.tsv`.
   - Set `RESET_GRAPH=true` in `project.env` when you explicitly want a full wipe + full reimport.
   Monitor its progress with:
   docker logs -f thomas-anders-four-java-neo4j-init

3. Tear down the full Gaffer stack when you are done:
   bash teardown.sh

4. Run the Neo4j sample application (separate test):
   mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.neo4j.Neo4jExperimentApp

### Gaffer Workflow 
The Gaffer integration provides a batch-based data import pipeline:

1. **Data Source**: Matrix characters CSV stored in `matrix_characters.csv`
   - Entities: Character, Actor, Movie
   - Edges: ActedIn relationships

2. **Conversion**: Convert CSV to Gaffer JSON using the converter:
   mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.gaffer.GafferJsonConverter -Dexec.args="matrix_characters.csv neo4j/import/matrix_characters.json"
   - The importer also accepts `graph.json` documents that contain both entity and edge arrays, including a nested `graph` wrapper.

3. **Batch Import**: The `gaffer-batch` Docker service streams and imports Gaffer data when run with its compose profile:
   docker compose --profile gaffer-manual up gaffer-batch
   - Reads CSV from `matrix_characters.csv`
   - Batches entities and edges for efficient Neo4j insertion (`gaffer.batch.size` / `GAFFER_BATCH_SIZE`)
   - Automatically prepares per-label unique constraints on `id` before entity writes, so node `MERGE` and edge endpoint `MATCH` lookups are index-backed
   - If pre-existing duplicates exist for the same label + `id`, Neo4j surfaces a constraint creation error and the import stops
   - Supports optional conversion limit (`gaffer.convert.limit` / `GAFFER_CONVERT_LIMIT`)
   - Progress output defaults to line-based logs (Docker-friendly). Set `gaffer.progress.inline=true` or `GAFFER_PROGRESS_INLINE=true` only when you explicitly want inline terminal updates.
   - When `GAFFER_CONVERT_LIMIT` is set for JSON imports, the import job now keeps resuming chunk-by-chunk in the same run until the progress file reaches `stage=done` (progress is tracked under `neo4j/import/.import-state/`)
   - After the final node chunk, the importer performs one relationship pass so edges are created only after their nodes exist
   - Streams data into Neo4j using the Java driver
   - Service exits after completion (ephemeral)

4. **Verification**: Query the imported data in Neo4j Browser:
   MATCH (c:Character)-[r:ActedIn]-(a:Actor) RETURN c, r, a LIMIT 25

### Verify the import
- Open the Neo4j Browser at http://localhost:7474 (use credentials `neo4j/neo4jpassword`), then run:
  MATCH (s:Sample) RETURN s LIMIT 25

- To find all nodes in the graph, run:
  MATCH (n) RETURN n

- For the Docker seed utility (`neo4j-init` + `wait-and-import.sh`), run:
  MATCH (n) RETURN count(n) AS remaining
- Place seed `.cypher`, `.json`, and `.csv` files in `neo4j/import` (mounted as `/data` in `neo4j-init`).
- `neo4j-init` preserves existing graph data by default and skips unchanged import files.
- To force a clean rebuild, set `RESET_GRAPH=true` in `project.env`, run `docker compose up -d --force-recreate neo4j-init`, and then set it back to `false`.
- Optional: set `GAFFER_CONVERT_LIMIT` on `neo4j-init` only when intentionally chunking imports; progress still persists in `neo4j/import/.import-state/` so interrupted runs can resume safely.

### Notes and tips
- If running the Java apps on the host machine they connect to Dockerized services via `localhost` (the defaults in application.properties will work).
- If a connection fails: check `docker ps` and `docker logs <container>` for error details, and ensure the host firewall isn't blocking ports.
- If Neo4j exits immediately with `Read-only file system` errors, make sure the `./neo4j/import` volume is mounted read-write in `docker-compose.yml`. Neo4j 5.x adjusts ownership in the import directory during startup.
- Neo4j CSV files are mounted to `/var/lib/neo4j/import/seed`, so `LOAD CSV` paths should use `file:///seed/...`.
- If WSL Git reports `Permission denied` for `neo4j/import`, reset ownership/mode from WSL:
  `sudo chown -R $(id -u):$(id -g) neo4j/import && sudo chmod -R u+rwX,go+rX neo4j/import`

### Gaffer Integration Troubleshooting
- **Gaffer batch service not starting**: Ensure Docker has sufficient memory (≥2GB recommended) and check `docker logs thomas-anders-four-java-gaffer-batch` for errors.
- **"Cannot find import source file"**: Verify your CSV or JSON source file path is mounted and reachable by the container.
- **Batch import fails with connection timeout**: Ensure Neo4j container is fully started before the batch service (depends_on should handle this, but `docker logs neo4j` can help verify).
- **Entities or edges not appearing in Neo4j**: Check that the Gaffer JSON format is valid (use `jq` to validate structure) and verify with `MATCH (n) RETURN count(n)` in Neo4j Browser.
- **Building Dockerfile locally**: Use `docker build -t gaffer-importer .` to test the multi-stage build locally.
