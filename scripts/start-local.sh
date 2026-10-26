#!/usr/bin/env bash
# Starts a local cluster of 3 storage nodes. The first one is the coordinator.
# Logs go to data/logs/; Ctrl+C stops all nodes.
set -euo pipefail

cd "$(dirname "$0")/.."

HOST="localhost"
TCP_PORTS=(7001 7002 7003)
# All nodes share the multicast port, they join the same group.
UDP_PORT=8000
JAR="target/distfs.jar"
LOG_DIR="data/logs"

if [[ ! -f "$JAR" ]]; then
    echo "Building $JAR..."
    ./mvnw -q package -DskipTests
fi

mkdir -p "$LOG_DIR"

pids=()
stop_nodes() {
    trap - INT TERM EXIT
    echo "Stopping nodes..."
    kill "${pids[@]}" 2>/dev/null || true
    wait 2>/dev/null || true
}
trap stop_nodes INT TERM EXIT

for i in "${!TCP_PORTS[@]}"; do
    port="${TCP_PORTS[$i]}"
    args=(node --host "$HOST" --tcp-port "$port" --udp-port "$UDP_PORT")
    if [[ "$i" -eq 0 ]]; then
        args+=(--coordinator)
    fi

    java -jar "$JAR" "${args[@]}" > "$LOG_DIR/node-$port.log" 2>&1 &
    pids+=($!)
    echo "Started node on TCP $port (log: $LOG_DIR/node-$port.log)"
done

echo "Press Ctrl+C to stop all nodes."
tail -n +1 -f "$LOG_DIR"/node-*.log
