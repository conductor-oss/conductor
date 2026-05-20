# Elasticsearch 6.x Persistence - DEPRECATED

⚠️ **This module is deprecated and provides only a migration error message.**

## What Happened?

Elasticsearch 6.x reached end-of-life in November 2020 and is no longer supported.

The `conductor.indexing.type=elasticsearch_v6` configuration has been deprecated. Users should migrate to Elasticsearch 7.x.

## Migration

Change your configuration from:

```properties
conductor.indexing.type=elasticsearch_v6
conductor.elasticsearch.url=http://localhost:9200
```

To:

```properties
conductor.indexing.type=elasticsearch
conductor.elasticsearch.url=http://localhost:9200
```

All other `conductor.elasticsearch.*` properties remain the same.

## Why the Change?

- Elasticsearch 6.x reached end-of-life in November 2020
- Security vulnerabilities are no longer patched
- Elasticsearch 7.x provides better performance and features
- Reduces maintenance burden of supporting legacy versions

## Legacy Code Reference

If you need the original Elasticsearch 6.x persistence module code for reference, it has been archived at:

https://github.com/conductor-oss/conductor-es6-persistence

**Note:** The archived module is no longer maintained and should not be used in production.

## See Also

- Legacy Elasticsearch 6.x Module: https://github.com/conductor-oss/conductor-es6-persistence
- Elasticsearch 7.x Support: Use `conductor.indexing.type=elasticsearch` instead
