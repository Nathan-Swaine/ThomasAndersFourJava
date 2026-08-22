#!/bin/sh

set -eu

IMPORT_DIR=${IMPORT_DIR:-/data}
IMPORT_STATE_DIR=${IMPORT_STATE_DIR:-$IMPORT_DIR/.import-state}
CLASSPATH="/app/app.jar:/app/libs/*"

mkdir -p "$IMPORT_STATE_DIR"

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

chunk_progress_complete() {
  progress_file="$1"
  [ -f "$progress_file" ] && grep -q '^stage=done$' "$progress_file"
}

run_chunked_json_until_complete() {
  source_json="$1"
  progress_file="$IMPORT_STATE_DIR/$(basename "$source_json").progress.properties"
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

run_import() {
  source_file="$1"
  progress_file=""

  case "$source_file" in
    *.json)
      if [ -n "${GAFFER_CONVERT_LIMIT:-}" ] && [ -z "${GAFFER_CONVERT_OFFSET:-}" ]; then
        progress_file="$IMPORT_STATE_DIR/$(basename "$source_file").progress.properties"
      fi
      ;;
  esac

  if [ -n "$progress_file" ]; then
    run_chunked_json_until_complete "$source_file"
    return
  fi

  java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferNeo4jImporter "$source_file"
}

if [ -d "$IMPORT_DIR" ]; then
  found=0
  for source in "$IMPORT_DIR"/*.csv "$IMPORT_DIR"/*.json; do
    [ -e "$source" ] || continue
    found=1
    echo "Starting streaming Gaffer import from $source"
    run_import "$source"
  done

  if [ "$found" -eq 0 ]; then
    echo "No .csv or .json files found in $IMPORT_DIR"
  else
    echo "Gaffer pipeline complete."
  fi
  exit 0
fi

CSV_PATH=${CSV_PATH:-/app/matrix_characters.csv}
IMPORT_SOURCE=${IMPORT_SOURCE:-$CSV_PATH}

echo "Starting streaming Gaffer import from $IMPORT_SOURCE"
run_import "$IMPORT_SOURCE"

echo "Gaffer pipeline complete."
