#!/bin/sh
# Conductor Server Startup Script
# Downloads the server JAR if missing and starts it with java
#
# Usage:
#   Interactive:  ./conductor_server.sh
#   With args:    ./conductor_server.sh [PORT] [VERSION]
#                 ./conductor_server.sh 9090 3.22.0
#                 ./conductor_server.sh 9090
#                 ./conductor_server.sh latest (uses default port 8080)
#   One-liner:    curl -sSL https://raw.githubusercontent.com/conductor-oss/conductor/main/conductor_server.sh | sh
#   With args:    curl ... | sh -s -- 9090 3.22.0

# Defaults
SERVER_PORT=8080
CONDUCTOR_VERSION="latest"
REPO_URL="https://conductor-server.s3.us-east-2.amazonaws.com"

# Argument parsing: check if args are port numbers or versions
for arg in "$@"; do
  if echo "$arg" | grep -q "^[0-9][0-9]*$"; then
    SERVER_PORT="$arg"
  else
    CONDUCTOR_VERSION="$arg"
  fi
done

# If running interactively and no args provided, prompt user
if [ $# -eq 0 ] && [ -z "$CONDUCTOR_PORT" ] && [ -t 0 ]; then
  printf "Enter the port for Server [8080]: "
  read input_port </dev/tty
  if [ -n "$input_port" ]; then
    SERVER_PORT="$input_port"
  fi

  printf "Enter the version [latest]: "
  read input_version </dev/tty
  if [ -n "$input_version" ]; then
    CONDUCTOR_VERSION="$input_version"
  fi
elif [ -n "$CONDUCTOR_PORT" ] && [ $# -eq 0 ]; then
    SERVER_PORT="$CONDUCTOR_PORT"
fi

JAR_NAME="conductor-server-${CONDUCTOR_VERSION}.jar"
JAR_URL="${REPO_URL}/${JAR_NAME}"

# Use CONDUCTOR_HOME if set, otherwise use current directory
if [ -z "$CONDUCTOR_HOME" ]; then
  CONDUCTOR_HOME="."
fi

JAR_PATH="$CONDUCTOR_HOME/$JAR_NAME"

# Download JAR if not present
if [ ! -f "$JAR_PATH" ]; then
  echo "Downloading Conductor Server ${CONDUCTOR_VERSION}..."
  mkdir -p "$CONDUCTOR_HOME"
  curl -L -o "$JAR_PATH" "$JAR_URL"
  if [ $? -ne 0 ]; then
    echo "Failed to download Conductor Server JAR from $JAR_URL"
    exit 1
  fi
  echo "Downloaded to $JAR_PATH"
fi

echo "Starting Conductor Server ${CONDUCTOR_VERSION} on port $SERVER_PORT..."
java -jar "$JAR_PATH" --server.port=$SERVER_PORT