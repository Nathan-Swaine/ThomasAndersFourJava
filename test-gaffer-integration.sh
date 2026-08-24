#!/bin/bash
# Gaffer Integration Test Script
# This script verifies the Gaffer batch importer integration with Neo4j

echo "=== Gaffer Integration Test ==="
echo

# Step 1: Verify file structure
echo "[1/5] Verifying file structure..."
if [ -f "Dockerfile" ]; then
    echo "✓ Dockerfile found"
else
    echo "✗ Dockerfile not found"
    exit 1
fi

if [ -f "social_media_usage.csv" ]; then
    echo "✓ Gaffer source CSV found"
else
    echo "✗ Gaffer source CSV not found"
    exit 1
fi

if [ -f "src/main/java/com/thomasandersfourjava/gaffer/GafferNeo4jImporter.java" ]; then
    echo "✓ GafferNeo4jImporter found"
else
    echo "✗ GafferNeo4jImporter not found"
    exit 1
fi
echo

# Step 2: Build and start Docker services
echo "[2/5] Starting Docker services..."
docker compose up -d neo4j
sleep 5
docker compose up -d gaffer-batch
echo "✓ Services started"
echo

# Step 3: Wait for Neo4j to be ready
echo "[3/5] Waiting for Neo4j to be ready..."
for i in {1..30}; do
    if docker exec thomas-anders-four-java-neo4j cypher-shell -u neo4j -p neo4jpassword "RETURN 1" 2>/dev/null; then
        echo "✓ Neo4j is ready"
        break
    fi
    echo "  Waiting... ($i/30)"
    sleep 1
done
echo

# Step 4: Monitor batch import
echo "[4/5] Monitoring Gaffer batch import..."
sleep 3
docker logs thomas-anders-four-java-gaffer-batch
echo

# Step 5: Verify data import
echo "[5/5] Verifying data in Neo4j..."
result=$(docker exec thomas-anders-four-java-neo4j cypher-shell -u neo4j -p neo4jpassword \
    "MATCH (n) RETURN count(n) AS node_count" 2>/dev/null | tail -1)

if [ ! -z "$result" ] && [ "$result" -gt "0" ]; then
    echo "✓ Data imported successfully!"
    echo "  Node count: $result"
    
    # Show sample data
    echo
    echo "  Sample Characters:"
    docker exec thomas-anders-four-java-neo4j cypher-shell -u neo4j -p neo4jpassword \
        "MATCH (c:Character) RETURN c.name LIMIT 3" 2>/dev/null | grep -v "^name" | grep -v "^---"
    
    echo
    echo "  Sample Actors:"
    docker exec thomas-anders-four-java-neo4j cypher-shell -u neo4j -p neo4jpassword \
        "MATCH (a:Actor) RETURN a.name LIMIT 3" 2>/dev/null | grep -v "^name" | grep -v "^---"
else
    echo "✗ No data found in Neo4j"
    exit 1
fi

echo
echo "=== Test Complete ==="
echo "Open Neo4j Browser at http://localhost:7474 to explore the data"
echo "Query: MATCH (c:Character)-[r:ActedIn]-(a:Actor) RETURN c, r, a LIMIT 25"
