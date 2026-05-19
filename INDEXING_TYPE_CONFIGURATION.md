# Unified Indexing Type Configuration

## Overview

This document describes the unified configuration approach for indexing backends in Conductor using `conductor.indexing.type` as the primary driver, as outlined in [Issue #678](https://github.com/conductor-oss/conductor/issues/678).

## Current State

Conductor currently has **two different patterns** for backend selection:

### Pattern 1: Modern (Postgres, OpenSearch, SQLite)
```properties
conductor.indexing.enabled=true
conductor.indexing.type=postgres|opensearch|sqlite
```

### Pattern 2: Legacy (Elasticsearch)
```properties
conductor.indexing.enabled=true
conductor.indexing.type=elasticsearch
conductor.elasticsearch.version=6|7
```

The Elasticsearch pattern is inconsistent because it requires TWO properties to select the backend version.

## Proposed Unified Approach

Use `conductor.indexing.type` with **version-specific values** for all backends:

```properties
# Global toggle (defaults to true)
conductor.indexing.enabled=true

# Backend selection with explicit versioning
conductor.indexing.type=opensearch2      # OpenSearch 2.x
conductor.indexing.type=opensearch3      # OpenSearch 3.x
conductor.indexing.type=elasticsearch6   # Elasticsearch 6.x (legacy)
conductor.indexing.type=elasticsearch7   # Elasticsearch 7.x
conductor.indexing.type=postgres         # Postgres (no version needed)
conductor.indexing.type=sqlite           # SQLite (no version needed)
```

### Benefits

1. **Single property drives backend selection** - No need for separate version properties
2. **Explicit versioning** - Clear which backend version you're using
3. **Consistent pattern** - All backends follow the same configuration approach
4. **Future-proof** - Easy to add new versions (opensearch4, elasticsearch8, etc.)
5. **No magic defaults** - User explicitly chooses their backend version

## Implementation Plan for OpenSearch

### Phase 1: Create Version-Specific Modules

Create two new modules following the `es6-persistence` / `es7-persistence` precedent:

```
os-persistence-v2/          # OpenSearch 2.x support
├── build.gradle            # opensearch-client 2.x dependencies
└── src/main/java/com/netflix/conductor/os2/
    └── config/
        └── OpenSearchConditions.java    # Checks for "opensearch2"

os-persistence-v3/          # OpenSearch 3.x support
├── build.gradle            # opensearch-client 3.x dependencies
└── src/main/java/com/netflix/conductor/os3/
    └── config/
        └── OpenSearchConditions.java    # Checks for "opensearch3"
```

### Phase 2: Update Condition Classes

Each module checks for its specific `conductor.indexing.type` value:

**os-persistence-v2/src/main/java/com/netflix/conductor/os2/config/OpenSearchConditions.java:**
```java
package com.netflix.conductor.os2.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class OpenSearchConditions {

    private OpenSearchConditions() {}

    public static class OpenSearchV2Enabled extends AllNestedConditions {

        OpenSearchV2Enabled() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(
                name = "conductor.indexing.enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class enabledIndexing {}

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch2")
        static class enabledOS2 {}
    }
}
```

**os-persistence-v3/src/main/java/com/netflix/conductor/os3/config/OpenSearchConditions.java:**
```java
package com.netflix.conductor.os3.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class OpenSearchConditions {

    private OpenSearchConditions() {}

    public static class OpenSearchV3Enabled extends AllNestedConditions {

        OpenSearchV3Enabled() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @SuppressWarnings("unused")
        @ConditionalOnProperty(
                name = "conductor.indexing.enabled",
                havingValue = "true",
                matchIfMissing = true)
        static class enabledIndexing {}

        @SuppressWarnings("unused")
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "opensearch3")
        static class enabledOS3 {}
    }
}
```

### Phase 3: Update Configuration Classes

Each module's main configuration class uses the appropriate condition:

```java
@Configuration
@EnableConfigurationProperties(OpenSearchProperties.class)
@Conditional(OpenSearchConditions.OpenSearchV2Enabled.class)  // or OpenSearchV3Enabled
public class OpenSearchConfiguration {
    // Configuration beans...
}
```

### Phase 4: Update Server Module Dependencies

```gradle
// server/build.gradle
dependencies {
    runtimeOnly project(':conductor-os-persistence-v2')
    runtimeOnly project(':conductor-os-persistence-v3')
}
```

Spring Boot will activate only the module matching the configured `conductor.indexing.type` value.

### Phase 5: Backward Compatibility (Optional)

To support existing configurations using `conductor.indexing.type=opensearch`, we could:

1. Keep the current `os-persistence` module temporarily
2. Add deprecation warnings
3. Document migration path to `opensearch2` or `opensearch3`
4. Remove in a future major version

**OR** choose not to support the generic "opensearch" value and require users to specify version.

## Implementation Plan for Elasticsearch (Optional)

The same pattern can be applied to Elasticsearch to unify the configuration approach:

### Update Condition Classes

**es6-persistence:**
```java
@ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch6")
```

**es7-persistence:**
```java
@ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch7")
```

### Backward Compatibility

Support BOTH patterns during migration:

```java
public static class ElasticSearchV7Enabled extends AnyNestedCondition {

    ElasticSearchV7Enabled() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    // New unified pattern
    @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch7")
    static class newPattern {}

    // Legacy pattern
    static class LegacyPattern extends AllNestedConditions {
        @ConditionalOnProperty(name = "conductor.indexing.type", havingValue = "elasticsearch")
        static class typeES {}

        @ConditionalOnProperty(name = "conductor.elasticsearch.version", havingValue = "7")
        static class version7 {}
    }
}
```

This allows both configurations to work during the transition period.

## Configuration Examples

### OpenSearch 2.x
```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch2
conductor.elasticsearch.url=http://localhost:9200
conductor.elasticsearch.indexPrefix=conductor_
```

### OpenSearch 3.x
```properties
conductor.indexing.enabled=true
conductor.indexing.type=opensearch3
conductor.elasticsearch.url=http://localhost:9200
conductor.elasticsearch.indexPrefix=conductor_
```

### Elasticsearch 7 (new pattern)
```properties
conductor.indexing.enabled=true
conductor.indexing.type=elasticsearch7
conductor.elasticsearch.url=http://localhost:9200
```

### Elasticsearch 7 (legacy pattern - still supported)
```properties
conductor.indexing.enabled=true
conductor.indexing.type=elasticsearch
conductor.elasticsearch.version=7
conductor.elasticsearch.url=http://localhost:9200
```

## Testing Strategy

### Unit Tests
- Test each condition class activates only with correct property value
- Test that multiple modules don't conflict when both present on classpath

### Integration Tests
- Docker compose tests for each backend version
- Verify only configured backend activates
- Test backward compatibility with legacy patterns

### Test Property Examples

**OpenSearch 2:**
```java
@TestPropertySource(properties = {
    "conductor.indexing.type=opensearch2",
    "conductor.elasticsearch.url=http://localhost:9200"
})
```

**OpenSearch 3:**
```java
@TestPropertySource(properties = {
    "conductor.indexing.type=opensearch3",
    "conductor.elasticsearch.url=http://localhost:9200"
})
```

## Migration Guide

### For Existing OpenSearch Users

**Before:**
```properties
conductor.elasticsearch.version=0   # Magic value for OpenSearch
conductor.elasticsearch.url=http://localhost:9200
```

**After:**
```properties
conductor.indexing.type=opensearch2  # or opensearch3
conductor.elasticsearch.url=http://localhost:9200
```

### For Existing Elasticsearch Users

**Before:**
```properties
conductor.indexing.type=elasticsearch
conductor.elasticsearch.version=7
conductor.elasticsearch.url=http://localhost:9200
```

**After (recommended):**
```properties
conductor.indexing.type=elasticsearch7
conductor.elasticsearch.url=http://localhost:9200
```

**After (legacy - still supported):**
```properties
conductor.indexing.type=elasticsearch
conductor.elasticsearch.version=7
conductor.elasticsearch.url=http://localhost:9200
```

## Documentation Updates

### Configuration Reference
- Document all supported `conductor.indexing.type` values
- Show version-specific configuration examples
- Explain backward compatibility for legacy patterns

### Getting Started Guides
- Update OpenSearch setup guide with new configuration
- Update Elasticsearch setup guide (if migrated to new pattern)
- Add migration guide for existing deployments

### Docker Compose Examples
- Update docker-compose files to use new configuration pattern
- Provide examples for each backend version

## Related Issues

- **#678** - Epic: Improve OpenSearch Support and Configuration
- **#668** - Decouple OpenSearch configuration from Elasticsearch namespace
- **#675** - PR: Implements conductor.opensearch.* namespace (related but different approach)
- **#650** - Upgrade opensearch-client (blocked until module split complete)

## Decision Points

1. **Should we support backward compatibility for `conductor.indexing.type=opensearch`?**
   - Option A: Require explicit version (opensearch2/opensearch3) - cleaner
   - Option B: Support generic "opensearch" with default version - easier migration

2. **Should we migrate Elasticsearch to unified pattern?**
   - Option A: Migrate ES6/ES7 to elasticsearch6/elasticsearch7 pattern
   - Option B: Leave ES6/ES7 as-is with legacy pattern
   - Option C: Support both patterns during transition

3. **How to handle the opensearch.* vs elasticsearch.* properties namespace?**
   - This doc focuses on `conductor.indexing.type`
   - Property namespace (PR #675) is a separate but related concern
   - Should work together: `conductor.indexing.type=opensearch2` + `conductor.opensearch.*` properties

## Next Steps

1. Review and approve this configuration strategy
2. Decide on backward compatibility approach
3. Implement os-persistence-v2 module
4. Implement os-persistence-v3 module
5. Update server module dependencies
6. Add integration tests
7. Update documentation
8. Decide whether to migrate Elasticsearch modules to unified pattern
