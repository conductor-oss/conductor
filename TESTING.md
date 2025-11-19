# Testing Guide for PR #660

This document describes the testing setup for the DO_WHILE iteration cleanup and Timeline UI fixes.

## What's Ready for Testing

### 1. Demo Workflows ✅
Located in `test-workflows/`:
- **`do-while-cleanup-demo.json`** - Demonstrates `keepLastN` parameter with 10 iterations
- **`do-while-timeline-ui-demo.json`** - Tests Timeline UI with SWITCH and FORK_JOIN_DYNAMIC

### 2. Automated Test Script ✅
- **`test-workflow.sh`** - Builds Docker, starts Conductor, runs workflows, verifies results

### 3. Comprehensive Testing Documentation ✅
- **`test-workflows/README.md`** - Detailed manual and automated testing instructions

### 4. Frontend Component Tests ✅
- **`ui/src/pages/execution/Timeline.test.cy.js`** - 8 Cypress tests
- **`ui/cypress/fixtures/doWhile/doWhileForkJoinDynamic.json`** - Test fixture

### 5. Backend Tests ✅
- **9 unit tests** in `DoWhileTest.java`
- **3 integration tests** in `DoWhileIntegrationTest.java`

## Quick Start (When Docker Environment is Available)

```bash
# Run full automated test
./test-workflow.sh
```

This will:
1. Build Conductor with your PR code
2. Start services (Conductor + Redis + Elasticsearch)
3. Register workflows
4. Execute test workflow with 10 iterations (keepLastN=3)
5. Verify cleanup is working
6. Display results

## Manual Testing Alternative

If Docker build issues occur, you can test on an existing Conductor instance:

```bash
# Register workflows
curl -X PUT http://localhost:8080/api/metadata/workflow \
  -H "Content-Type: application/json" \
  -d @test-workflows/do-while-cleanup-demo.json

# Run workflow
curl -X POST http://localhost:8080/api/workflow/do_while_cleanup_demo \
  -H "Content-Type: application/json" \
  -d '{"max_iterations": 10}'

# View in UI
open http://localhost:8080
```

## Expected Test Results

### Backend Cleanup Test
When running `do-while-cleanup-demo` with `keepLastN: 3`:
- ✅ Workflow completes 10 iterations
- ✅ Only last 3 iterations (8, 9, 10) appear in output
- ✅ Database tasks for iterations 1-7 are removed
- ✅ Task count stays low despite many iterations

### Timeline UI Test
When viewing `do-while-timeline-ui-demo` in UI:
- ✅ Timeline tab loads without going blank
- ✅ Both DO_WHILE loops render correctly
- ✅ SWITCH defaultCase tasks are visible
- ✅ FORK_JOIN_DYNAMIC tasks are visible in nested groups

## Verification Commands

```bash
# Check workflow status
curl http://localhost:8080/api/workflow/{workflowId}

# Get detailed task list
curl http://localhost:8080/api/workflow/{workflowId}?includeTasks=true

# Count tasks (should be ≤ 8 for cleanup demo)
curl http://localhost:8080/api/workflow/{workflowId}?includeTasks=true | \
  grep -o '"taskType"' | wc -l
```

## Test Files Included in PR

```
conductor/
├── test-workflows/
│   ├── README.md                          # Testing documentation
│   ├── do-while-cleanup-demo.json         # Cleanup feature demo
│   └── do-while-timeline-ui-demo.json     # Timeline UI fix demo
├── test-workflow.sh                        # Automated test script
├── ui/
│   ├── src/pages/execution/
│   │   └── Timeline.test.cy.js            # Component tests (8 tests)
│   └── cypress/fixtures/doWhile/
│       └── doWhileForkJoinDynamic.json    # Test fixture
└── core/src/test/java/
    └── com/netflix/conductor/core/execution/tasks/
        ├── DoWhileTest.java                # Unit tests (6 tests)
        └── DoWhileIntegrationTest.java     # Integration tests (3 tests)
```

## Test Coverage Summary

**Total: 17 automated tests**

| Type | Count | File |
|------|-------|------|
| Backend Unit Tests | 6 | DoWhileTest.java |
| Backend Integration Tests | 3 | DoWhileIntegrationTest.java |
| Frontend Component Tests | 8 | Timeline.test.cy.js |

## Known Issues

### Docker Build Failure
If you encounter `rpc error: code = Unavailable`, this is a Docker daemon issue:
- **Solution 1**: Restart Docker daemon
- **Solution 2**: Increase Docker resources (memory/CPU)
- **Solution 3**: Test on pre-existing Conductor instance

## Next Steps for Reviewers

1. **Run automated tests**: Execute `./test-workflow.sh`
2. **Manual UI testing**: View Timeline with DO_WHILE workflows
3. **Review test code**: Check test coverage and assertions
4. **Verify backward compatibility**: Test existing workflows still work

## Related Documentation

- **PR #660**: https://github.com/conductor-oss/conductor/pull/660
- **Issue #618**: DO_WHILE iteration cleanup epic
- **Issue #534**: Timeline UI blank bug
- **PR #537**: WorkflowDAG.js fix (merged)
