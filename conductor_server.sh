#!/bin/sh
# Conductor Server Startup Script
# Downloads the server JAR if missing and starts it with java
#
# Usage:
#   Interactive:  ./conductor_server.sh
#   With port:    ./conductor_server.sh 9090
#   One-liner:    curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
#   With port:    curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh -s -- 9090

JAR_URL="https://oss-releases.s3.us-east-1.amazonaws.com/conductor-server-latest.jar"
JAR_NAME="conductor-server-latest.jar"

# Use CONDUCTOR_HOME if set, otherwise use current directory
if [ -z "$CONDUCTOR_HOME" ]; then
  CONDUCTOR_HOME="."
fi

JAR_PATH="$CONDUCTOR_HOME/$JAR_NAME"

# Download JAR if not present
if [ ! -f "$JAR_PATH" ]; then
  echo "Downloading Conductor Server..."
  mkdir -p "$CONDUCTOR_HOME"
  curl -L -o "$JAR_PATH" "$JAR_URL"
  if [ $? -ne 0 ]; then
    echo "Failed to download Conductor Server JAR"
    exit 1
  fi
  echo "Downloaded to $JAR_PATH"
fi

# Get server port from argument, environment, or prompt
if [ -n "$1" ]; then
  SERVER_PORT="$1"
elif [ -n "$CONDUCTOR_PORT" ]; then
  SERVER_PORT="$CONDUCTOR_PORT"
elif [ -t 0 ]; then
  # Interactive mode - prompt for port
  printf "Enter the port for Server [8080]: "
  read SERVER_PORT </dev/tty
  if [ -z "$SERVER_PORT" ]; then
    SERVER_PORT=8080
  fi
else
  # Non-interactive (piped) - use default
  SERVER_PORT=8080
fi

echo "Starting Conductor Server on port $SERVER_PORT..."
java -jar "$JAR_PATH" --server.port=$SERVER_PORT