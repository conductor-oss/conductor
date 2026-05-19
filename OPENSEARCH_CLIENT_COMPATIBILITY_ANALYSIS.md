# OpenSearch Client Compatibility Analysis

## Question: Can opensearch-java 2.x and 3.x coexist on the classpath?

**TL;DR: NO - They will have classpath conflicts and require shading (like ES7 does).**

## Evidence

### Package Namespace Analysis

Based on research and Maven Central inspection:

**opensearch-java 2.x uses:**
```
org.opensearch.client.opensearch.*
org.opensearch.client.transport.*
org.opensearch.client.*
```

**opensearch-java 3.x uses:**
```
org.opensearch.client.opensearch.*  (SAME)
org.opensearch.client.transport.*  (SAME)
org.opensearch.client.*            (SAME)
```

**Conclusion:** Both versions use the **SAME package namespace**, which means they **WILL conflict** on the classpath.

### Existing Conductor Evidence

**Current state:**
- `os-persistence` already uses shadow plugin for shading
- `es7-persistence` uses shadow plugin for shading
- `es6-persistence` does NOT use shadow plugin

This suggests:
- ES6 was first, no conflicts with anything else
- ES7 added later, needed shading to avoid ES6 conflicts
- OpenSearch needed shading from the start (conflicts with both ES versions)

### OpenSearch Client Compatibility Matrix

