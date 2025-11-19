# DO_WHILE Test Workflows

This directory contains test workflows for validating the DO_WHILE iteration cleanup feature and Timeline UI fixes from PR #660.

## Workflows

### 1. `do-while-cleanup-demo.json`
**Purpose:** Demonstrates the `keepLastN` parameter for iteration cleanup.

**Features:**
- Runs 10 iterations of a DO_WHILE loop
- Each iteration generates and processes 100 random data points
- `keepLastN: 3` ensures only the last 3 iterations are kept in database
- Prevents database bloat and memory exhaustion

**What to verify:**
- ✅ Workflow completes successfully with 10 iterations
- ✅ Only last 3 iterations appear in task output
- ✅ Old iteration tasks are removed from the database

### 2. `do-while-timeline-ui-demo.json`
**Purpose:** Validates Timeline UI rendering fix for issue #534.

**Features:**
- First DO_WHILE loop contains a SWITCH task with defaultCase
- Second DO_WHILE loop contains FORK_JOIN_DYNAMIC tasks
- Tests both scenarios that previously caused Timeline UI to go blank

**What to verify:**
- ✅ Timeline UI renders correctly (doesn't go blank)
- ✅ Both DO_WHILE loops execute successfully
- ✅ Workflow diagram displays all tasks correctly

## Quick Start

### Automated Testing (Recommended)

Run the complete test suite with one command:

```bash
./test-workflow.sh
```

This script will:
1. Build Docker images from your current branch
2. Start Conductor with Redis + Elasticsearch
3. Register the cleanup demo workflow
4. Execute the workflow with 10 iterations (keepLastN=3)
5. Verify that cleanup is working
6. Display results and UI links

### Manual Testing

#### Step 1: Start Conductor

```bash
cd docker
docker compose -f docker-compose.yaml build
docker compose -f docker-compose.yaml up -d
```

Wait for Conductor to be ready (check health):
```bash
curl http://localhost:8080/health
```

#### Step 2: Register Workflows

```bash
# Register cleanup demo
curl -X PUT http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d @test-workflows/do-while-cleanup-demo.json

# Register Timeline UI demo
curl -X PUT http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d @test-workflows/do-while-timeline-ui-demo.json
```

#### Step 3: Execute Workflows

**Cleanup demo:**
```bash
curl -X POST http://localhost:8080/api/workflow/do_while_cleanup_demo \
  -H "Content-Type: application/json" \
  -d '{"max_iterations": 10}'
```

**Timeline UI demo:**
```bash
curl -X POST http://localhost:8080/api/workflow/do_while_timeline_ui_demo \
  -H "Content-Type: application/json" \
  -d '{}'
```

#### Step 4: View in UI

Open your browser to:
- **Conductor UI:** http://localhost:8080
- **Workflow Executions:** http://localhost:8080/execution

## Verification Steps

### For Iteration Cleanup (`do-while-cleanup-demo`)

1. **Check workflow completed:**
   ```bash
   curl http://localhost:8080/api/workflow/{workflowId}
   ```
   - Verify `"status": "COMPLETED"`
   - Check `"iteration": 10` in DO_WHILE task output

2. **Verify only 3 iterations retained:**
   ```bash
   curl http://localhost:8080/api/workflow/{workflowId}?includeTasks=true
   ```
   - Count tasks with `__8`, `__9`, `__10` suffixes (should exist)
   - Confirm tasks with `__1`, `__2`, `__7` suffixes don't exist (removed)

3. **Check UI:**
   - Navigate to execution in Timeline view
   - Verify only recent iterations are visible
   - No database bloat warnings

### For Timeline UI Fix (`do-while-timeline-ui-demo`)

1. **Check Timeline doesn't crash:**
   - Open workflow execution in UI
   - Click "Timeline" tab
   - **Before fix:** Timeline would go blank ❌
   - **After fix:** Timeline renders correctly ✅

2. **Verify task rendering:**
   - All tasks in both DO_WHILE loops should be visible
   - SWITCH defaultCase tasks should render
   - FORK_JOIN_DYNAMIC tasks should render in nested groups

3. **Check workflow diagram:**
   - Navigate to "Workflow" tab
   - Verify DAG displays correctly
   - All iterations and branches should be visible

## Expected Results

### Cleanup Demo Output
```json
{
  "do_while_loop_ref": {
    "iteration": 10,
    "8": { "generate_data_ref": {...}, "process_data_ref": {...} },
    "9": { "generate_data_ref": {...}, "process_data_ref": {...} },
    "10": { "generate_data_ref": {...}, "process_data_ref": {...} }
  },
  "summary_ref": {
    "total_iterations": 10,
    "message": "Workflow completed. Only last 3 iterations retained in output."
  }
}
```

### Timeline UI Demo Output
- Both DO_WHILE loops complete successfully
- Timeline UI renders without errors
- All task states are visible in Timeline

## Cleanup

Stop and remove containers:
```bash
cd docker
docker compose -f docker-compose.yaml down
```

Remove volumes (full cleanup):
```bash
docker compose -f docker-compose.yaml down -v
```

## Troubleshooting

### Conductor won't start
```bash
# Check logs
docker compose -f docker/docker-compose.yaml logs conductor-server

# Ensure ports are available
lsof -i :8080
lsof -i :9201
lsof -i :7379
```

### Workflow registration fails
- Verify Conductor is healthy: `curl http://localhost:8080/health`
- Check workflow JSON syntax: `cat test-workflows/do-while-cleanup-demo.json | python3 -m json.tool`

### Timeline UI still blank
- Ensure you're testing the built image from this branch
- Clear browser cache and reload
- Check browser console for JavaScript errors

## Related Issues & PRs

- **Issue #618:** DO_WHILE iteration cleanup epic
- **Issue #534:** Timeline UI goes blank with DO_WHILE + SWITCH/FORK_JOIN_DYNAMIC
- **PR #660:** This implementation
- **PR #537:** WorkflowDAG.js fix (merged)
