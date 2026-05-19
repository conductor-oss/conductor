# Next Steps After PR #675 Merges

## Current Status

✅ **Phase 1 Complete** - PR #675 establishes `conductor.opensearch.*` property namespace
- New properties: `conductor.opensearch.url`, `conductor.opensearch.version`, etc.
- Backward compatibility with legacy `conductor.elasticsearch.*` properties
- Comprehensive test coverage (28 tests added)

## Decision Point: Configuration Approach for Phase 2

There are **two different approaches** for implementing version-specific OpenSearch modules:

### Approach A: Version in Property Namespace (Issue #678's Original Proposal)

**Configuration:**
```properties
# OpenSearch 2.x
conductor.opensearch.v2.enabled=true
conductor.opensearch.v2.url=http://localhost:9200
conductor.opensearch.v2.indexPrefix=conductor

# OpenSearch 3.x
conductor.opensearch.v3.enabled=true
conductor.opensearch.v3.url=http://localhost:9200
conductor.opensearch.v3.indexPrefix=conductor
```

**Module Selection:**
- Conditional on `conductor.opensearch.v2.enabled` or `conductor.opensearch.v3.enabled`
- Separate property namespaces per version

**Pros:**
- Very explicit versioning in property names
- No conflicts between versions in property namespace

**Cons:**
- ❌ Inconsistent with existing backends (postgres, sqlite use `conductor.indexing.type`)
- ❌ Doesn't use `conductor.indexing.type` at all
- ❌ Creates sprawl: `conductor.opensearch.*`, `conductor.opensearch.v2.*`, `conductor.opensearch.v3.*`
- ❌ PR #675's work on `conductor.opensearch.*` namespace becomes less useful
- ❌ Migration path unclear (which namespace do users migrate to?)

---

### Approach B: Unified `conductor.indexing.type` (Recommended)

**Configuration:**
```properties
# OpenSearch 2.x
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor

# OpenSearch 3.x
conductor.indexing.type=opensearch3
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor
```

