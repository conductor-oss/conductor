# Vector Database Configuration

This document describes the configuration format for vector databases in Conductor.

## Overview

Conductor supports multiple vector database providers with the ability to configure **multiple named instances** of each type. This allows you to:

- Use multiple databases of the same type (e.g., multiple PostgreSQL instances)
- Connect to different environments (prod, dev, staging)
- Separate concerns by use case (embeddings, search, recommendations)

## Supported Vector Databases

- **PostgreSQL** (with pgvector extension)
- **MongoDB** (with Atlas Vector Search)
- **Pinecone**
- **SQLite** (with the [sqlite-vec](https://github.com/asg017/sqlite-vec) extension) — embedded, zero-infrastructure backend for local development, demos and small deployments

## Configuration Format

Vector databases are configured using a list-based approach under `conductor.vectordb.instances`:

```yaml
conductor:
  vectordb:
    instances:
      - name: "instance-name"        # Unique identifier for this instance
        type: "database-type"        # Type: postgres, mongodb, pinecone, or sqlite
        <type-specific-config>:      # Configuration block for the database type
          # ... type-specific properties
```

## Configuration Examples

### Single PostgreSQL Instance

```yaml
conductor:
  vectordb:
    instances:
      - name: "postgres-main"
        type: "postgres"
        postgres:
          datasourceURL: "jdbc:postgresql://localhost:5432/vectors"
          user: "conductor"
          password: "secret"
          dimensions: 1536
          connectionPoolSize: 10
          indexingMethod: "hnsw"        # Options: hnsw, ivfflat
          distanceMetric: "cosine"      # Options: l2, cosine, inner_product
          tablePrefix: "conductor"
```

### Multiple PostgreSQL Instances

```yaml
conductor:
  vectordb:
    instances:
      - name: "postgres-prod"
        type: "postgres"
        postgres:
          datasourceURL: "jdbc:postgresql://prod-db:5432/vectors"
          user: "conductor"
          password: "prod-secret"
          dimensions: 1536
          
      - name: "postgres-dev"
        type: "postgres"
        postgres:
          datasourceURL: "jdbc:postgresql://dev-db:5432/vectors"
          user: "conductor"
          password: "dev-secret"
          dimensions: 768
```

### MongoDB Atlas Vector Search

```yaml
conductor:
  vectordb:
    instances:
      - name: "mongodb-embeddings"
        type: "mongodb"
        mongodb:
          connectionString: "mongodb+srv://user:pass@cluster.mongodb.net/"
          database: "conductor"
          collection: "embeddings"
          numCandidates: 100
```

### Pinecone

```yaml
conductor:
  vectordb:
    instances:
      - name: "pinecone-search"
        type: "pinecone"
        pinecone:
          apiKey: "your-pinecone-api-key"
```

### SQLite (sqlite-vec)

```yaml
conductor:
  vectordb:
    instances:
      - name: "sqlite-local"
        type: "sqlite"
        sqlite:
          dbPath: "/var/lib/conductor/vectordb.db"   # use ":memory:" for an ephemeral DB
          dimensions: 1536
          distanceMetric: "cosine"                    # Options: l2, cosine, l1
          connectionPoolSize: 5
          tablePrefix: "conductor"
          # extensionPath is optional — the native vec0 binary is bundled in the jar.
          # Set it only to override with a custom build:
          # extensionPath: "/opt/sqlite-vec/vec0"
```

> **The native `vec0` extension is bundled.** Conductor ships the official, checksum-pinned sqlite-vec
> loadable binaries for linux (x86_64/aarch64), macOS (x86_64/aarch64) and windows (x86_64) inside the
> AI jar, and extracts the right one at runtime — so `extensionPath` is normally unnecessary. Provide
> `extensionPath` (the file name without its platform suffix, e.g. `/opt/sqlite-vec/vec0`) only to
> override the bundled binary or to support a platform that is not bundled. sqlite-vec is pre-v1 and
> performs exact (brute-force) KNN with no ANN index, making it suitable for thousands to low-millions
> of vectors.

### Zero-config default (SQLite persistence + AI)

When the server runs with **both** `conductor.db.type=sqlite` and `conductor.integrations.ai.enabled=true`,
Conductor automatically registers a vector DB instance named **`default`** backed by the bundled
sqlite-vec extension — no `conductor.vectordb.instances` entry required. Workflows can target it with
`"vectorDB": "default"`. The defaults below can be overridden:

```yaml
conductor:
  vectordb:
    sqlite-default:
      name: "default"            # instance name workflows reference
      db-path: ""                # default: a *_vectordb.db file next to the persistence DB
      dimensions: 256            # must match the embedding model's output dimensions
      distance-metric: "cosine"  # l2, cosine or l1
      extension-path: ""         # default: the bundled vec0 binary
```

An explicitly configured instance named `default` takes precedence over the auto-registered one.

### Mixed Configuration (Multiple Types)

```yaml
conductor:
  vectordb:
    instances:
      - name: "postgres-prod"
        type: "postgres"
        postgres:
          datasourceURL: "jdbc:postgresql://prod:5432/vectors"
          user: "conductor"
          password: "secret"
          dimensions: 1536
          
      - name: "pinecone-embeddings"
        type: "pinecone"
        pinecone:
          apiKey: "pk-xxx"
          
      - name: "mongodb-cache"
        type: "mongodb"
        mongodb:
          connectionString: "mongodb://localhost:27017"
          database: "conductor"
```

## Usage in Workflows

When using vector database tasks in your workflows, reference the instance by its configured name:

```json
{
  "name": "store_embeddings",
  "taskReferenceName": "store_embeddings_ref",
  "type": "LLM_STORE_EMBEDDINGS",
  "inputParameters": {
    "vectorDB": "postgres-prod",
    "index": "documents",
    "namespace": "my_namespace",
    "embeddings": "${embedding_task.output.embeddings}",
    "metadata": {
      "documentId": "${workflow.input.docId}"
    }
  }
}
```

## PostgreSQL Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `datasourceURL` | String | Required | JDBC connection URL |
| `user` | String | Required | Database username |
| `password` | String | Required | Database password |
| `dimensions` | Integer | 256 | Vector dimensions |
| `connectionPoolSize` | Integer | 5 | Connection pool size |
| `indexingMethod` | String | "hnsw" | Index method (hnsw or ivfflat) |
| `distanceMetric` | String | "l2" | Distance metric (l2, cosine, inner_product) |
| `invertedListCount` | Integer | 100 | IVFFlat index parameter |
| `tablePrefix` | String | null | Prefix for table names |

## MongoDB Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connectionString` | String | Required | MongoDB connection string |
| `database` | String | Required | Database name |
| `collection` | String | Optional | Collection name |
| `numCandidates` | Integer | Optional | Vector search parameter |

## Pinecone Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `apiKey` | String | Required | Pinecone API key |

## SQLite Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `dbPath` | String | Required | Path to the SQLite database file, or `:memory:` for an in-memory DB |
| `extensionPath` | String | bundled | Override path to the `vec0` extension (without platform suffix); defaults to the binary bundled in the jar |
| `dimensions` | Integer | 256 | Vector dimensions |
| `distanceMetric` | String | "l2" | Distance metric (l2, cosine, l1) |
| `connectionPoolSize` | Integer | 5 | Connection pool size (forced to 1 for `:memory:`) |
| `tablePrefix` | String | null | Prefix for table names |

## Migration from Old Configuration

### Old Format (Single Instance Per Type)

```yaml
conductor:
  vectordb:
    postgres:
      datasourceURL: "jdbc:postgresql://localhost:5432/vectors"
      user: "conductor"
      password: "secret"
```

### New Format (Named Instances)

```yaml
conductor:
  vectordb:
    instances:
      - name: "pgvectordb"           # Use old type name for backward compatibility
        type: "postgres"
        postgres:
          datasourceURL: "jdbc:postgresql://localhost:5432/vectors"
          user: "conductor"
          password: "secret"
```

**Note:** The type identifiers have been simplified:
- `pgvectordb` → `postgres`
- `mongovectordb` → `mongodb`  
- `pineconedb` → `pinecone`

However, for backward compatibility, you can still reference instances using the old type names if you name your instance accordingly.

## Best Practices

1. **Use descriptive names**: Choose instance names that clearly indicate their purpose (e.g., `postgres-prod`, `pinecone-embeddings-search`)

2. **Separate environments**: Use different instances for different environments to avoid accidental data mixing

3. **Optimize dimensions**: Configure `dimensions` to match your embedding model to avoid runtime errors

4. **Connection pooling**: Adjust `connectionPoolSize` based on your workload and database capacity

5. **Index selection**: 
   - Use `hnsw` for better query performance (default)
   - Use `ivfflat` for faster indexing with slightly lower query performance

6. **Distance metrics**:
   - Use `cosine` for normalized embeddings
   - Use `l2` (Euclidean) for absolute distances
   - Use `inner_product` for dot product similarity

## Troubleshooting

### Instance Not Found

If you see an error like "Vector DB instance not found: xyz", check:

1. The instance name in your workflow matches the configured name exactly
2. The instance is properly configured in your application.yml/properties
3. The application has been restarted after configuration changes

### PostgreSQL Connection Issues

- Ensure pgvector extension is installed: `CREATE EXTENSION vector;`
- Verify JDBC URL format and network connectivity
- Check database user permissions

### MongoDB Vector Search

- Vector search requires MongoDB Atlas or MongoDB 6.0+ with Atlas Search
- Ensure vector search index is created on your collection
- Local MongoDB containers don't support vector search

### Pinecone

- Verify API key is valid and has necessary permissions
- Ensure index exists in your Pinecone account before using it

### SQLite (sqlite-vec)

- "no such function: load_extension" — extension loading is disabled; the backend enables it at the
  driver level, so this usually means the driver in use does not permit it
- "no such module: vec0" — the `vec0` extension could not be loaded. The binary is bundled for common
  platforms; if your platform is not bundled (the log shows "No bundled sqlite-vec extension for ..."),
  install vec0 and set `extensionPath` to the compiled extension (Linux `.so`, macOS `.dylib`, Windows
  `.dll`), ensuring it is readable by the server process
- "Embeddings must be of dimensions : N" — the instance's `dimensions` must equal the embedding model's
  output size; for the auto-registered `default` instance set `conductor.vectordb.sqlite-default.dimensions`
  or request that many dimensions from the embedding model
- Local-only: each instance maps to a single SQLite file on the server host and is not shared across
  a cluster — use pgvector/Pinecone/MongoDB for distributed deployments
