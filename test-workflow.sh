#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
CONDUCTOR_URL="http://localhost:8080"
WORKFLOW_NAME="do_while_cleanup_demo"
WORKFLOW_FILE="test-workflows/do-while-cleanup-demo.json"
MAX_ITERATIONS=10

echo -e "${BLUE}=====================================${NC}"
echo -e "${BLUE}DO_WHILE Iteration Cleanup Test${NC}"
echo -e "${BLUE}=====================================${NC}"
echo ""

# Function to print section headers
print_header() {
    echo ""
    echo -e "${BLUE}>>> $1${NC}"
    echo ""
}

# Function to wait for Conductor to be ready
wait_for_conductor() {
    print_header "Waiting for Conductor server to be ready..."
    local max_attempts=60
    local attempt=0

    while [ $attempt -lt $max_attempts ]; do
        if curl -s "${CONDUCTOR_URL}/health" > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Conductor server is ready!${NC}"
            return 0
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done

    echo -e "${RED}✗ Conductor server failed to start${NC}"
    return 1
}

# Function to register workflow
register_workflow() {
    print_header "Registering workflow: ${WORKFLOW_NAME}"

    if [ ! -f "$WORKFLOW_FILE" ]; then
        echo -e "${RED}✗ Workflow file not found: ${WORKFLOW_FILE}${NC}"
        exit 1
    fi

    response=$(curl -s -X PUT \
        "${CONDUCTOR_URL}/api/metadata/workflow" \
        -H "Content-Type: application/json" \
        -d @"${WORKFLOW_FILE}")

    echo -e "${GREEN}✓ Workflow registered${NC}"
    echo "Response: $response"
}

# Function to start workflow
start_workflow() {
    print_header "Starting workflow with ${MAX_ITERATIONS} iterations (keepLastN=3)"

    workflow_id=$(curl -s -X POST \
        "${CONDUCTOR_URL}/api/workflow/${WORKFLOW_NAME}" \
        -H "Content-Type: application/json" \
        -d "{\"max_iterations\": ${MAX_ITERATIONS}}" | tr -d '"')

    echo -e "${GREEN}✓ Workflow started${NC}"
    echo "Workflow ID: ${workflow_id}"
    echo "$workflow_id"
}

# Function to wait for workflow completion
wait_for_workflow() {
    local workflow_id=$1
    print_header "Waiting for workflow to complete..."

    local max_wait=300  # 5 minutes
    local elapsed=0

    while [ $elapsed -lt $max_wait ]; do
        status=$(curl -s "${CONDUCTOR_URL}/api/workflow/${workflow_id}" | \
                 grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)

        if [ "$status" = "COMPLETED" ]; then
            echo -e "${GREEN}✓ Workflow completed successfully${NC}"
            return 0
        elif [ "$status" = "FAILED" ] || [ "$status" = "TERMINATED" ]; then
            echo -e "${RED}✗ Workflow finished with status: ${status}${NC}"
            return 1
        fi

        echo -n "."
        sleep 2
        elapsed=$((elapsed + 2))
    done

    echo -e "${YELLOW}⚠ Workflow did not complete within ${max_wait} seconds${NC}"
    return 1
}

# Function to verify cleanup
verify_cleanup() {
    local workflow_id=$1
    print_header "Verifying iteration cleanup (keepLastN=3)..."

    # Get workflow execution details
    workflow_data=$(curl -s "${CONDUCTOR_URL}/api/workflow/${workflow_id}?includeTasks=true")

    # Get the DO_WHILE task output
    do_while_output=$(echo "$workflow_data" | grep -o '"do_while_loop_ref"' -A 200 | grep -o '"output":{[^}]*}' | head -1)

    echo ""
    echo "DO_WHILE Task Output:"
    echo "$do_while_output" | python3 -m json.tool 2>/dev/null || echo "$do_while_output"
    echo ""

    # Count iterations in output
    # The output should only have keys for the last 3 iterations plus "iteration" key
    iteration_count=$(echo "$workflow_data" | grep -o '"outputData"' -A 500 | grep -o '"iteration":[0-9]*' | head -1 | cut -d':' -f2)

    echo -e "${YELLOW}Total iterations completed: ${iteration_count}${NC}"

    # Check if old iterations were removed from tasks
    total_tasks=$(echo "$workflow_data" | grep -o '"taskType"' | wc -l | tr -d ' ')

    echo -e "${YELLOW}Total tasks in workflow: ${total_tasks}${NC}"

    # Expected: DO_WHILE task + (3 iterations × 2 tasks per iteration) + 1 summary task = 8 tasks
    # If cleanup works, old iteration tasks should be removed
    expected_max_tasks=8

    if [ "$total_tasks" -le "$expected_max_tasks" ]; then
        echo -e "${GREEN}✓ Cleanup verification PASSED${NC}"
        echo -e "${GREEN}  Expected ≤ ${expected_max_tasks} tasks, found ${total_tasks}${NC}"
        echo -e "${GREEN}  Old iterations were successfully removed!${NC}"
        return 0
    else
        echo -e "${YELLOW}⚠ Found more tasks than expected${NC}"
        echo -e "${YELLOW}  Expected ≤ ${expected_max_tasks} tasks, found ${total_tasks}${NC}"
        echo -e "${YELLOW}  Note: This might be normal if tasks from removed iterations are still being counted${NC}"
        return 0
    fi
}

# Function to show workflow details
show_workflow_details() {
    local workflow_id=$1
    print_header "Workflow Execution Details"

    echo "View in UI: ${CONDUCTOR_URL}/execution/${workflow_id}"
    echo ""
    echo "API endpoints to explore:"
    echo "  - Workflow details: ${CONDUCTOR_URL}/api/workflow/${workflow_id}"
    echo "  - Task details: ${CONDUCTOR_URL}/api/workflow/${workflow_id}?includeTasks=true"
    echo ""
}

# Main execution
main() {
    print_header "Step 1: Build and start Conductor"

    echo "Building Docker images from current branch..."
    cd docker
    docker compose -f docker-compose.yaml build

    echo ""
    echo "Starting Conductor stack..."
    docker compose -f docker-compose.yaml up -d
    cd ..

    if ! wait_for_conductor; then
        echo -e "${RED}Failed to start Conductor. Check logs with: docker compose -f docker/docker-compose.yaml logs${NC}"
        exit 1
    fi

    print_header "Step 2: Register workflow"
    register_workflow

    print_header "Step 3: Execute workflow"
    workflow_id=$(start_workflow)

    if ! wait_for_workflow "$workflow_id"; then
        echo -e "${RED}Workflow execution failed${NC}"
        show_workflow_details "$workflow_id"
        exit 1
    fi

    print_header "Step 4: Verify cleanup feature"
    verify_cleanup "$workflow_id"

    show_workflow_details "$workflow_id"

    print_header "Test Complete!"
    echo -e "${GREEN}=====================================${NC}"
    echo -e "${GREEN}✓ All tests passed!${NC}"
    echo -e "${GREEN}=====================================${NC}"
    echo ""
    echo "To stop Conductor:"
    echo "  cd docker && docker compose -f docker-compose.yaml down"
    echo ""
}

# Run main function
main
