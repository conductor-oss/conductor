# OpenSearch Persistence - DEPRECATED

⚠️ **This module is deprecated and provides only a migration error message.**

## What Happened?

The generic `conductor.indexing.type=opensearch` configuration has been replaced with version-specific modules:

- **`os-persistence-v2`** - For OpenSearch 2.x
- **`os-persistence-v3`** - For OpenSearch 3.x

This change enables proper dependency isolation between OpenSearch 2.x and 3.x clients, which use incompatible package namespaces.

## Migration

Change your configuration from:

```properties
conductor.indexing.type=opensearch
conductor.opensearch.url=http://localhost:9200
```

To one of:

```properties
# For OpenSearch 2.x
conductor.indexing.type=opensearch2
conductor.opensearch.url=http://localhost:9200
```

```properties
# For OpenSearch 3.x
conductor.indexing.type=opensearch3
conductor.opensearch.url=http://localhost:9200
```

All other `conductor.opensearch.*` properties remain the same.

## Why the Change?

- OpenSearch 2.x and 3.x clients use identical package names (`org.opensearch.client.*`)
- Having both on the classpath causes conflicts
- Version-specific modules use shadow plugin to relocate packages and avoid conflicts
- Follows the same pattern as `es6-persistence` and `es7-persistence`

## See Also

- Issue #678: https://github.com/conductor-oss/conductor/issues/678
- Migration Guide: [link to docs when published]
