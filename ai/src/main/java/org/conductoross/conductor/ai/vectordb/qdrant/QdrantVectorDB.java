/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.vectordb.qdrant;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;

import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.GetCollectionInfoRequest;
import io.qdrant.client.grpc.Collections.KeywordIndexParams;
import io.qdrant.client.grpc.Collections.PayloadIndexParams;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.QueryPoints;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.UpsertPoints;
import lombok.extern.slf4j.Slf4j;

import static io.qdrant.client.ConditionFactory.matchKeyword;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.QueryFactory.nearest;
import static io.qdrant.client.ValueFactory.list;
import static io.qdrant.client.ValueFactory.nullValue;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;
import static io.qdrant.client.WithPayloadSelectorFactory.enable;

/**
 * Qdrant implementation of {@link VectorDB}.
 *
 * <p>Data model:
 *
 * <ul>
 *   <li>The Conductor {@code index} is the Qdrant collection. Conductor creates the collection on
 *       the first write, sized to the first vector it receives.
 *   <li>The Conductor {@code namespace} is the {@code namespace} payload field. Conductor makes
 *       sure the field has an index (a tenant keyword index when Conductor creates it) and filters
 *       on it at search time, so many namespaces share one collection. This follows the Qdrant
 *       multitenancy guidance. A blank namespace means no namespace.
 *   <li>Qdrant point IDs must be unsigned integers or UUIDs, but Conductor document IDs are free
 *       text. Conductor derives a name-based UUID from the namespace and the document ID, so
 *       writing the same document ID again in the same namespace replaces the point. The original
 *       ID is kept in the {@code doc_id} payload field and returned by search.
 *   <li>Metadata is stored as a nested payload object, so Qdrant filters can use its fields. Qdrant
 *       payload integers are 64-bit, so a larger integer is stored as its exact decimal text.
 * </ul>
 *
 * <p>Errors: bad input and Qdrant errors that a retry cannot fix (such as a dimension mismatch or a
 * rejected API key) throw {@link NonRetryableException}, so the task fails with a terminal error.
 * Connection problems and timeouts throw a plain runtime exception, so Conductor retries the task
 * according to its task definition.
 */
@Slf4j
public class QdrantVectorDB extends VectorDB {

    public static final String TYPE = "qdrant";

    static final String FIELD_DOC_ID = "doc_id";
    static final String FIELD_PARENT_DOC_ID = "parent_doc_id";
    static final String FIELD_TEXT = "text";
    static final String FIELD_NAMESPACE = "namespace";
    static final String FIELD_METADATA = "metadata";

    /** gRPC status codes that mean the same request will fail again, so retrying is pointless. */
    private static final Set<Status.Code> PERMANENT_ERRORS =
            EnumSet.of(
                    Status.Code.INVALID_ARGUMENT,
                    Status.Code.NOT_FOUND,
                    Status.Code.ALREADY_EXISTS,
                    Status.Code.UNAUTHENTICATED,
                    Status.Code.PERMISSION_DENIED,
                    Status.Code.OUT_OF_RANGE,
                    Status.Code.UNIMPLEMENTED);

    /** Reads of a collection that is still being created: 5 attempts, about 2 seconds in all. */
    private static final int COLLECTION_READ_ATTEMPTS = 5;

    private static final Duration COLLECTION_READ_BACKOFF = Duration.ofMillis(200);

    private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final QdrantConfig config;

    /**
     * Collections this instance has confirmed or created, with the namespace index in place. A
     * write to a cached collection skips the collection check.
     */
    private final Set<String> knownCollections = ConcurrentHashMap.newKeySet();

    private volatile QdrantClient client;

    public QdrantVectorDB(String name, QdrantConfig config) {
        super(name, TYPE);
        this.config = config;
    }

