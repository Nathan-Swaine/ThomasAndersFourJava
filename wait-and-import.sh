#!/bin/sh
# wait-and-import.sh
# POSIX-compatible script to wait for Neo4j to become available and run any .cypher files

set -eu

NEO4J_URI=${NEO4J_URI:-bolt://neo4j:7687}
AUTH=${NEO4J_AUTH:-neo4j/neo4jpassword}
# Split AUTH into user and pass (format: user/password)
USER=$(printf "%s" "$AUTH" | awk -F/ '{print $1}')
PASS=$(printf "%s" "$AUTH" | awk -F/ '{print $2}')

IMPORT_DIR=${IMPORT_DIR:-/import/data}

# Fail fast if cypher-shell is not available
if ! command -v cypher-shell >/dev/null 2>&1; then
  echo "Error: cypher-shell not found in PATH"
  exit 1
fi

echo "Waiting for Neo4j at $NEO4J_URI ..."
# Wait up to 60 seconds for Neo4j to respond
i=1
while [ "$i" -le 60 ]; do
  if cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" "RETURN 1" >/dev/null 2>&1; then
    echo "Neo4j is available"
    break
  fi
  echo "Waiting for bolt... ($i/60)"
  sleep 1
  i=$((i + 1))
done

if [ "$i" -gt 60 ]; then
  echo "Timed out waiting for Neo4j at $NEO4J_URI"
  exit 1
fi

# Run imports if directory exists
if [ -d "$IMPORT_DIR" ]; then
  found=0
  for f in "$IMPORT_DIR"/*.cypher; do
    [ -e "$f" ] || continue
    found=1
    echo "Running $f"
    if ! cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" < "$f"; then
      echo "Failed to run $f"
      exit 1
    fi
  done

  if [ "$found" -eq 0 ]; then
    echo "No .cypher files in $IMPORT_DIR"
  fi
else
  echo "Import directory $IMPORT_DIR does not exist"
fi

echo "Import finished."
