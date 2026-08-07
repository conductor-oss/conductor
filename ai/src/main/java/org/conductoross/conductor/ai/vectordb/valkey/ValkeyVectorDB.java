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
package org.conductoross.conductor.ai.vectordb.valkey;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.VectorDB;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import glide.api.GlideClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.*;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.configuration.NodeAddress;
import glide.api.models.configuration.ServerCredentials;
import glide.api.models.exceptions.RequestException;
import lombok.extern.slf4j.Slf4j;

import static glide.api.models.GlideString.gs;

/**
 * Valkey vector database backend using the {@code valkey-search} module (FT.CREATE / FT.SEARCH).
 *
 * <p>Score semantics: the {@code __embedding_score} field returned by valkey-search is a raw
 * distance value (lower is better). For COSINE metric: 0 = identical, 1 = orthogonal. This matches
 * the convention used by {@code PostgresVectorDB} and {@code SqliteVectorDB}.
 *
 * <p>Key schema: {@code <keyPrefix>:<indexName>:<namespace>:<docId>}. The FT.CREATE PREFIX filter
 * is set to {@code <keyPrefix>:<indexName>:<namespace>:} so that HSET keys are automatically
 * indexed.
 *
 * <p>Index isolation: each (indexName, namespace) pair maps to a distinct physical index name via
 * {@link #physicalIndexNameFor(String, String)}. This ensures that documents written under
 * different namespaces are indexed independently, even if they share the same logical index name.
 *
 * <p>This class is standalone-mode only. Cluster mode (GlideClusterClient) is deliberately out of
 * scope for this release.
 */
@Slf4j
public class ValkeyVectorDB extends VectorDB implements Closeable {

    public static final String TYPE = "valkey";

    /** Name validation: letters, digits, underscores, hyphens only. */
    private static final Pattern VALID_NAME = Pattern.compile("[a-zA-Z0-9_-]+");

    /** Score field pattern: __<vectorFieldName>_score. Our field is always named "embedding". */
    static final String SCORE_FIELD = "__embedding_score";

    /** The single vector field name used in the hash and in FT.CREATE schema. */
    static final String EMBEDDING_FIELD = "embedding";

    static final String DOC_FIELD = "doc";
    static final String PARENT_DOC_ID_FIELD = "parent_doc_id";
    static final String METADATA_FIELD = "metadata";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ValkeyConfig config;
    private final GlideClient client;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Normalized key prefix (no trailing colons, validated non-blank). */
    private final String normalizedKeyPrefix;

    /**
     * Tracks which physical indexes have been successfully created in this JVM lifetime. Guards
     * against redundant FT.CREATE calls and the inevitable already-exists race on concurrent
     * first-writes. Keyed by physical index name.
     */
    private final ConcurrentHashMap<String, Boolean> initializedIndexes = new ConcurrentHashMap<>();

    private final long requestTimeoutMs;
    private final DistanceMetric distanceMetric;

    /** Public constructor: validates configuration, then creates the real GLIDE client. */
    public ValkeyVectorDB(String name, ValkeyConfig config) {
        this(name, validateAndReturn(config), null, true);
    }

    /** Package-private constructor for test injection of a pre-built client. */
    ValkeyVectorDB(String name, ValkeyConfig config, GlideClient client) {
        this(name, validateAndReturn(config), Objects.requireNonNull(client), false);
    }

    private ValkeyVectorDB(
            String name, ValkeyConfig config, GlideClient injectedClient, boolean createClient) {
        super(name, TYPE);
        this.config = config;
        this.normalizedKeyPrefix = normalizeKeyPrefix(config.getKeyPrefix());
        this.requestTimeoutMs =
                config.getRequestTimeoutMs() != null ? config.getRequestTimeoutMs() : 2000L;
        this.distanceMetric = ValkeyConfig.resolveDistanceMetric(config.getDistanceMetric());
        this.client = createClient ? buildClient(name, config, requestTimeoutMs) : injectedClient;
    }

