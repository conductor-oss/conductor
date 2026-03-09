# OpenSearch v3 Migration Implementation Plan

## Goal
Migrate os-persistence-v3 from OpenSearch High-Level REST Client to opensearch-java 3.x client.

## Current Status
- ✅ Module structure created
- ✅ Dependencies configured
- ✅ Config classes updated (OpenSearchProperties, OpenSearchConditions)
- ❌ DAO layer still uses old API (77+ compilation errors)
- ❌ Query builders not migrated

## Migration Approach

**Strategy:** Incremental migration in small, testable commits.

Each commit should:
1. Compile successfully
2. Pass existing tests
3. Be reviewable independently

## Phase 1: Foundation (Days 1-2)

### Commit 1: Client Infrastructure
**File:** `OpenSearchRestDAO.java` (constructor + client init)

**Tasks:**
- [ ] Add new `OpenSearchClient` field
- [ ] Keep old `RestHighLevelClient` temporarily (dual-client mode)
- [ ] Add client initialization in constructor
- [ ] Add Jackson JSON mapper setup
- [ ] Add client close() method

**Test:** Verify server starts without errors

### Commit 2: Query Builder Abstraction
**New file:** `QueryHelper.java`

**Tasks:**
- [ ] Create helper class for query building
- [ ] Implement `buildBoolQuery(String structured, String freeText)` → returns `Query`
- [ ] Implement `buildMatchQuery(String field, String value)` → returns `Query`
- [ ] Implement `buildRangeQuery(String field, Object from, Object to)` → returns `Query`
- [ ] Add unit tests for query building

**Test:** Unit tests pass

## Phase 2: Search Operations (Days 3-4)

### Commit 3: Core Search Method
**File:** `OpenSearchRestDAO.java` (new method)

**Tasks:**
- [ ] Create NEW method: `searchObjectsV3(...)` using new client
- [ ] Implement query building with lambda builders
- [ ] Implement sorting with new API
- [ ] Implement pagination
- [ ] Map results to `SearchResult<T>`

**Test:** Add integration test comparing v2 vs v3 search results

### Commit 4: Migrate Search Methods (One at a Time)
**Files:** `OpenSearchRestDAO.java`

**Order:**
1. [ ] `searchObjectsViaExpression()` - use `searchObjectsV3()`
2. [ ] `searchWorkflowSummary()` - use `searchObjectsV3()`
3. [ ] `searchTaskSummary()` - use `searchObjectsV3()`

**Test:** Integration tests pass for each method

### Commit 5: Count Operation
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Create `countDocuments(String index, Query query)` helper
- [ ] Update all count operations to use new method
- [ ] Fix `CountResponse.count()` vs old `getCount()`

**Test:** Count queries return correct values

## Phase 3: Index Operations (Days 5-6)

### Commit 6: Index/Update Operations
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Create `indexDocumentV3(String index, String id, Object doc)` helper
- [ ] Migrate `indexObject()` to use new method
- [ ] Migrate `updateObject()` to use new method
- [ ] Handle async updates

**Test:** Document indexing works correctly

### Commit 7: Delete Operations
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Create `deleteDocumentV3(String index, String id)` helper
- [ ] Migrate `deleteObject()` to use new method

**Test:** Document deletion works correctly

### Commit 8: Bulk Operations
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Create `BulkHelper.java` for bulk operation building
- [ ] Migrate `bulkIndexObjects()` to use lambda builders
- [ ] Migrate `asyncBulkIndexObjects()` to use lambda builders
- [ ] Update `BulkProcessor` initialization (if needed)

**Test:** Bulk operations work correctly

## Phase 4: Specialized Operations (Day 7)

### Commit 9: Task Logs
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Migrate `addTaskExecutionLogs()` to new API
- [ ] Migrate `getTaskExecutionLogs()` to new API

**Test:** Task logs index and retrieve correctly

### Commit 10: Event Messages
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Migrate `addMessage()` to new API
- [ ] Migrate `getMessages()` to new API

**Test:** Event messages work correctly

## Phase 5: Cleanup & Optimization (Day 8)

### Commit 11: Remove Old Client
**File:** `OpenSearchRestDAO.java`

**Tasks:**
- [ ] Remove `RestHighLevelClient` field
- [ ] Remove dual-client code paths
- [ ] Clean up unused imports
- [ ] Remove old API dependencies from build.gradle (if possible)

