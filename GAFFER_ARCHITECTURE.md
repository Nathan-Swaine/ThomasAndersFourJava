# Gaffer Integration Architecture

## Overview
The Gaffer integration provides a batch-based pipeline for importing Gaffer-format graph data into Neo4j. This document describes the architecture, data model, and workflow.

## Gaffer Data Model

### Entities (Nodes)
Entities represent the primary nodes in the graph. In the Matrix dataset:

#### Character Entity
- **Vertex ID**: Character name (e.g., "Neo", "Trinity")
- **Properties**:
  - `name`: Display name
  - `roleNotes`: Description of the character's role

#### Actor Entity
- **Vertex ID**: Actor name (e.g., "Keanu Reeves")
- **Properties**:
  - `name`: Display name
  - `birthYear`: Birth year as integer

#### Movie Entity
- **Vertex ID**: Movie title (e.g., "The Matrix")
- **Properties**:
  - `title`: Display title
  - `releaseYear`: Release year as integer

### Edges (Relationships)
Edges represent connections between entities:

#### ActedIn Edge
- **Source**: Actor
- **Destination**: Character
- **Properties**:
  - `movie`: The movie in which the actor played the character
- **Meaning**: Connects an actor to the character they portrayed

## Gaffer JSON Format

The Gaffer JSON format is simple and extensible:

```json
{
  "entities": [
    {
      "group": "Character",
      "vertex": "Neo",
      "properties": {
        "name": "Neo",
        "roleNotes": "Protagonist; \"The One\""
      }
    }
  ],
  "edges": [
    {
      "group": "ActedIn",
      "source": "Keanu Reeves",
      "destination": "Neo",
      "properties": {
        "movie": "The Matrix"
      }
    }
  ]
}
```

**Key Points:**
- `group`: The entity/edge type (becomes Neo4j label or relationship type)
- `vertex`: Unique identifier for entities
- `source`/`destination`: Connection endpoints for edges
- `properties`: Flexible key-value pairs for metadata

## Implementation

### Java Classes

#### GafferEntity.java
Represents a Gaffer entity (node). Maps to a single Neo4j node with:
- Label from the `group` field
- Properties from the `properties` map
- Vertex ID stored as `id` property

#### GafferEdge.java
Represents a Gaffer edge (relationship). Maps to a Neo4j relationship with:
- Relationship type from the `group` field
- Source and destination vertices
- Properties from the `properties` map

#### GafferGraph.java
Container for all entities and edges. Provides structure for JSON serialization/deserialization.

#### GafferJsonConverter.java
Converts CSV data to Gaffer JSON format:
- Reads matrix_characters.csv
- Extracts unique entities (Characters, Actors, Movies)
- Creates edges (ActedIn relationships)
- Outputs Gaffer-formatted JSON

Usage:
```bash
mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.gaffer.GafferJsonConverter \
  -Dexec.args="matrix_characters.csv neo4j/import/matrix_characters.json"
```

#### GafferNeo4jImporter.java
Batch importer that streams Gaffer data into Neo4j:
- Reads Gaffer JSON file
- Batches entities and edges (default batch size: 100)
- Uses Neo4j MERGE operations for idempotency
- Creates nodes with labels and properties
- Creates relationships with types and properties

Usage:
```bash
mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.gaffer.GafferNeo4jImporter \
  -Dexec.args="neo4j/import/matrix_characters.json"
```

## Docker Integration

### Multi-Stage Build (Dockerfile)
The Dockerfile uses a multi-stage build for efficiency:
1. **Builder Stage**: Uses `maven:3.9-eclipse-temurin-17` to compile the project
2. **Runtime Stage**: Uses `eclipse-temurin:17-jre` to run only what's needed

### Ephemeral Service (docker-compose.yml)
The `gaffer-batch` service:
- Depends on the Neo4j service
- Builds the Docker image from the Dockerfile
- Sets Neo4j connection environment variables
- Runs the GafferJsonConverter and then the GafferNeo4jImporter on startup
- Exits after completion (no restart policy)

## Batch Processing Workflow

```
CSV Data (matrix_characters.csv)
    ↓
[GafferJsonConverter] → Gaffer JSON (matrix_characters.json)
    ↓
[Docker Build] → gaffer-pipeline Docker Image
    ↓
[docker compose up] → gaffer-batch Container
    ↓
[run-gaffer-pipeline.sh] → Conversion + Batch Processing
    ├─ Read Gaffer JSON
    ├─ Convert CSV to JSON
    ├─ Parse Entities
    ├─ Batch Entities (100 at a time)
    ├─ Execute MERGE for each batch
    ├─ Parse Edges
    ├─ Batch Edges (100 at a time)
    ├─ Execute MERGE for each batch
    └─ Container Exit
    ↓
Neo4j Graph Database (with imported data)
```

## Neo4j Queries After Import

### View all entities
```cypher
MATCH (n) RETURN DISTINCT labels(n) as type, count(n) as count
```

### View all relationships
```cypher
MATCH (n)-[r]->(m) RETURN DISTINCT type(r) as relationship, count(r) as count
```

### View character-actor relationships
```cypher
MATCH (a:Actor)-[r:ActedIn]->(c:Character)
RETURN a.name as actor, c.name as character, r.movie as movie
ORDER BY a.name
```

### Find all characters in a movie
```cypher
MATCH (c:Character)<-[:ActedIn {movie: "The Matrix"}]-(a:Actor)
RETURN a.name as actor, c.name as character
ORDER BY a.name
```

## Performance Considerations

### Batch Size
Default batch size is 100 entities/edges per transaction. Adjust in `GafferNeo4jImporter.java` if needed:
- **Smaller batches** (10-50): Lower memory usage, more transactions
- **Larger batches** (200-500): Better throughput, higher memory usage

### Idempotency
Uses Neo4j MERGE operations to ensure:
- Re-running the import doesn't create duplicates
- Safe for retries on failure
- Suitable for data updates/refreshes

### Scalability
Current implementation suitable for:
- Millions of nodes and relationships
- Limited by available Neo4j heap memory
- Can handle multiple batches sequentially

## Troubleshooting

### Common Issues

**Build Fails**
- Ensure Docker is running
- Check disk space for builder image
- Verify JDK 17+ available

**Connection Timeout**
- Ensure Neo4j container is fully started
- Check network connectivity: `docker network ls`
- Verify port 7687 not blocked

**Missing Data**
- Validate Gaffer JSON format with `jq matrix_characters.json`
- Check Neo4j logs: `docker logs thomas-anders-four-java-neo4j`
- Verify file permissions on mounted volumes

**Memory Issues**
- Reduce batch size in GafferNeo4jImporter
- Increase Docker memory limits
- Process smaller Gaffer JSON files

## Future Enhancements

- Stream processing for very large datasets
- Support for weighted edges
- Cypher-based preprocessing before import
- Incremental updates (detect changes, only import deltas)
- Support for additional Gaffer properties and types
