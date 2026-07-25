#!/bin/bash
# =============================================
# MIDOMAX HELPDESK - Linux Startup Script
# =============================================
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$APP_DIR/helpdesk.jar"
LOG="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"

echo "=== Starting Midomax Helpdesk ==="

if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Already running with PID $PID"
        exit 1
    fi
fi

nohup java -jar "$JAR" \
    --spring.config.additional-location="$APP_DIR/application.properties" \
    >> "$LOG" 2>&1 &

echo $! > "$PID_FILE"
echo "Started with PID $(cat "$PID_FILE")"
echo "Logs: $LOG"