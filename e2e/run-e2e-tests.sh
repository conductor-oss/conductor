#!/bin/bash

# E2E Tests Runner Script for Conductor
# This script builds the project, creates the docker image, and runs the e2e tests.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR/.."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}======================================${NC}"
echo -e "${GREEN}       Conductor E2E Tests           ${NC}"
echo -e "${GREEN}======================================${NC}"

# Function to cleanup on exit
cleanup() {
    if [ "$1" == "stop" ]; then
        echo -e "\n${YELLOW}Stopping Docker services...${NC}"
        cd "$SCRIPT_DIR"
        docker compose down -v
    fi
}

# Parse arguments
SKIP_BUILD=false
SKIP_DOCKER=false
STOP_AFTER=false
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true ;;
        --skip-docker) SKIP_DOCKER=true ;;
        --stop-after) STOP_AFTER=true ;;
        -h|--help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --skip-build     Skip building the project and Docker image"
            echo "  --skip-docker    Skip starting Docker services (assumes they're already running)"
            echo "  --stop-after     Stop Docker services after tests complete"
            echo "  -h, --help       Show this help message"
            exit 0
            ;;
        *) echo "Unknown parameter: $1"; exit 1 ;;
    esac
    shift
done

# Build the project and Docker image if not skipped
if [ "$SKIP_BUILD" = false ]; then
    echo -e "\n${YELLOW}Building Conductor server...${NC}"
    cd "$PROJECT_ROOT"
    ./gradlew :conductor-server:build -x test -x spotlessCheck --no-daemon

    echo -e "\n${YELLOW}Preparing Docker build context...${NC}"
    cp "$PROJECT_ROOT/server/build/libs/"*boot*.jar "$SCRIPT_DIR/docker/conductor-server.jar"
    cp -r "$SCRIPT_DIR/config" "$SCRIPT_DIR/docker/"

    echo -e "\n${YELLOW}Building Docker image...${NC}"
    cd "$SCRIPT_DIR/docker"
    docker build -t conductor:server-e2e .

    # Cleanup
    rm -f "$SCRIPT_DIR/docker/conductor-server.jar"
    rm -rf "$SCRIPT_DIR/docker/config"
fi

# Start Docker services if not skipped
if [ "$SKIP_DOCKER" = false ]; then
    echo -e "\n${YELLOW}Starting Docker services...${NC}"
    cd "$SCRIPT_DIR"
    docker compose up -d

    echo -e "\n${YELLOW}Waiting for Conductor proxy to be ready...${NC}"
    max_attempts=60
    attempt=1
    while [ $attempt -le $max_attempts ]; do
        proxy_ready=$(curl -s http://localhost:8080/health > /dev/null 2>&1 && echo "yes" || echo "no")

        if [ "$proxy_ready" = "yes" ]; then
            echo -e "${GREEN}Conductor proxy is ready!${NC}"
            break
        fi
        echo "  Attempt $attempt/$max_attempts: Proxy=$proxy_ready"
        sleep 5
        attempt=$((attempt + 1))
    done

    if [ $attempt -gt $max_attempts ]; then
        echo -e "${RED}Conductor failed to start within the expected time.${NC}"
        docker compose logs conductor-server-1 conductor-server-2 proxy
        exit 1
    fi
fi

# Set environment variable for Conductor URL (via HAProxy)
export CONDUCTOR_SERVER_URL="http://localhost:8080/api"

echo -e "\n${YELLOW}Running E2E tests...${NC}"
echo "Conductor URL: $CONDUCTOR_SERVER_URL"
echo ""

# Run the tests
cd "$PROJECT_ROOT"
./gradlew :conductor-e2e:test --info

TEST_RESULT=$?

# Stop services if requested
if [ "$STOP_AFTER" = true ]; then
    cleanup "stop"
fi

if [ $TEST_RESULT -eq 0 ]; then
    echo -e "\n${GREEN}======================================${NC}"
    echo -e "${GREEN}  All E2E tests passed!              ${NC}"
    echo -e "${GREEN}======================================${NC}"
else
    echo -e "\n${RED}======================================${NC}"
    echo -e "${RED}  Some E2E tests failed!             ${NC}"
    echo -e "${RED}======================================${NC}"
fi

exit $TEST_RESULT