Source: [opensearch-java COMPATIBILITY.md](https://github.com/opensearch-project/opensearch-java/blob/main/COMPATIBILITY.md)

| Client Version | OpenSearch Server Versions | JDK Support |
|----------------|----------------------------|-------------|
| 1.0.0 | 1.x | JDK 8 |
| 2.x.0 | 1.x-2.x | JDK 8, 11, 17, 21 |
| 3.x.0 | 1.x-3.x | JDK 8, 11, 17, 21 |

**Key Finding:** Client 3.x is backward compatible with OpenSearch 1.x-3.x servers, but the **client library itself** uses the same package names across versions.

### Breaking Changes in opensearch-java 3.0

Source: [opensearch-java releases](https://github.com/opensearch-project/opensearch-java/releases)

Major breaking changes in 3.0.0:
- Migration to Apache HttpClient / Core 5.x (from 4.x)
- Changed `SearchAfter` type from `String` to `FieldValue`
- Changed `DanglingIndex.creationDateMillis` from `String` to `long`
- Changed various statistics fields from `Number` to `int`/`Integer`
- Unified `tasks.Info` and `tasks.State` into `tasks.TaskInfo`

**BUT**: No package namespace changes. Still uses `org.opensearch.client.*`

## Solution: Use Shadow Plugin (Like ES7)

### Current os-persistence Module Pattern

```gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

shadowJar {
    configurations = [project.configurations.shadow]
    archiveClassifier = null

    mergeServiceFiles {
        include 'META-INF/services/*'
        include 'META-INF/maven/*'
    }
}
```

### Recommended Approach for os-persistence-v2 and os-persistence-v3

Both modules will need shadow plugin with package relocation:

**os-persistence-v2/build.gradle:**
```gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

dependencies {
    implementation 'org.opensearch.client:opensearch-java:2.18.0'
    implementation 'org.opensearch.client:opensearch-rest-client:2.18.0'
}

shadowJar {
    configurations = [project.configurations.shadow]
    archiveClassifier = null

    // Relocate opensearch-java 2.x to avoid conflicts with 3.x
    relocate 'org.opensearch.client', 'com.netflix.conductor.os2.shaded.opensearch.client'

    mergeServiceFiles {
        include 'META-INF/services/*'
        include 'META-INF/maven/*'
    }
}
```

**os-persistence-v3/build.gradle:**
```gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

dependencies {
    implementation 'org.opensearch.client:opensearch-java:3.3.2'
    implementation 'org.opensearch.client:opensearch-rest-client:3.0.0'
}

shadowJar {
    configurations = [project.configurations.shadow]
    archiveClassifier = null

    // Relocate opensearch-java 3.x to avoid conflicts with 2.x
    relocate 'org.opensearch.client', 'com.netflix.conductor.os3.shaded.opensearch.client'

    mergeServiceFiles {
        include 'META-INF/services/*'
        include 'META-INF/maven/*'
    }
}
```

## Alternative: Don't Include Both Modules Simultaneously

**Option:** Make users choose ONE version at build time via gradle flags (like ES6 is currently disabled on macOS).

**Pros:**
- No shading needed
- Simpler build configuration
- Smaller artifacts

**Cons:**
- ❌ Can't support both versions in same server build
- ❌ Users need to rebuild to switch versions
- ❌ Not consistent with ES6/ES7 coexistence pattern
- ❌ Doesn't align with Viren's vision of clean separation

**Recommendation:** Don't use this approach. Shading is better.

## Implementation Plan

### Step 1: Create os-persistence-v2 with Shading

1. Copy `os-persistence/` structure
2. Rename package: `com.netflix.conductor.os` → `com.netflix.conductor.os2`
3. Add shadow plugin with relocation to `com.netflix.conductor.os2.shaded.opensearch.client`
4. Update `OpenSearchConditions`:
   ```java
   @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch2")
   ```
5. Keep opensearch-java 2.18.0 dependencies
6. Test in isolation

### Step 2: Create os-persistence-v3 with Shading

1. Copy `os-persistence-v2/` structure
2. Rename package: `com.netflix.conductor.os2` → `com.netflix.conductor.os3`
3. Add shadow plugin with relocation to `com.netflix.conductor.os3.shaded.opensearch.client`
4. Update `OpenSearchConditions`:
   ```java
   @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch3")
   ```
5. Upgrade to opensearch-java 3.3.2 dependencies
6. Handle breaking changes:
   - Update SearchAfter usage
   - Update DanglingIndex field types
   - Update statistics field types
   - Update tasks classes
7. Test in isolation

### Step 3: Update Server Module

```gradle
// server/build.gradle
dependencies {
    runtimeOnly project(':conductor-os-persistence-v2')
    runtimeOnly project(':conductor-os-persistence-v3')
}
```

Both modules can coexist because:
- ✅ Each uses different relocated package namespace via shading
- ✅ Spring's `@ConditionalOnProperty` ensures only one activates
- ✅ Proven pattern (ES6 + ES7 already work this way)

### Step 4: Test Classpath Coexistence

```bash
# Start with OS 2.x config
conductor.indexing.type=opensearch2
→ Only os-persistence-v2 activates
→ Uses shaded org.opensearch.client from v2

# Start with OS 3.x config
conductor.indexing.type=opensearch3
→ Only os-persistence-v3 activates
→ Uses shaded org.opensearch.client from v3
```

No conflicts because each module's OpenSearch client is in a different package namespace after shading.

## Comparison with Elasticsearch Modules

### ES6 (No Shading)
```gradle
// es6-persistence/build.gradle
// No shadow plugin - was first module, no conflicts
dependencies {
    implementation "org.elasticsearch.client:transport:${revElasticSearch6}"
    implementation "org.elasticsearch.client:elasticsearch-rest-client:${revElasticSearch6}"
}
```

### ES7 (With Shading)
```gradle
// es7-persistence/build.gradle
plugins {
    id 'com.github.johnrengelman.shadow' version '7.0.0'
}

dependencies {
    implementation "org.elasticsearch.client:elasticsearch-rest-client:${revElasticSearch7}"
}

shadowJar {
    // Shades to avoid conflicts with ES6
}
```

### OpenSearch V2 & V3 (Both Need Shading)
Since we're creating both modules simultaneously, **BOTH need shading** to avoid conflicts with:
- Each other
- Potentially with ES6/ES7 if used together

## Configuration After Implementation

### User Configuration (Clean!)

```properties
# OpenSearch 2.x
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor

# OR OpenSearch 3.x
conductor.indexing.type=opensearch3
conductor.opensearch.url=http://localhost:9200
conductor.opensearch.indexPrefix=conductor
```

Users don't see or care about shading - it's an internal implementation detail.

## Risks and Mitigations

### Risk 1: Shading Increases JAR Size
**Mitigation:** Acceptable - ES7 already does this, proven pattern

### Risk 2: Debugging Shaded Code is Harder
**Mitigation:**
- Use clear relocation package names
- Document internal structure
- Keep shading configuration simple

### Risk 3: Service Files Conflicts
**Mitigation:** Already handled by `mergeServiceFiles` in shadowJar config

### Risk 4: Breaking Changes in OS 3.x Client
**Mitigation:**
- Thoroughly test migration
- Document breaking changes in PR
- Provide clear migration guide

## Timeline Impact

Adding shading doesn't significantly increase timeline:

| Task | Without Shading | With Shading | Delta |
|------|----------------|--------------|-------|
| Create v2 module | 4 hours | 5 hours | +1 hour |
| Create v3 module | 4 hours | 5 hours | +1 hour |
| Test classpath | 2 hours | 3 hours | +1 hour |
| **Total** | **10 hours** | **13 hours** | **+3 hours** |

The extra 3 hours is worth it for clean module coexistence.

## Recommendation

**✅ Use shading for both os-persistence-v2 and os-persistence-v3**

**Reasons:**
1. Both opensearch-java 2.x and 3.x use same package namespace → Will conflict
2. ES7 already uses shading → Proven pattern in Conductor
3. Allows both modules in server simultaneously → Better UX
4. Minimal timeline impact (+3 hours)
5. Aligns with Viren's vision of clean separation

**Next Steps:**
1. ✅ Confirm shading approach with team
2. Create os-persistence-v2 with shadow plugin
3. Create os-persistence-v3 with shadow plugin
4. Test both modules in server together
5. Verify conditional activation works correctly

## Sources

- [opensearch-java COMPATIBILITY.md](https://github.com/opensearch-project/opensearch-java/blob/main/COMPATIBILITY.md)
- [opensearch-java releases](https://github.com/opensearch-project/opensearch-java/releases)
- [OpenSearch 3.0 breaking changes](https://opensearch.org/blog/opensearch-3-0-what-to-expect/)
- [opensearch-java CHANGELOG](https://github.com/opensearch-project/opensearch-java/blob/main/CHANGELOG.md)
