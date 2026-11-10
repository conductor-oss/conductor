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
- **Qdrant**

## Configuration Format

Vector databases are configured using a list-based approach under `conductor.vectordb.instances`:

```yaml
conductor:
  vectordb:
    instances:
      - name: "instance-name"        # Unique identifier for this instance
        type: "database-type"        # Type: postgres, mongodb, pinecone, or qdrant
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

### Qdrant

A local Qdrant instance, for example one started with `docker run -p 6333:6333 -p 6334:6334 qdrant/qdrant`:

```yaml
conductor:
  vectordb:
    instances:
      - name: "qdrant-local"
        type: "qdrant"
        qdrant:
          host: "localhost"
          port: 6334                    # gRPC port
          distance: "cosine"            # Options: cosine, euclid, dot, manhattan
```

A Qdrant Cloud cluster:

```yaml
conductor:
  vectordb:
    instances:
      - name: "qdrant-cloud"
        type: "qdrant"
        qdrant:
          host: "xyz-example.eu-central.aws.cloud.qdrant.io"
          port: 6334
          useTls: true
          apiKey: "your-qdrant-api-key"
```

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

      - name: "qdrant-search"
        type: "qdrant"
        qdrant:
          host: "localhost"
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

## Qdrant Configuration Options

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `host` | String | "localhost" | Qdrant host name |
| `port` | Integer | 6334 | Qdrant gRPC port |
| `apiKey` | String | null | API key for Qdrant Cloud or a secured instance |
| `useTls` | Boolean | false | Use TLS for the gRPC connection. Qdrant Cloud requires `true` |
| `distance` | String | "cosine" | Distance for collections that Conductor creates (cosine, euclid, dot, manhattan). The aliases `l2` and `inner_product` map to euclid and dot |
| `timeout` | Duration | 30s | Deadline for each call to Qdrant, for example `10s`. A call that takes longer fails, and Conductor retries the task. `0` means no deadline |

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

### Qdrant

- Use the gRPC port (6334 by default). The REST port (6333) does not accept gRPC connections.
- Set `useTls: true` and `apiKey` for Qdrant Cloud. An error that says `First received frame was not SETTINGS` means the server uses TLS and `useTls` is false.
- All vectors in one collection must have the same size. A vector of a different size fails with a dimension error from Qdrant.
- Conductor writes one unnamed vector per point. A collection that you created with named vectors fails with a `Not existing vector name` error.