    private static ValkeyConfig validateAndReturn(ValkeyConfig config) {
        Objects.requireNonNull(config, "config must not be null");
        validateConfig(config);
        normalizeKeyPrefix(config.getKeyPrefix());
        ValkeyConfig.resolveDistanceMetric(config.getDistanceMetric());
        ValkeyConfig.resolveIndexingMethod(config.getIndexingMethod());
        return config;
    }

    // ----- Client lifecycle -----

    private static final String CLIENT_NAME_PREFIX = "conductor-vectordb-";

    /**
     * Builds the GLIDE client configuration, including a {@code clientName} so this instance's
     * connections are identifiable in {@code CLIENT LIST} on a shared Valkey server.
     * Package-private (and split out from {@link #buildClient}) so it can be unit-tested without a
     * live connection.
     */
    static GlideClientConfiguration buildClientConfiguration(
            String name, ValkeyConfig cfg, long timeoutMs) {
        GlideClientConfiguration.GlideClientConfigurationBuilder<?, ?> builder =
                GlideClientConfiguration.builder()
                        .address(
                                NodeAddress.builder()
                                        .host(cfg.getHost() != null ? cfg.getHost() : "localhost")
                                        .port(cfg.getPort() != null ? cfg.getPort() : 6379)
                                        .build())
                        .requestTimeout(Math.toIntExact(timeoutMs))
                        .clientName(CLIENT_NAME_PREFIX + sanitizeClientName(name));

        if (Boolean.TRUE.equals(cfg.getUseTls())) {
            builder.useTLS(true);
        }

        if (cfg.getPassword() != null || cfg.getUsername() != null) {
            ServerCredentials.ServerCredentialsBuilder credBuilder = ServerCredentials.builder();
            if (cfg.getPassword() != null) {
                credBuilder.password(cfg.getPassword());
            }
            if (cfg.getUsername() != null) {
                credBuilder.username(cfg.getUsername());
            }
            builder.credentials(credBuilder.build());
        }

        if (cfg.getDatabase() != null && cfg.getDatabase() != 0) {
            builder.databaseId(cfg.getDatabase());
        }

        return builder.build();
    }

    /**
     * CLIENT SETNAME rejects names containing spaces or newlines. The configured instance name is
     * an operator-provided config key with no character restrictions at that layer, so it is
     * sanitized here rather than letting an otherwise-valid config fail client creation.
     */
    private static String sanitizeClientName(String name) {
        return name.replaceAll("\\s+", "_");
    }