**Test:** All tests still pass

### Commit 12: Query Parser Migration
**Files:** `os3/dao/query/parser/**/*.java`

**Tasks:**
- [ ] Update `Expression.java` to use `Query` instead of `QueryBuilder`
- [ ] Update `NameValue.java` query building
- [ ] Update `GroupedExpression.java` query building
- [ ] Fix `FilterProvider` interface

**Test:** Query parsing works correctly

### Commit 13: Spotless & Documentation
**Tasks:**
- [ ] Run Spotless formatting
- [ ] Update JavaDocs to reference new API
- [ ] Update README with migration notes
- [ ] Update build.gradle comments

**Test:** Build passes with no warnings

## Phase 6: Testing & Validation (Days 9-10)

### Commit 14: Integration Test Suite
**New file:** `OpenSearchRestDAOV3IntegrationTest.java`

**Tasks:**
- [ ] Test all CRUD operations
- [ ] Test search with complex queries
- [ ] Test bulk operations
- [ ] Test sorting and pagination
- [ ] Test task logs
- [ ] Test event messages
- [ ] Compare results with v2

**Test:** All integration tests pass

### Commit 15: Side-by-Side Comparison
**New file:** `os-persistence-v3/src/test/resources/comparison-tests.json`

**Tasks:**
- [ ] Run test workflows on both v2 and v3
- [ ] Compare indexed documents
- [ ] Compare search results
- [ ] Document any differences

**Test:** No behavioral differences detected

## Detailed Work Breakdown

### Critical Files to Modify

1. **OpenSearchRestDAO.java** (~1343 lines)
   - Core DAO implementation
   - ~25 methods to migrate
   - Estimated: 20 hours

2. **Query Parser Files** (~300 lines total)
   - `Expression.java`
   - `NameValue.java`
   - `GroupedExpression.java`
   - `FilterProvider.java`
   - Estimated: 4 hours

3. **Helper Classes** (new)
   - `QueryHelper.java` (query building)
   - `BulkHelper.java` (bulk operations)
   - Estimated: 4 hours

4. **Test Files** (new)
   - Integration tests
   - Comparison tests
   - Estimated: 8 hours

### Dependencies to Add/Remove

**Keep:**
```gradle
implementation 'org.opensearch.client:opensearch-java:3.0.0'
implementation "org.opensearch.client:opensearch-rest-client:3.0.0"
```

**Remove (after migration):**
```gradle
implementation "org.opensearch.client:opensearch-rest-high-level-client:3.0.0"
```

## Risk Mitigation

### Risk 1: Breaking API Changes
**Mitigation:** Side-by-side testing with v2

### Risk 2: Performance Regression
**Mitigation:** Benchmark tests before/after

### Risk 3: Serialization Issues
**Mitigation:** Test with real workflow data early

### Risk 4: Unknown API Differences
**Mitigation:** Iterative approach, test each commit

## Timeline Estimate

**Optimistic:** 1 week (40 hours)
**Realistic:** 2 weeks (60-80 hours)
**Pessimistic:** 3 weeks (if major blockers found)

**Breakdown:**
- Foundation: 2 days
- Search: 2 days
- CRUD: 2 days
- Specialized: 1 day
- Cleanup: 1 day
- Testing: 2 days
- **Total: 10 working days**

## Success Criteria

- [ ] os-persistence-v3 compiles with 0 errors
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Side-by-side comparison shows identical behavior
- [ ] Performance within 10% of v2
- [ ] No deprecated API usage
- [ ] Code review approved
- [ ] Documentation updated

## Next Steps

1. **Start with Commit 1** (Client Infrastructure)
2. **Create feature branch:** `feature/os-persistence-v3-migration`
3. **Work through commits sequentially**
4. **Test after each commit**
5. **Create PR when Phase 1-3 complete** (minimal viable functionality)
6. **Complete Phase 4-6** based on feedback

## Questions to Resolve

1. Do we need to maintain backward compatibility with v2 config?
2. Should we support rolling upgrades from v2 to v3?
3. What's the deprecation timeline for v2?
4. Do we need separate Docker images for v2 vs v3?

## Resources

- Migration Guide: `os-persistence-v3/MIGRATION_GUIDE.md`
- OpenSearch Java Docs: https://opensearch.org/docs/latest/clients/java/
- Example Code: https://github.com/opensearch-project/opensearch-java/tree/main/samples
