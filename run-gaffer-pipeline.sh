#!/bin/sh

set -eu

CSV_PATH=${CSV_PATH:-/app/matrix_characters.csv}
JSON_PATH=${JSON_PATH:-/app/matrix_characters.json}
CLASSPATH="/app/app.jar:/app/libs/*"

echo "Starting Gaffer conversion..."
java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferJsonConverter "$CSV_PATH" "$JSON_PATH"

echo "Starting Gaffer batch import..."
java -cp "$CLASSPATH" com.thomasandersfourjava.gaffer.GafferNeo4jImporter "$JSON_PATH"

echo "Gaffer pipeline complete."
