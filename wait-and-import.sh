#!/bin/sh
set -eu

NEO4J_URI=${NEO4J_URI:-bolt://neo4j:7687}
AUTH=${NEO4J_AUTH:-neo4j/neo4jpassword}
USER=$(printf "%s" "$AUTH" | awk -F/ '{print $1}')
PASS=$(printf "%s" "$AUTH" | awk -F/ '{print $2}')

IMPORT_DIR=${IMPORT_DIR:-/import/data}

echo "Waiting for Neo4j at $NEO4J_URI ..."
for i in $(seq 1 60); do
  if cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" "RETURN 1" >/dev/null 2>&1 ; then
    echo "Neo4j is available"
    break
  fi
  echo "Waiting for bolt... ($i/60)"
  sleep 1
done

if [ -d "$IMPORT_DIR" ]; then
  found=0
  for f in "$IMPORT_DIR"/*.cypher; do
    [ -e "$f" ] || continue
    found=1
    echo "Running $f"
    cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" < "$f" || { echo "Failed to run $f"; exit 1; }
  done
  if [ "$found" -eq 0 ]; then
    echo "No .cypher files in $IMPORT_DIR"
  fi
else
  echo "Import directory $IMPORT_DIR does not exist"
fi

echo "Import finished."