    @Override
    public int updateEmbeddings(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> metadata) {
        requireCollectionName(indexName);
        if (StringUtils.isBlank(id)) {
            throw new NonRetryableException("Document id is required for Qdrant");
        }
        requireEmbeddings(embeddings);
        // Validate before any call, so a bad setting fails the same way on every write.
        Distance distance = toDistance(config.getDistance());
        String ns = normalizeNamespace(namespace);

        Map<String, Value> payload = new HashMap<>();
        payload.put(FIELD_DOC_ID, value(id));
        payload.put(FIELD_TEXT, value(doc == null ? "" : doc));
        if (parentDocId != null) {
            payload.put(FIELD_PARENT_DOC_ID, value(parentDocId));
        }
        if (ns != null) {
            payload.put(FIELD_NAMESPACE, value(ns));
        }
        payload.put(FIELD_METADATA, toValue(metadata == null ? Map.of() : metadata));

        UpsertPoints upsert =
                UpsertPoints.newBuilder()
                        .setCollectionName(indexName)
                        .addPoints(
                                PointStruct.newBuilder()
                                        .setId(id(pointId(ns, id)))
                                        .setVectors(vectors(embeddings))
                                        .putAllPayload(payload)
                                        .build())
                        .setWait(true)
                        .build();

        QdrantClient qdrant = getClient();
        ensureCollection(qdrant, indexName, embeddings.size(), distance);
        String operation = "upsert into collection " + indexName;
        try {
            await(qdrant.upsertAsync(upsert), operation);
        } catch (RuntimeException e) {
            if (statusCode(e) != Status.Code.NOT_FOUND) {
                throw e;
            }
            // The collection was deleted after this instance cached it. Create it again and retry
            // once, so one deletion does not break every later write until a restart.
            log.info("Qdrant collection {} no longer exists, creating it again", indexName);
            knownCollections.remove(indexName);
            ensureCollection(qdrant, indexName, embeddings.size(), distance);
            await(qdrant.upsertAsync(upsert), operation);
        }
        return 1;
    }

    @Override
    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {
        requireCollectionName(indexName);
        requireEmbeddings(embeddings);
        if (maxResults < 1) {
            throw new NonRetryableException(
                    "maxResults must be 1 or larger for Qdrant search, got " + maxResults);
        }
        String ns = normalizeNamespace(namespace);

        QueryPoints.Builder query =
                QueryPoints.newBuilder()
                        .setCollectionName(indexName)
                        .setQuery(nearest(embeddings))
                        .setLimit(maxResults)
                        .setWithPayload(enable(true));
        if (ns != null) {
            query.setFilter(Filter.newBuilder().addMust(matchKeyword(FIELD_NAMESPACE, ns)).build());
        }

        List<ScoredPoint> points;
        try {
            // The raw gRPC stub, for the same reason as in getCollectionInfo: NOT_FOUND below is a
            // normal answer, not an error to log.
            points =
                    await(
                                    getClient().grpcClient().points().query(query.build()),
                                    "query collection " + indexName)
                            .getResultList();
        } catch (RuntimeException e) {
            if (statusCode(e) != Status.Code.NOT_FOUND) {
                throw e;
            }
            // Conductor creates a collection on its first write, so a search can run before
            // anything is indexed. Nothing indexed yet means no results.
            log.warn("Qdrant collection {} does not exist yet, returning no results", indexName);
            return new ArrayList<>();
        }

        List<IndexedDoc> matches = new ArrayList<>(points.size());
        for (ScoredPoint point : points) {
            Map<String, Value> payload = point.getPayloadMap();
            IndexedDoc indexedDoc =
                    new IndexedDoc(
                            stringField(payload, FIELD_DOC_ID),
                            stringField(payload, FIELD_PARENT_DOC_ID),
                            StringUtils.defaultString(stringField(payload, FIELD_TEXT)),
                            point.getScore());
            Value metadata = payload.get(FIELD_METADATA);
            if (metadata != null && metadata.hasStructValue()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadataMap = (Map<String, Object>) fromValue(metadata);
                indexedDoc.setMetadata(metadataMap);
            }
            matches.add(indexedDoc);
        }
        return matches;
    }

    /** A blank namespace means no namespace, for writes, point IDs, and search filters alike. */
    static String normalizeNamespace(String namespace) {
        return StringUtils.isBlank(namespace) ? null : namespace;
    }

