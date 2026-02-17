# OpenSearch Java Client 3.x Migration Guide

## Overview

This document guides the migration from OpenSearch High-Level REST Client (used in v2) to the new opensearch-java 3.x client (for v3).

## Key API Changes

### 1. Client Initialization

**Old (v2):**
```java
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.RestClient;

RestHighLevelClient client = new RestHighLevelClient(
    RestClient.builder(new HttpHost(host, port, "http"))
);
```

**New (v3):**
```java
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.rest_client.RestClientTransport;

RestClient restClient = RestClient.builder(new HttpHost(host, port, "http")).build();
OpenSearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
OpenSearchClient client = new OpenSearchClient(transport);
```

### 2. Query Building

**Old (v2):**
```java
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.BoolQueryBuilder;

BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
    .must(QueryBuilders.matchQuery("field", "value"))
    .filter(QueryBuilders.rangeQuery("age").gte(18));
```

**New (v3):**
```java
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;

Query query = Query.of(q -> q
    .bool(b -> b
        .must(m -> m.match(t -> t.field("field").query("value")))
        .filter(f -> f.range(r -> r.field("age").gte(JsonData.of(18))))
    )
);
```

### 3. Index Operations

**Old (v2):**
```java
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.xcontent.XContentType;

IndexRequest request = new IndexRequest("index")
    .id("1")
    .source(jsonString, XContentType.JSON);

IndexResponse response = client.index(request, RequestOptions.DEFAULT);
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;

IndexResponse response = client.index(i -> i
    .index("index")
    .id("1")
    .document(myObject)  // Auto-serialized via Jackson
);
```

### 4. Search Operations

**Old (v2):**
```java
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.SearchHit;

SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
    .query(queryBuilder)
    .from(0)
    .size(10);

SearchRequest searchRequest = new SearchRequest("index")
    .source(sourceBuilder);

SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
SearchHit[] hits = response.getHits().getHits();
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

SearchResponse<MyDoc> response = client.search(s -> s
    .index("index")
    .query(query)
    .from(0)
    .size(10),
    MyDoc.class
);

List<Hit<MyDoc>> hits = response.hits().hits();
```

### 5. Bulk Operations

**Old (v2):**
```java
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;

BulkRequest bulkRequest = new BulkRequest();
bulkRequest.add(new IndexRequest("index").id("1").source(...));
bulkRequest.add(new DeleteRequest("index", "2"));

BulkResponse response = client.bulk(bulkRequest, RequestOptions.DEFAULT);
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;

BulkResponse response = client.bulk(b -> b
    .operations(op -> op.index(i -> i.index("index").id("1").document(doc)))
    .operations(op -> op.delete(d -> d.index("index").id("2")))
);
```

### 6. Delete Operations

**Old (v2):**
```java
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;

DeleteRequest request = new DeleteRequest("index", "id");
DeleteResponse response = client.delete(request, RequestOptions.DEFAULT);
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.DeleteResponse;

DeleteResponse response = client.delete(d -> d
    .index("index")
    .id("id")
);
```

### 7. Get Operations

**Old (v2):**
```java
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;

GetRequest request = new GetRequest("index", "id");
GetResponse response = client.get(request, RequestOptions.DEFAULT);
String sourceAsString = response.getSourceAsString();
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;

GetResponse<MyDoc> response = client.get(g -> g
    .index("index")
    .id("id"),
    MyDoc.class
);
MyDoc document = response.source();
```

### 8. Count Operations

**Old (v2):**
```java
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;

CountRequest countRequest = new CountRequest("index")
    .query(queryBuilder);

CountResponse response = client.count(countRequest, RequestOptions.DEFAULT);
long count = response.getCount();
```

**New (v3):**
```java
import org.opensearch.client.opensearch.core.CountRequest;
import org.opensearch.client.opensearch.core.CountResponse;

CountResponse response = client.count(c -> c
    .index("index")
    .query(query)
);
long count = response.count();
```

## Common Patterns

### Pattern 1: String-based JSON Source → Typed Documents

**Old:** Frequently used string JSON
```java
.source(jsonString, XContentType.JSON)
```

**New:** Use typed POJOs
```java
.document(myTypedObject)  // Jackson handles serialization
```

### Pattern 2: Imperative Builder → Functional Builder

**Old:** Imperative chaining
```java
BoolQueryBuilder query = QueryBuilders.boolQuery();
query.must(QueryBuilders.matchQuery("field", "value"));
query.filter(QueryBuilders.rangeQuery("age").gte(18));
```

**New:** Functional lambda builders
```java
Query query = Query.of(q -> q.bool(b -> b
    .must(m -> m.match(t -> t.field("field").query("value")))
    .filter(f -> f.range(r -> r.field("age").gte(JsonData.of(18))))
));
```

