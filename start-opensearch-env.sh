#!/bin/bash
set -e

echo "=== OpenSearch Testing Environment Setup ==="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to check if container is healthy
wait_for_container() {
    local container_name=$1
    local port=$2
    local max_attempts=30
    local attempt=1

    echo -n "Waiting for $container_name to be ready..."
    while [ $attempt -le $max_attempts ]; do
        if curl -s "http://localhost:$port/_cluster/health" > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done
    echo -e " ${RED}✗ (timeout)${NC}"
    return 1
}

wait_for_redis() {
    local max_attempts=15
    local attempt=1

    echo -n "Waiting for Redis to be ready..."
    while [ $attempt -le $max_attempts ]; do
        if docker exec docker-conductor-redis-1 redis-cli ping > /dev/null 2>&1; then
            echo -e " ${GREEN}✓${NC}"
            return 0
        fi
        echo -n "."
        sleep 1
        attempt=$((attempt + 1))
    done
    echo -e " ${RED}✗ (timeout)${NC}"
    return 1
}

# Start Redis
echo -e "${YELLOW}Starting Redis...${NC}"
if docker ps -a --format '{{.Names}}' | grep -q "^docker-conductor-redis-1$"; then
    docker start docker-conductor-redis-1
    wait_for_redis
else
    echo "Redis container not found, creating new one..."
    docker run -d \
      --name docker-conductor-redis-1 \
      -p 6379:6379 \
      redis:6.2.3-alpine
    wait_for_redis
fi

# Start OpenSearch 2
echo -e "${YELLOW}Starting OpenSearch 2.x...${NC}"
if docker ps -a --format '{{.Names}}' | grep -q "^opensearch-2$"; then
    docker start opensearch-2
    wait_for_container "opensearch-2" 9201
else
    echo "OpenSearch 2 container not found, creating new one..."
    docker run -d \
      --name opensearch-2 \
      -p 9201:9200 \
      -p 9601:9600 \
      -e "discovery.type=single-node" \
      -e "plugins.security.disabled=true" \
      -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=TestPass123!" \
      opensearchproject/opensearch:2.18.0
    wait_for_container "opensearch-2" 9201
fi

# Start OpenSearch 3
echo -e "${YELLOW}Starting OpenSearch 3.x...${NC}"
if docker ps -a --format '{{.Names}}' | grep -q "^opensearch-3$"; then
    echo "Found existing OpenSearch 3 container, restarting..."
    docker start opensearch-3
    wait_for_container "opensearch-3" 9202
else
    echo "Creating new OpenSearch 3 container..."
    docker run -d \
      --name opensearch-3 \
      -p 9202:9200 \
      -p 9602:9600 \
      -e "discovery.type=single-node" \
      -e "plugins.security.disabled=true" \
      -e "OPENSEARCH_INITIAL_ADMIN_PASSWORD=TestPass123!" \
      opensearchproject/opensearch:3.0.0
    wait_for_container "opensearch-3" 9202
fi

echo ""
echo -e "${GREEN}=== All services ready! ===${NC}"
echo ""
echo "Services running:"
echo "  • Redis:         localhost:6379"
echo "  • OpenSearch 2:  http://localhost:9201"
echo "  • OpenSearch 3:  http://localhost:9202"
echo ""
echo "Test connectivity:"
echo "  curl http://localhost:9201"
echo "  curl http://localhost:9202"
echo ""
echo "Stop all:"
echo "  docker stop docker-conductor-redis-1 opensearch-2 opensearch-3"
echo ""