    /**
     * Derives the Qdrant point ID. The NUL separator cannot appear in a namespace that comes from
     * workflow input JSON in practice, so distinct (namespace, id) pairs map to distinct names.
     */
    static UUID pointId(String namespace, String id) {
        String name = StringUtils.defaultString(normalizeNamespace(namespace)) + '\u0000' + id;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Makes sure the collection exists and its namespace field has an index. Creates a missing
     * collection. For an existing collection, creates the namespace index only if the field has no
     * index, so an index the owner made stays as it is.
     */
    private void ensureCollection(
            QdrantClient qdrant, String collectionName, int dimensions, Distance distance) {
        if (knownCollections.contains(collectionName)) {
            return;
        }
        CollectionInfo info = getCollectionInfo(qdrant, collectionName);
        if (info == null) {
            createCollection(qdrant, collectionName, dimensions, distance);
            createNamespaceIndex(qdrant, collectionName);
        } else if (!info.getPayloadSchemaMap().containsKey(FIELD_NAMESPACE)) {
            createNamespaceIndex(qdrant, collectionName);
        }
        knownCollections.add(collectionName);
    }

    /** Returns the collection info, or null if the collection does not exist. */
    private CollectionInfo getCollectionInfo(QdrantClient qdrant, String collectionName) {
        for (int attempt = 1; ; attempt++) {
            try {
                // The raw gRPC stub, not QdrantClient: the client logs every failed call at ERROR
                // level, and NOT_FOUND here is the normal answer for a new collection.
                return await(
                                qdrant.grpcClient()
                                        .collections()
                                        .get(
                                                GetCollectionInfoRequest.newBuilder()
                                                        .setCollectionName(collectionName)
                                                        .build()),
                                "read collection " + collectionName)
                        .getResult();
            } catch (RuntimeException e) {
                Status.Code code = statusCode(e);
                if (code == Status.Code.NOT_FOUND) {
                    return null;
                }
                // While another worker creates the collection, the collection exists but its
                // shards are not ready, and Qdrant answers INTERNAL. Wait briefly for it, so
                // parallel first writes do not fail an attempt.
                if (code != Status.Code.INTERNAL || attempt >= COLLECTION_READ_ATTEMPTS) {
                    throw e;
                }
                sleep(COLLECTION_READ_BACKOFF.multipliedBy(attempt));
            }
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for a Qdrant collection", e);
        }
    }

    private void createCollection(
            QdrantClient qdrant, String collectionName, int dimensions, Distance distance) {
        CreateCollection request =
                CreateCollection.newBuilder()
                        .setCollectionName(collectionName)
                        .setVectorsConfig(
                                VectorsConfig.newBuilder()
                                        .setParams(
                                                VectorParams.newBuilder()
                                                        .setSize(dimensions)
                                                        .setDistance(distance)
                                                        .build())
                                        .build())
                        .build();
        try {
            // The raw gRPC stub, for the same reason as in getCollectionInfo: ALREADY_EXISTS is
            // the normal answer when parallel workers create the same collection.
            boolean created =
                    await(
                                    qdrant.grpcClient().collections().create(request),
                                    "create collection " + collectionName)
                            .getResult();
            if (!created) {
                throw new RuntimeException("Qdrant did not create collection " + collectionName);
            }
            log.info(
                    "Created Qdrant collection {} with {} dimensions ({}) on instance {}",
                    collectionName,
                    dimensions,
                    distance,
                    name);
        } catch (RuntimeException e) {
            // Another worker can create the same collection between the check and the create.
            if (statusCode(e) != Status.Code.ALREADY_EXISTS) {
                throw e;
            }
        }
    }

    /**
     * Creates a tenant keyword index on the namespace field. It makes namespace-filtered search
     * efficient when many namespaces share the collection. Qdrant accepts a request for an index
     * that already exists with the same settings, so concurrent workers can all send it.
     */
    private void createNamespaceIndex(QdrantClient qdrant, String collectionName) {
        await(
                qdrant.createPayloadIndexAsync(
                        collectionName,
                        FIELD_NAMESPACE,
                        PayloadSchemaType.Keyword,
                        PayloadIndexParams.newBuilder()
                                .setKeywordIndexParams(
                                        KeywordIndexParams.newBuilder().setIsTenant(true).build())
                                .build(),
                        true,
                        null,
                        null),
                "create namespace index on collection " + collectionName);
    }

    private QdrantClient getClient() {
        QdrantClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = buildClient();
                    client = current;
                }
            }
        }
        return current;
    }

    private QdrantClient buildClient() {
        if (StringUtils.isBlank(config.getHost())) {
            throw new NonRetryableException(
                    "Qdrant host is not configured for vector DB instance " + name);
        }
        int port = config.getPort() != null ? config.getPort() : 6334;
        boolean useTls = Boolean.TRUE.equals(config.getUseTls());
        // The client's optional version check is a blocking call with no deadline. Against a host
        // that accepts connections but never answers, it would block this thread, and every other
        // thread waiting for the client, forever. So it stays off.
        QdrantGrpcClient.Builder builder =
                QdrantGrpcClient.newBuilder(config.getHost(), port, useTls, false);
        Duration timeout = config.getTimeout();
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            builder.withTimeout(timeout);
        }
        if (StringUtils.isNotBlank(config.getApiKey())) {
            builder.withApiKey(config.getApiKey());
        }
        log.info(
                "Connecting vector DB instance {} to Qdrant at {}:{} (tls: {}, timeout: {})",
                name,
                config.getHost(),
                port,
                useTls,
                timeout);
        return new QdrantClient(builder.build());
    }

    private static void requireCollectionName(String indexName) {
        if (StringUtils.isBlank(indexName)) {
            throw new NonRetryableException("Index (Qdrant collection name) is required");
        }
    }

    private static void requireEmbeddings(List<Float> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            throw new NonRetryableException("Embeddings are required for Qdrant");
        }
        for (Float f : embeddings) {
            if (f == null || f.isNaN() || f.isInfinite()) {
                throw new NonRetryableException(
                        "Embeddings must contain only finite numbers, got " + f);
            }
        }
    }

    static Distance toDistance(String distance) {
        if (StringUtils.isBlank(distance)) {
            return Distance.Cosine;
        }
        switch (distance.trim().toLowerCase(Locale.ROOT)) {
            case "cosine":
                return Distance.Cosine;
            case "euclid":
            case "euclidean":
            case "l2":
                return Distance.Euclid;
            case "dot":
            case "inner_product":
                return Distance.Dot;
            case "manhattan":
                return Distance.Manhattan;
            default:
                throw new NonRetryableException(
                        "Unsupported Qdrant distance: "
                                + distance
                                + ". Use cosine, euclid, dot, or manhattan");
        }
    }

    /** Converts a metadata value from workflow JSON into a Qdrant payload value. */
    static Value toValue(Object object) {
        if (object == null) {
            return nullValue();
        }
        if (object instanceof Value v) {
            return v;
        }
        if (object instanceof String s) {
            return value(s);
        }
        if (object instanceof Boolean b) {
            return value(b);
        }
        if (object instanceof Double || object instanceof Float || object instanceof BigDecimal) {
            return value(((Number) object).doubleValue());
        }
        if (object instanceof BigInteger big) {
            // A payload integer is 64-bit. Keep a larger value exactly, as decimal text, rather
            // than let it wrap around to a wrong number.
            return big.compareTo(LONG_MIN) >= 0 && big.compareTo(LONG_MAX) <= 0
                    ? value(big.longValue())
                    : value(big.toString());
        }
        if (object instanceof Number n) {
            return value(n.longValue());
        }
        if (object instanceof Map<?, ?> map) {
            Map<String, Value> fields = new LinkedHashMap<>();
            map.forEach((k, v) -> fields.put(String.valueOf(k), toValue(v)));
            return value(fields);
        }
        if (object instanceof Collection<?> collection) {
            List<Value> values = new ArrayList<>(collection.size());
            collection.forEach(v -> values.add(toValue(v)));
            return list(values);
        }
        if (object instanceof Object[] array) {
            return toValue(Arrays.asList(array));
        }
        return value(object.toString());
    }

    /** Converts a Qdrant payload value back into plain Java types. */
    static Object fromValue(Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case INTEGER_VALUE:
                return value.getIntegerValue();
            case DOUBLE_VALUE:
                return value.getDoubleValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case STRUCT_VALUE:
                Map<String, Object> map = new LinkedHashMap<>();
                value.getStructValue().getFieldsMap().forEach((k, v) -> map.put(k, fromValue(v)));
                return map;
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                value.getListValue().getValuesList().forEach(v -> list.add(fromValue(v)));
                return list;
            case NULL_VALUE:
            case KIND_NOT_SET:
            default:
                return null;
        }
    }

    private static String stringField(Map<String, Value> payload, String field) {
        Value value = payload.get(field);
        return value != null && value.hasStringValue() ? value.getStringValue() : null;
    }

    /** Returns the gRPC status code in the cause chain, or null if there is none. */
    static Status.Code statusCode(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof StatusRuntimeException sre) {
                return sre.getStatus().getCode();
            }
            if (t instanceof StatusException se) {
                return se.getStatus().getCode();
            }
        }
        return null;
    }

    private static <T> T await(Future<T> future, String operation) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during Qdrant " + operation, e);
        } catch (ExecutionException e) {
            // No log here: the caller decides whether the failure is expected, such as a
            // collection that another worker created first.
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = "Qdrant " + operation + " failed: " + cause.getMessage();
            if (PERMANENT_ERRORS.contains(statusCode(cause))) {
                throw new NonRetryableException(message, cause);
            }
            throw new RuntimeException(message, cause);
        }
    }
}