### Pattern 3: XContentType → JsonData

**Old:**
```java
import org.opensearch.common.xcontent.XContentType;
.source(jsonBytes, XContentType.JSON)
```

**New:**
```java
import org.opensearch.client.json.JsonData;
.document(JsonData.of(value))  // For raw JSON
```

## Migration Steps for OpenSearchRestDAO

### Step 1: Update Dependencies (DONE)
```gradle
implementation 'org.opensearch.client:opensearch-java:3.0.0'
implementation "org.opensearch.client:opensearch-rest-client:3.0.0"
implementation "org.opensearch.client:opensearch-rest-high-level-client:3.0.0"  // Keep for transition
```

### Step 2: Update Client Initialization

**File:** `OpenSearchRestDAO.java` constructor

**Change:**
```java
// Remove:
private final RestHighLevelClient openSearchClient;

// Add:
private final OpenSearchClient openSearchClient;
private final RestClient restClient;

// Update constructor to build new client
```

### Step 3: Migrate Query Builder Methods

**Method to migrate:** `boolQueryBuilder(String structuredQuery, String freeTextQuery)`

**Current signature:**
```java
private QueryBuilder boolQueryBuilder(String structuredQuery, String freeTextQuery)
```

**New signature:**
```java
private Query boolQuery(String structuredQuery, String freeTextQuery)
```

**Implementation changes:**
- Replace `QueryBuilders.*` with lambda builders
- Return `Query` instead of `QueryBuilder`
- Use functional composition instead of imperative building

### Step 4: Migrate Search Methods

Methods to update:
- `searchObjectsViaExpression()`
- `searchObjects()`
- `searchWorkflowSummary()`
- `searchTaskSummary()`

**Key changes:**
- Replace `SearchRequest` import (old → new package)
- Replace `SearchSourceBuilder` with lambda builders
- Update response handling (`SearchHits` → `hits().hits()`)
- Add generic type parameters (`SearchResponse<T>`)

### Step 5: Migrate Index/Update Methods

Methods to update:
- `indexObject()`
- `updateObject()`
- `addTaskExecutionLogs()`

**Key changes:**
- Use lambda builders for `IndexRequest`
- Replace `XContentType.JSON` with typed documents
- Update response handling

### Step 6: Migrate Delete Methods

Methods to update:
- `deleteObject()`
- `asyncBulkDelete()`

### Step 7: Migrate Bulk Operations

Methods to update:
- `bulkIndexObjects()`
- `asyncBulkIndexObjects()`

**Major changes needed:**
- Replace `BulkRequest.add()` with lambda builders
- Use `BulkOperation` for each operation
- Update `BulkProcessor` initialization (if used)

### Step 8: Fix Sorting

**Old:**
```java
import org.opensearch.search.sort.FieldSortBuilder;
import org.opensearch.search.sort.SortOrder;

searchSourceBuilder.sort(new FieldSortBuilder(field).order(order));
```

**New:**
```java
import org.opensearch.client.opensearch._types.SortOrder;

.sort(s -> s.field(f -> f.field(fieldName).order(sortOrder)))
```

### Step 9: Update Exception Handling

**Old:**
```java
catch (IOException e) {
    // Handle
}
```

**New:** Same, but also handle:
```java
catch (OpenSearchException e) {
    // New exception types from opensearch-java client
}
```

## Testing Strategy

1. **Unit tests first**
   - Test query building in isolation
   - Test serialization/deserialization
   - Mock the OpenSearchClient

2. **Integration tests**
   - Use OpenSearch Testcontainers
   - Test against real OpenSearch 3.x instance
   - Verify results match v2 behavior

3. **Side-by-side comparison**
   - Run same workflows on v2 and v3
   - Compare indexed documents
   - Compare search results

## Estimated Effort

- **Step 1-2:** Client setup - 2 hours
- **Step 3:** Query builders - 4 hours
- **Step 4:** Search methods - 8 hours
- **Step 5-7:** CRUD operations - 8 hours
- **Step 8-9:** Sorting, exceptions - 2 hours
- **Testing:** 8 hours
- **Buffer for unknowns:** 8 hours

**Total:** ~40 hours (1 week for experienced dev, 2 weeks with testing/review)

## References

- [OpenSearch Java Client Documentation](https://opensearch.org/docs/latest/clients/java/)
- [OpenSearch Java Client GitHub](https://github.com/opensearch-project/opensearch-java)
- [Migration Guide from Elasticsearch](https://opensearch.org/docs/latest/clients/java/#migrating-from-the-elasticsearch-java-client)
