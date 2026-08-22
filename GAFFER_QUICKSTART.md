# Gaffer Integration - Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Prerequisites
- Docker Desktop (or Docker + Docker Compose)
- Maven 3.9+ (for manual compilation)
- Java 17+ (for local testing)

### Step 1: Start Services
```bash
cd ThomasAndersFourJava
docker compose up -d
```

This starts:
- Neo4j database (port 7687)
- Gaffer batch container (converts CSV, then imports automatically, exits when done)

### Step 2: Verify Import
```bash
# Watch the batch importer logs
docker logs -f thomas-anders-four-java-gaffer-batch

# Should see:
# - "Starting Gaffer conversion..."
# - "Conversion complete!"
# - "Starting Gaffer to Neo4j import..."
# - "Loaded Gaffer graph: ..."
# - "Importing entities..."
# - "Entities imported successfully!"
# - "Importing edges..."
# - "Edges imported successfully!"
# - "Import complete!"
```

### Step 3: Query the Data
1. Open browser: http://localhost:7474
2. Login: neo4j / neo4jpassword
3. Run query:
   ```cypher
   MATCH (c:Character)-[r:ActedIn]-(a:Actor)
   RETURN c, r, a LIMIT 25
   ```

You should see Matrix characters connected to actors!

## 📊 Sample Queries

### See all entities
```cypher
MATCH (n) RETURN DISTINCT labels(n) as type, count(n) as count
```

### Find actors who played characters
```cypher
MATCH (a:Actor)-[r:ActedIn]->(c:Character)
RETURN a.name as actor, c.name as character
ORDER BY a.name
```

### Count relationships
```cypher
MATCH (n)-[r]->(m) RETURN type(r) as relationship, count(r) as count
```

## 🛠️ Manual Operations

### Convert CSV to Gaffer JSON
```bash
mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.gaffer.GafferJsonConverter \
  -Dexec.args="matrix_characters.csv neo4j/import/matrix_characters.json"
```

### Run Importer Manually
```bash
mvn exec:java -Dexec.mainClass=com.thomasandersfourjava.gaffer.GafferNeo4jImporter \
  -Dexec.args="neo4j/import/matrix_characters.json"
```

### Test Integration
```bash
bash test-gaffer-integration.sh
```

### Tear Down the Stack
```bash
bash teardown.sh
```

## 📁 What's Included

| File | Purpose |
|------|---------|
| `src/main/java/com/thomasandersfourjava/gaffer/` | Gaffer Java implementation |
| `matrix_characters.csv` | Source data for conversion |
| `Dockerfile` | Container image for the Gaffer pipeline |
| `run-gaffer-pipeline.sh` | Runs CSV conversion then batch import on container start |
| `teardown.sh` | Tears down the Docker stack and volumes |
| `docker-compose.yml` | Service definitions |
| `README.md` | Full documentation |
| `GAFFER_ARCHITECTURE.md` | Technical deep dive |

## 🔧 Configuration

For Docker/runtime settings, use `project.env` (single source of truth):
```env
NEO4J_URI=bolt://neo4j:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=neo4jpassword
GAFFER_BATCH_SIZE=100
GAFFER_CONVERT_LIMIT=1000
IMPORT_DIR=/data/out
```

For local host Java fallback values, use `src/main/resources/application.properties`:
```properties
neo4j.uri=bolt://localhost:7687
neo4j.user=neo4j
neo4j.password=neo4jpassword
```

## ❓ Troubleshooting

**Q: Batch service won't start**
- Check Docker running: `docker ps`
- Check logs: `docker logs thomas-anders-four-java-gaffer-batch`
- Ensure Neo4j is ready: `docker logs thomas-anders-four-java-neo4j | head -20`

**Q: No data in Neo4j**
- Verify CSV file exists: `ls matrix_characters.csv`
- Check Neo4j connection: `docker exec thomas-anders-four-java-neo4j cypher-shell -u neo4j -p neo4jpassword "RETURN 1"`
- Query count: `MATCH (n) RETURN count(n)`

**Q: Connection timeout**
- Wait for Neo4j: `docker logs thomas-anders-four-java-neo4j | grep "started"`
- Check network: `docker network ls`
- Verify ports: `netstat -an | grep 7687`

**Q: How do I add new data?**
- Edit `matrix_characters.csv`, OR
- Run GafferJsonConverter manually, OR
- Update the converter logic for new data sources

## 📚 Learn More

- Full docs: See `README.md`
- Architecture: See `GAFFER_ARCHITECTURE.md`
- Source code: See `src/main/java/com/thomasandersfourjava/gaffer/`

## ✅ Next Steps

1. ✅ Services running
2. ✅ Data imported
3. Next: Explore Neo4j Browser
4. Next: Create custom Gaffer JSON for your data
5. Next: Modify converter for your data sources

---

**Happy graphing! 🎉**
