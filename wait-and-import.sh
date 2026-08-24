#!/bin/sh
# wait-and-import.sh
# POSIX-compatible script to wait for Neo4j, reset the database, then import .cypher/.csv/.json files.

set -eu

NEO4J_URI=${NEO4J_URI:-bolt://neo4j:7687}
AUTH=${NEO4J_AUTH:-neo4j/neo4jpassword}
USER=${AUTH%%/*}
PASS=${AUTH#*/}
if [ "$USER" = "$AUTH" ] || [ -z "$USER" ] || [ -z "$PASS" ]; then
  echo "Error: NEO4J_AUTH must be in the form user/password"
  exit 1
fi
IMPORT_DIR=${IMPORT_DIR:-/data}
CLASSPATH=${CLASSPATH:-/app/app.jar:/app/libs/*}
RESET_GRAPH=${RESET_GRAPH:-false}
IMPORT_STATE_DIR=${IMPORT_STATE_DIR:-$IMPORT_DIR/.import-state}
IMPORT_MANIFEST_FILE=${IMPORT_MANIFEST_FILE:-$IMPORT_STATE_DIR/manifest.tsv}

if ! command -v cypher-shell >/dev/null 2>&1; then
  echo "Error: cypher-shell not found in PATH"
  exit 1
fi

if ! command -v java >/dev/null 2>&1; then
  echo "Error: java not found in PATH"
  exit 1
fi

if [ ! -f "/app/app.jar" ]; then
  echo "Error: /app/app.jar not found; Java import tools are unavailable"
  exit 1
fi

echo "Waiting for Neo4j at $NEO4J_URI ..."
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

mkdir -p "$IMPORT_DIR"
mkdir -p "$IMPORT_STATE_DIR"
touch "$IMPORT_MANIFEST_FILE"

if [ "$RESET_GRAPH" = "true" ]; then
  echo "RESET_GRAPH=true: clearing existing graph data before import..."
  if ! cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" "MATCH (n) DETACH DELETE n"; then
    echo "Failed to clear existing graph data"
    exit 1
  fi
  : > "$IMPORT_MANIFEST_FILE"
else
  echo "Incremental mode enabled (RESET_GRAPH=false): preserving existing graph data."
fi

file_fingerprint() {
  target_file="$1"
  sha256sum "$target_file" | awk '{print $1}'
}

should_import_file() {
  target_file="$1"
  current_fingerprint="$(file_fingerprint "$target_file")"
  if awk -F '\t' -v f="$target_file" -v p="$current_fingerprint" '$1==f && $2==p {found=1} END {exit(found ? 0 : 1)}' "$IMPORT_MANIFEST_FILE"; then
    return 1
  fi
  return 0
}

record_imported_file() {
  target_file="$1"
  current_fingerprint="$(file_fingerprint "$target_file")"
  temp_manifest="${IMPORT_MANIFEST_FILE}.tmp"
  awk -F '\t' -v f="$target_file" '$1!=f' "$IMPORT_MANIFEST_FILE" > "$temp_manifest"
  printf '%s\t%s\n' "$target_file" "$current_fingerprint" >> "$temp_manifest"
  mv "$temp_manifest" "$IMPORT_MANIFEST_FILE"
}

uses_chunk_progress() {
  target_file="$1"
  case "$target_file" in
    *.json)
      [ -n "${GAFFER_CONVERT_LIMIT:-}" ] && [ -z "${GAFFER_CONVERT_OFFSET:-}" ]
      ;;
    *)
      return 1
      ;;
  esac
}

progress_file_for_source() {
  target_file="$1"
  printf '%s/%s.progress.properties\n' "$IMPORT_STATE_DIR" "$(basename "$target_file")"
}

chunk_progress_complete() {
  progress_file="$1"
  [ -f "$progress_file" ] && grep -q '^stage=done$' "$progress_file"
}

chunk_progress_signature() {
  progress_file="$1"
  if [ ! -f "$progress_file" ]; then
    printf 'missing'
    return
  fi
  awk -F '=' '
    /^stage=/ { stage=$2 }
    /^totalNodesImported=/ { total=$2 }
    /^shardNodesByteOffset=/ { offset=$2 }
    END { printf "stage=%s,total=%s,offset=%s", stage, total, offset }
  ' "$progress_file"
}

run_chunked_json_until_complete() {
  source_json="$1"
  progress_file="$(progress_file_for_source "$source_json")"
  previous_signature=""
  pass=1

  while :; do
    echo "Resuming chunked JSON import with progress file $progress_file (pass $pass)"
    if ! GAFFER_PROGRESS_FILE="$progress_file" java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferNeo4jImporter "$source_json"; then
      return 1
    fi
    if chunk_progress_complete "$progress_file"; then
      echo "Chunked JSON import complete for $source_json"
      return 0
    fi

    current_signature="$(chunk_progress_signature "$progress_file")"
    if [ "$current_signature" = "missing" ]; then
      echo "Chunked progress file $progress_file was not created; cannot continue resumable import."
      return 1
    fi
    if [ "$current_signature" = "$previous_signature" ]; then
      echo "Chunked JSON import did not advance (state: $current_signature); aborting to avoid an endless loop."
      return 1
    fi
    previous_signature="$current_signature"
    pass=$((pass + 1))
  done
}

import_json_file() {
  source_json="$1"
  echo "Streaming JSON import: $source_json"
  if uses_chunk_progress "$source_json"; then
    run_chunked_json_until_complete "$source_json"
    return
  fi
  java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferNeo4jImporter "$source_json"
}

import_csv_file() {
  source_csv="$1"
  echo "Streaming CSV import: $source_csv"
  java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferNeo4jImporter "$source_csv"
}

found=0
for f in "$IMPORT_DIR"/*.cypher; do
  [ -e "$f" ] || continue
  found=1
  if ! should_import_file "$f"; then
    echo "Skipping unchanged file: $f"
    continue
  fi
  echo "Running $f"
  if ! cypher-shell -a "$NEO4J_URI" -u "$USER" -p "$PASS" < "$f"; then
    echo "Failed to run $f"
    exit 1
  fi
  record_imported_file "$f"
done

for f in "$IMPORT_DIR"/*.json; do
  [ -e "$f" ] || continue
  found=1
  if uses_chunk_progress "$f"; then
    if ! import_json_file "$f"; then
      echo "Failed to import JSON file $f"
      exit 1
    fi
    progress_file="$(progress_file_for_source "$f")"
    if chunk_progress_complete "$progress_file"; then
      record_imported_file "$f"
    fi
    continue
  fi
  if ! should_import_file "$f"; then
    echo "Skipping unchanged file: $f"
    continue
  fi
  if ! import_json_file "$f"; then
    echo "Failed to import JSON file $f"
    exit 1
  fi
  record_imported_file "$f"
done

for f in "$IMPORT_DIR"/*.csv; do
  [ -e "$f" ] || continue
  found=1
  if ! should_import_file "$f"; then
    echo "Skipping unchanged file: $f"
    continue
  fi
  if ! import_csv_file "$f"; then
    echo "Failed to import CSV file $f"
    exit 1
  fi
  record_imported_file "$f"
done

if [ "$found" -eq 0 ]; then
  echo "No .cypher, .json, or .csv files in $IMPORT_DIR"
fi

echo "Import finished."
