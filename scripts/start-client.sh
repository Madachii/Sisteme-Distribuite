#!/usr/bin/env bash
# Starts the interactive client (REPL).
set -euo pipefail

cd "$(dirname "$0")/.."

JAR="target/distfs.jar"

if [[ ! -f "$JAR" ]]; then
    echo "Building $JAR..."
    mvn -q package -DskipTests
fi

exec java -jar "$JAR" client "$@"