**Module Selection:**
- Conditional on `conductor.indexing.type=opensearch2|opensearch3`
- **Shared** `conductor.opensearch.*` property namespace (from PR #675)

**Pros:**
- ✅ Consistent with existing pattern (`conductor.indexing.type=postgres|sqlite`)
- ✅ Fully leverages PR #675's `conductor.opensearch.*` namespace
- ✅ Single property (`indexing.type`) drives backend selection
- ✅ Clear migration path from current setup
- ✅ Aligns with Elasticsearch pattern (could eventually use `elasticsearch6|elasticsearch7`)
- ✅ Future-proof: easy to add `opensearch4`, `opensearch5`, etc.

**Cons:**
- Need to deprecate/remove `conductor.opensearch.version` property (since version is now in indexing.type)

---

## Recommendation: Choose Approach B

**Reasons:**

1. **Consistency** - All backends use `conductor.indexing.type` as the primary selection mechanism
2. **Maximizes PR #675 value** - The `conductor.opensearch.*` namespace becomes the shared configuration for all OpenSearch versions
3. **Simpler for users** - One property namespace instead of version-specific namespaces
4. **Proven pattern** - Mirrors how postgres/sqlite/elasticsearch already work

## Implementation Plan for Phase 2 (Using Approach B)

### Step 1: Create Module Structure

```
os-persistence-v2/
├── build.gradle
│   └── opensearch-java:2.x dependencies
└── src/main/java/com/netflix/conductor/os2/
    └── config/
        ├── OpenSearchConditions.java     # Checks: indexing.type=opensearch2
        ├── OpenSearchConfiguration.java
        └── OpenSearchProperties.java     # @ConfigurationProperties("conductor.opensearch")

os-persistence-v3/
├── build.gradle
│   └── opensearch-java:3.x dependencies
└── src/main/java/com/netflix/conductor/os3/
    └── config/
        ├── OpenSearchConditions.java     # Checks: indexing.type=opensearch3
        ├── OpenSearchConfiguration.java
        └── OpenSearchProperties.java     # @ConfigurationProperties("conductor.opensearch")
```

**Key Points:**
- Both modules use **the same** `conductor.opensearch.*` property namespace
- Module selection happens via `conductor.indexing.type` value
- Each module gets its own package (`os2`, `os3`) to avoid conflicts
- Dependency isolation at module level (no shading needed initially)

### Step 2: Implementation Tasks

#### Task 2.1: Create `os-persistence-v2` Module

1. Copy `os-persistence/` as starting point
2. Rename package: `com.netflix.conductor.os` → `com.netflix.conductor.os2`
3. Update `OpenSearchConditions.java`:
   ```java
   @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch2")
   ```
4. Keep opensearch-java 2.x client dependencies
5. Keep `@ConfigurationProperties("conductor.opensearch")` (shared namespace)
6. Add tests
7. Update `settings.gradle` to include module

#### Task 2.2: Create `os-persistence-v3` Module

1. Copy `os-persistence-v2/` as starting point
2. Rename package: `com.netflix.conductor.os2` → `com.netflix.conductor.os3`
3. Update `OpenSearchConditions.java`:
   ```java
   @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch3")
   ```
4. Upgrade to opensearch-java 3.x client dependencies
5. Handle API breaking changes between 2.x and 3.x
6. Keep `@ConfigurationProperties("conductor.opensearch")` (shared namespace)
7. Add tests
8. Update `settings.gradle` to include module

#### Task 2.3: Update Server Module

```gradle
// server/build.gradle
dependencies {
    runtimeOnly project(':conductor-os-persistence-v2')
    runtimeOnly project(':conductor-os-persistence-v3')
}
```

Spring Boot will activate only the module matching `conductor.indexing.type`.

#### Task 2.4: Test Classpath Isolation

Verify both modules can coexist on classpath:
- Start server with `conductor.indexing.type=opensearch2` → only v2 activates
- Start server with `conductor.indexing.type=opensearch3` → only v3 activates
- No classpath conflicts between opensearch-java 2.x and 3.x clients

If conflicts occur, implement shading as fallback.

#### Task 2.5: Update Current `os-persistence` Module

**Option A: Deprecate and Keep Temporarily**
- Add deprecation warning when activated
- Update documentation to use `opensearch2` or `opensearch3`
- Remove in next major version

**Option B: Remove Immediately**
- Force users to migrate to explicit version
- Breaking change (requires major version bump)

**Recommendation:** Option A for smoother migration.

### Step 3: Migration Path for Users

#### Current Configuration (using generic "opensearch")
```properties
conductor.indexing.type=opensearch
conductor.elasticsearch.version=0
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.version=2
```

#### New Configuration (explicit version)
```properties
conductor.indexing.type=opensearch2      # Explicit version
conductor.opensearch.url=http://localhost:9200
# conductor.opensearch.version no longer needed (deprecated)
```

### Step 4: Handle `conductor.opensearch.version` Property

Since PR #675 introduced `conductor.opensearch.version` (values: 1, 2), we need to handle deprecation:

**Short-term (during transition):**
- Keep `conductor.opensearch.version` for backward compatibility
- Log deprecation warning if used
- Document migration to `conductor.indexing.type=opensearch2|opensearch3`

**Long-term (next major version):**
- Remove `conductor.opensearch.version` property
- Remove validation logic
- Users must use `conductor.indexing.type` for version selection

### Step 5: Update Issue #678

Update the issue to reflect Approach B (unified indexing.type) instead of Approach A (version-specific namespaces).

**Changes needed:**
- Update Phase 2 configuration examples
- Change `conductor.opensearch.v2.*` → `conductor.opensearch.*` (shared)
- Change module selection from `.v2.enabled` → `indexing.type=opensearch2`
- Update acceptance criteria

## Comparison: Before and After

### Current State (PR #675 merged)
```properties
conductor.indexing.type=opensearch           # Generic
conductor.opensearch.version=2               # Specifies version
conductor.opensearch.url=http://localhost:9200
```
**Issue:** Still only ONE module for all OpenSearch versions

---

### After Phase 2 (Approach B)
```properties
conductor.indexing.type=opensearch2          # Version-specific module
conductor.opensearch.url=http://localhost:9200
```
**Result:** Separate modules per version, clean selection, shared properties

---

### If We Used Approach A (Issue #678's Original)
```properties
conductor.opensearch.v2.enabled=true         # Version-specific namespace
conductor.opensearch.v2.url=http://localhost:9200
```
**Result:** Separate modules, but inconsistent with other backends

## Open Questions

1. **Should we support OpenSearch 1.x?**
   - Issue #678 says "drop 1.x support"
   - PR #675 added `SUPPORTED_VERSIONS = Set.of(1, 2)`
   - **Recommendation:** Drop 1.x, only create v2 and v3 modules

2. **Should we update Elasticsearch modules to use unified pattern?**
   - Currently: `conductor.indexing.type=elasticsearch` + `conductor.elasticsearch.version=6|7`
   - Could become: `conductor.indexing.type=elasticsearch6|elasticsearch7`
   - **Recommendation:** Yes, but in separate effort (don't block OpenSearch work)

3. **Backward compatibility duration?**
   - How long should `conductor.indexing.type=opensearch` (generic) keep working?
   - **Recommendation:** Keep for 1-2 minor versions with deprecation warnings

4. **Should we fix issue #615 (workflow archival) before or after module split?**
   - Issue #678 Phase 4 suggests after
   - But might be easier to debug with current single module
   - **Recommendation:** Investigate now, fix after module split (may be version-specific)

## Next Immediate Actions

1. **Decide on approach** - Confirm Approach B (unified indexing.type)
2. **Update issue #678** - Revise Phase 2 plan to use Approach B
3. **Create first module** - Implement `os-persistence-v2` following the plan above
4. **Test in isolation** - Verify v2 module works independently
5. **Create second module** - Implement `os-persistence-v3`
6. **Test together** - Verify no classpath conflicts, proper activation
7. **Update documentation** - Migration guide, configuration reference
8. **Deprecate old module** - Add warnings to `os-persistence`

## Timeline Estimate

| Task | Effort | Priority |
|------|--------|----------|
| Create os-persistence-v2 module | 4-6 hours | 🔴 Critical |
| Create os-persistence-v3 module | 4-6 hours | 🔴 Critical |
| Handle 2.x → 3.x API changes | 2-4 hours | 🔴 Critical |
| Test classpath isolation | 2-3 hours | 🔴 Critical |
| Update server dependencies | 1 hour | 🔴 Critical |
| Docker compose verification | 2 hours | 🟡 High |
| Documentation updates | 2-3 hours | 🟡 High |
| Deprecation handling | 1-2 hours | 🟡 High |
| **Total** | **18-27 hours** | |

Split across multiple PRs for easier review.

## Success Metrics

After Phase 2 completion:

- [ ] `conductor.indexing.type=opensearch2` activates v2 module
- [ ] `conductor.indexing.type=opensearch3` activates v3 module
- [ ] Both modules can coexist on classpath without conflicts
- [ ] All tests pass for both modules
- [ ] Docker compose works for both versions
- [ ] Documentation updated with migration guide
- [ ] No breaking changes for users (backward compatibility maintained)
- [ ] Clear deprecation path for old configuration