    private static GlideClient buildClient(String name, ValkeyConfig cfg, long timeoutMs) {
        try {
            return awaitFuture(
                    GlideClient.createClient(buildClientConfiguration(name, cfg, timeoutMs)),
                    timeoutMs,
                    "GLIDE client creation");
        } catch (RuntimeException e) {
            throw new RuntimeException(
                    "Failed to create Valkey GLIDE client for "
                            + cfg.getHost()
                            + ":"
                            + cfg.getPort(),
                    e);
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                client.close();
            } catch (ExecutionException e) {
                log.warn(
                        "Error closing GLIDE client for ValkeyVectorDB '{}': {}",
                        name,
                        e.getMessage());
            } catch (Exception e) {
                log.warn(
                        "Error closing GLIDE client for ValkeyVectorDB '{}': {}",
                        name,
                        e.getMessage());
            }
        }
    }

    // ----- Public API -----

    @Override
    public int updateEmbeddings(
            String indexName,
            String namespace,
            String doc,
            String parentDocId,
            String id,
            List<Float> embeddings,
            Map<String, Object> metadata) {
        ensureOpen();
        validateName("indexName", indexName);
        validateName("namespace", namespace);
        Objects.requireNonNull(doc, "doc must not be null");
        validateId(id);
        validateEmbeddings(embeddings);

        if (parentDocId == null) {
            parentDocId = id;
        }

        byte[] embeddingBytes = encodeEmbedding(embeddings);
        String metadataJson = serializeMetadata(metadata);
        ensureIndexCreated(indexName, namespace);

        String key = keyFor(indexName, namespace, id);
        Map<GlideString, GlideString> fields = new LinkedHashMap<>();
        fields.put(gs(EMBEDDING_FIELD), gs(embeddingBytes));
        fields.put(gs(DOC_FIELD), gs(doc));
        fields.put(gs(PARENT_DOC_ID_FIELD), gs(parentDocId));
        fields.put(gs(METADATA_FIELD), gs(metadataJson));

        await(client.hset(gs(key), fields));
        return 1;
    }

    @Override
    public List<IndexedDoc> search(
            String indexName, String namespace, List<Float> embeddings, int maxResults) {
        ensureOpen();
        validateName("indexName", indexName);
        validateName("namespace", namespace);
        validateEmbeddings(embeddings);
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be > 0, got: " + maxResults);
        }

        ensureIndexCreated(indexName, namespace);

        byte[] queryBytes = encodeEmbedding(embeddings);

        // Use the physical index name for FT.SEARCH (must match FT.CREATE)
        String physicalIndex = physicalIndexNameFor(indexName, namespace);

        // KNN query: *=>[KNN <k> @embedding $query_vec]
        String query = "*=>[KNN " + maxResults + " @" + EMBEDDING_FIELD + " $query_vec]";

        FTSearchOptions searchOptions =
                FTSearchOptions.builder()
                        .params(Map.of(gs("query_vec"), gs(queryBytes)))
                        .addReturnField(DOC_FIELD)
                        .addReturnField(PARENT_DOC_ID_FIELD)
                        .addReturnField(METADATA_FIELD)
                        .addReturnField(SCORE_FIELD)
                        .limit(0, maxResults)
                        .dialect(2)
                        .build();

        Object[] result = await(FT.search(client, physicalIndex, query, searchOptions));
        return parseSearchResults(result, indexName, namespace);
    }

    // ----- Async->Sync bridge: one helper, used everywhere -----

    /** Resolves every GLIDE future through one timeout- and interrupt-safe implementation. */
    <T> T await(CompletableFuture<T> future) {
        return awaitFuture(future, requestTimeoutMs, "Valkey operation");
    }

    private static <T> T awaitFuture(
            CompletableFuture<T> future, long timeoutMs, String operation) {
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(operation + " interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new RuntimeException(operation + " failed", cause != null ? cause : e);
        } catch (TimeoutException e) {
            throw new RuntimeException(operation + " timed out after " + timeoutMs + "ms", e);
        }
    }

    // ----- Namespace/index isolation: physical index name derivation -----

    /**
     * Derives a collision-safe physical index name for FT.CREATE and FT.SEARCH. Format: {@code
     * <normalizedKeyPrefix>:<indexName>:<namespace>}. This ensures two namespaces sharing the same
     * logical indexName map to distinct physical FT indexes with distinct prefixes.
     *
     * @param indexName logical index name (validated)
     * @param namespace logical namespace (validated)
     * @return physical index name safe for Valkey FT commands
     */
    String physicalIndexNameFor(String indexName, String namespace) {
        return normalizedKeyPrefix + ":" + indexName + ":" + namespace;
    }

    // ----- Index creation with concurrency guard -----

    private void ensureIndexCreated(String indexName, String namespace) {
        String physicalIndex = physicalIndexNameFor(indexName, namespace);
        if (initializedIndexes.containsKey(physicalIndex)) {
            return;
        }

        // If index creation throws, computeIfAbsent does not install a cache entry.
        initializedIndexes.computeIfAbsent(
                physicalIndex,
                k -> {
                    createIndex(indexName, namespace, physicalIndex);
                    return Boolean.TRUE;
                });
    }

    private void createIndex(String indexName, String namespace, String physicalIndex) {
        String prefix = keyPrefixFor(indexName, namespace);

        FieldInfo[] schema = buildSchema();

        FTCreateOptions createOptions =
                FTCreateOptions.builder()
                        .dataType(DataType.HASH)
                        .prefixes(new String[] {prefix})
                        .build();

        try {
            await(FT.create(client, physicalIndex, schema, createOptions));
            log.info(
                    "Created Valkey search index '{}' with prefix '{}' (metric={}, dims={})",
                    physicalIndex,
                    prefix,
                    config.getDistanceMetric(),
                    config.getDimensions());
        } catch (RuntimeException e) {
            if (isAlreadyExistsError(e)) {
                log.debug("Index '{}' already exists, continuing", physicalIndex);
            } else if (isUnknownCommandError(e)) {
                throw new RuntimeException(
                        "The Valkey server does not have the search module loaded. "
                                + "Install valkey-search or use the valkey/valkey-bundle container image.",
                        e);
            } else {
                // A failed computation is not cached, so the next operation can retry.
                throw e;
            }
        }
    }

    private FieldInfo[] buildSchema() {
        String method = ValkeyConfig.resolveIndexingMethod(config.getIndexingMethod());
        int dims = config.getDimensions() != null ? config.getDimensions() : 256;

        Field vectorField;
        if ("flat".equals(method)) {
            vectorField = VectorFieldFlat.builder(distanceMetric, dims).build();
        } else {
            vectorField = VectorFieldHnsw.builder(distanceMetric, dims).build();
        }

        return new FieldInfo[] {new FieldInfo(EMBEDDING_FIELD, vectorField)};
    }

    // ----- Embedding encoding: one shared FLOAT32 little-endian encoder -----

    /**
     * Encodes a list of floats as raw little-endian FLOAT32 bytes. This is the only encoder -- both
     * stored vectors and query vectors pass through here. Using a different encoding for either
     * side causes documents to be silently excluded from vector search results.
     */
    static byte[] encodeEmbedding(List<Float> embeddings) {
        ByteBuffer buffer =
                ByteBuffer.allocate(4 * embeddings.size()).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : embeddings) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    // ----- Key schema: one method, used by HSET and FT.CREATE prefix -----

    /**
     * Returns the prefix string for FT.CREATE. The prefix includes the trailing colon so that the
     * HSET key {@code <prefix><docId>} is matched by the index.
     */
    String keyPrefixFor(String indexName, String namespace) {
        return normalizedKeyPrefix + ":" + indexName + ":" + namespace + ":";
    }

    private String keyFor(String indexName, String namespace, String docId) {
        return keyPrefixFor(indexName, namespace) + docId;
    }

    // ----- Search result parsing -----

    List<IndexedDoc> parseSearchResults(Object[] result, String indexName, String namespace) {
        if (result == null || result.length != 2) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: expected exactly 2 elements, got "
                            + (result == null ? "null" : result.length));
        }
        if (!(result[0] instanceof Long)) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: expected Long at result[0], got "
                            + (result[0] != null ? result[0].getClass().getName() : "null"));
        }
        if (!(result[1] instanceof Map<?, ?>)) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: expected Map at result[1], got "
                            + (result[1] != null ? result[1].getClass().getName() : "null"));
        }

        long declaredCount = (Long) result[0];
        Map<?, ?> rawDocMap = (Map<?, ?>) result[1];
        if (declaredCount < 0 || rawDocMap.size() > declaredCount) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: declared count "
                            + declaredCount
                            + " is smaller than returned document count "
                            + rawDocMap.size());
        }

        String prefix = keyPrefixFor(indexName, namespace);
        List<IndexedDoc> docs = new ArrayList<>(rawDocMap.size());
        for (Map.Entry<?, ?> entry : rawDocMap.entrySet()) {
            if (!(entry.getKey() instanceof GlideString)) {
                throw new IllegalStateException(
                        "Malformed FT.SEARCH result: document key is not a GlideString");
            }
            String fullKey = entry.getKey().toString();
            if (!fullKey.startsWith(prefix) || fullKey.length() == prefix.length()) {
                throw new IllegalStateException(
                        "Malformed FT.SEARCH result: key '"
                                + fullKey
                                + "' does not match expected prefix '"
                                + prefix
                                + "'");
            }

            Map<GlideString, GlideString> fields = requireFieldMap(entry.getValue(), fullKey);
            String docId = fullKey.substring(prefix.length());
            String docText = requireFieldValue(fields, DOC_FIELD, docId);
            String parentDocId = requireFieldValue(fields, PARENT_DOC_ID_FIELD, docId);
            String scoreStr = requireFieldValue(fields, SCORE_FIELD, docId);
            String metadataStr = requireFieldValue(fields, METADATA_FIELD, docId);

            IndexedDoc indexedDoc =
                    new IndexedDoc(docId, parentDocId, docText, parseScore(scoreStr, docId));
            indexedDoc.setMetadata(deserializeMetadata(metadataStr, docId));
            docs.add(indexedDoc);
        }
        return docs;
    }

    private Map<GlideString, GlideString> requireFieldMap(Object rawFields, String fullKey) {
        if (!(rawFields instanceof Map<?, ?>)) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: fields for '" + fullKey + "' are not a Map");
        }
        Map<GlideString, GlideString> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> field : ((Map<?, ?>) rawFields).entrySet()) {
            if (!(field.getKey() instanceof GlideString)
                    || !(field.getValue() instanceof GlideString)) {
                throw new IllegalStateException(
                        "Malformed FT.SEARCH result: field key/value for '"
                                + fullKey
                                + "' must be GlideString");
            }
            fields.put((GlideString) field.getKey(), (GlideString) field.getValue());
        }
        return fields;
    }

    private String requireFieldValue(
            Map<GlideString, GlideString> fields, String fieldName, String docId) {
        GlideString value = fields.get(gs(fieldName));
        if (value == null) {
            throw new IllegalStateException(
                    "Malformed FT.SEARCH result: missing field '"
                            + fieldName
                            + "' for document '"
                            + docId
                            + "'");
        }
        return value.toString();
    }

    /** Parses a finite distance score or throws on malformed input. */
    static double parseScore(String scoreStr, String docId) {
        if (scoreStr == null || scoreStr.isEmpty()) {
            throw new IllegalStateException(
                    "Missing score field for document '" + docId + "': score was null or empty");
        }
        try {
            double score = Double.parseDouble(scoreStr);
            if (!Double.isFinite(score)) {
                throw new IllegalStateException(
                        "Non-finite score '" + scoreStr + "' for document '" + docId + "'");
            }
            return score;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Malformed score '" + scoreStr + "' for document '" + docId + "'", e);
        }
    }

    // ----- Metadata serialization -----

    /**
     * Serializes metadata to JSON. Throws on serialization failure instead of silently writing
     * empty JSON.
     */
    static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize metadata to JSON", e);
        }
    }

    /**
     * Deserializes metadata JSON. Throws on parse failure instead of silently returning empty map.
     */
    static Map<String, Object> deserializeMetadata(String metadataStr, String docId) {
        try {
            return OBJECT_MAPPER.readValue(
                    metadataStr, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse metadata for document '" + docId + "': " + e.getMessage(), e);
        }
    }

    // ----- Validation helpers -----

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(
                    "ValkeyVectorDB '" + name + "' has been closed and cannot perform operations");
        }
    }

    private void validateName(String paramName, String value) {
        if (value == null || !VALID_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + paramName + ": '" + value + "'. Must match [a-zA-Z0-9_-]+");
        }
    }

    /**
     * Validates the document id before it is concatenated into the Valkey key. Null still throws
     * {@link NullPointerException} (unchanged contract, matches sibling {@code doc} parameter); a
     * non-null id must match the same allowlist as {@code indexName}/{@code namespace} so that a
     * colon or other delimiter cannot produce an ambiguous or unexpectedly-scoped key.
     */
    private void validateId(String id) {
        Objects.requireNonNull(id, "id must not be null");
        if (!VALID_NAME.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Invalid id: '" + id + "'. Must match [a-zA-Z0-9_-]+");
        }
    }

    /**
     * Validates embedding vector: non-null, correct dimension count, each element non-null and
     * finite.
     */
    private void validateEmbeddings(List<Float> embeddings) {
        int expected = config.getDimensions() != null ? config.getDimensions() : 256;
        if (embeddings == null || embeddings.size() != expected) {
            throw new IllegalArgumentException(
                    "Embeddings must be of dimensions: "
                            + expected
                            + " but got "
                            + (embeddings != null ? embeddings.size() : 0));
        }
        for (int i = 0; i < embeddings.size(); i++) {
            Float elem = embeddings.get(i);
            if (elem == null) {
                throw new IllegalArgumentException(
                        "Embedding element at index " + i + " must not be null");
            }
            if (!Float.isFinite(elem)) {
                throw new IllegalArgumentException(
                        "Embedding element at index " + i + " must be finite, got: " + elem);
            }
        }
    }

    /** Validates configuration constraints at construction time. */
    private static void validateConfig(ValkeyConfig config) {
        if (config.getHost() == null || config.getHost().isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (config.getPort() != null && (config.getPort() <= 0 || config.getPort() > 65_535)) {
            throw new IllegalArgumentException(
                    "port must be between 1 and 65535, got: " + config.getPort());
        }
        if (config.getDimensions() != null && config.getDimensions() <= 0) {
            throw new IllegalArgumentException(
                    "dimensions must be positive, got: " + config.getDimensions());
        }
        if (config.getRequestTimeoutMs() != null && config.getRequestTimeoutMs() <= 0) {
            throw new IllegalArgumentException(
                    "requestTimeoutMs must be positive, got: " + config.getRequestTimeoutMs());
        }
        if (config.getDatabase() != null && config.getDatabase() < 0) {
            throw new IllegalArgumentException(
                    "database must be >= 0, got: " + config.getDatabase());
        }
    }

    /**
     * Normalizes the key prefix: strips trailing colons, validates non-blank and safe characters.
     */
    static String normalizeKeyPrefix(String keyPrefix) {
        String prefix = keyPrefix != null ? keyPrefix : "conductor";
        // Strip trailing colons
        while (prefix.endsWith(":")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("keyPrefix must not be blank after normalization");
        }
        // Validate safe characters: alphanumeric, underscore, hyphen, dots
        if (!Pattern.matches("[a-zA-Z0-9_.\\-]+", prefix)) {
            throw new IllegalArgumentException(
                    "keyPrefix contains unsafe characters: '"
                            + prefix
                            + "'. Must match [a-zA-Z0-9_.\\-]+");
        }
        return prefix;
    }

    // ----- Error classification -----

    /**
     * Classifies whether an exception represents an "index already exists" error from Valkey.
     * Requires the cause chain to contain a GLIDE {@link RequestException} specifically. Anchored
     * to the verified message shape ({@code "Index: <name> in database <db> already exists."}) via
     * a suffix check, rather than a free-floating substring match, so an unrelated message that
     * happens to mention "already exists." elsewhere is not misclassified.
     */
    static boolean isAlreadyExistsError(RuntimeException e) {
        RequestException reqEx = findRequestException(e);
        if (reqEx == null) {
            return false;
        }
        String msg = reqEx.getMessage();
        return msg != null && msg.endsWith(" already exists.");
    }

    /**
     * Classifies whether an exception represents an "unknown command" error (search module not
     * loaded). Requires the cause chain to contain a GLIDE {@link RequestException} specifically --
     * unlike the prior implementation, this does not fall back to matching non-RequestException
     * messages, so errors from other exception types are never suppressed here.
     */
    static boolean isUnknownCommandError(RuntimeException e) {
        RequestException reqEx = findRequestException(e);
        if (reqEx == null) {
            return false;
        }
        String msg = reqEx.getMessage();
        return msg != null && msg.startsWith("ERR unknown command");
    }

    /**
     * Walks the cause chain to find a GLIDE {@link RequestException}. Returns null if not found.
     */
    private static RequestException findRequestException(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof RequestException) {
                return (RequestException) current;
            }
            current = current.getCause();
        }
        return null;
    }
}
