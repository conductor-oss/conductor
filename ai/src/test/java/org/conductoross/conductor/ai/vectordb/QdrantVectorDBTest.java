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
package org.conductoross.conductor.ai.vectordb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.LLMs;
import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.model.StoreEmbeddingsInput;
import org.conductoross.conductor.ai.model.VectorDBInput;
import org.conductoross.conductor.ai.tasks.worker.VectorDBWorkers;
import org.conductoross.conductor.ai.vectordb.qdrant.QdrantConfig;
import org.conductoross.conductor.ai.vectordb.qdrant.QdrantVectorDB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.testcontainers.qdrant.QdrantContainer;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CollectionInfo;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaInfo;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Runs the Qdrant vector DB against a real Qdrant server through the Conductor task workers. */
public class QdrantVectorDBTest {

    private static final String INSTANCE = "qdrant-test";

    private static QdrantContainer qdrantContainer;
    private static QdrantClient rawClient;
    private static QdrantConfig qdrantConfig;
    private static VectorDBWorkers workers;
    private static VectorDBProvider vectorDBProvider;

    private String collection;

    @BeforeAll
    public static void setup() {
        qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.20.0");
        qdrantContainer.start();

        rawClient =
                new QdrantClient(
                        QdrantGrpcClient.newBuilder(
                                        qdrantContainer.getHost(),
                                        qdrantContainer.getMappedPort(6334),
                                        false)
                                .build());

        qdrantConfig = containerConfig();

        // Build the instance through the same configuration path the server uses.
        VectorDBInstanceConfig.VectorDBInstance instance =
                new VectorDBInstanceConfig.VectorDBInstance();
        instance.setName(INSTANCE);
        instance.setType("qdrant");
        instance.setQdrant(qdrantConfig);
        VectorDBInstanceConfig instanceConfig = new VectorDBInstanceConfig();
        instanceConfig.setInstances(List.of(instance));

        @SuppressWarnings("unchecked")
        ObjectProvider<VectorDB> noDefaults = mock(ObjectProvider.class);
        vectorDBProvider = new VectorDBProvider(instanceConfig, noDefaults);

        AIModelProvider provider = new AIModelProvider(List.of(), new StandardEnvironment());
        // No schema service: this test never attaches a schema to an LLM call.
        LLMs llm = new LLMs(null, null, provider, new okhttp3.OkHttpClient());
        workers = new VectorDBWorkers(new VectorDBs(vectorDBProvider), llm);
    }

    @AfterAll
    public static void teardown() {
        if (rawClient != null) {
            rawClient.close();
        }
        if (qdrantContainer != null) {
            qdrantContainer.stop();
        }
    }

    @BeforeEach
    public void setupTask() {
        // Each test writes to its own collection, so tests do not see each other's points.
        collection = "docs_" + UUID.randomUUID().toString().replace("-", "");
        Task task = new Task();
        task.setTaskId(UUID.randomUUID().toString());
        TaskContext.TASK_CONTEXT_INHERITABLE_THREAD_LOCAL.set(
                new TaskContext(task, new TaskResult()));
    }

    @Test
    public void testConfigTypeCreatesQdrantInstance() {
        VectorDB db = vectorDBProvider.get(INSTANCE, TaskContext.get());
        assertInstanceOf(QdrantVectorDB.class, db);
        assertEquals("qdrant", db.getType());

        VectorDBInstanceConfig.VectorDBInstance alias =
                new VectorDBInstanceConfig.VectorDBInstance();
        alias.setName("qdrant-alias");
        alias.setType("qdrantdb");
        alias.setQdrant(qdrantConfig);
        VectorDBInstanceConfig config = new VectorDBInstanceConfig();
        config.setInstances(List.of(alias));
        assertInstanceOf(QdrantVectorDB.class, config.getVectorDBInstances().get("qdrant-alias"));
    }

    @Test
    public void testStoreCreatesCollectionWithTenantIndex() throws Exception {
        int stored = workers.storeEmbeddings(storeInput("doc-1", "items", List.of(1f, 0f, 0f)));
        assertEquals(1, stored);

        CollectionInfo info = rawClient.getCollectionInfoAsync(collection).get();
        assertEquals(3, info.getConfig().getParams().getVectorsConfig().getParams().getSize());
        assertEquals(
                Distance.Cosine,
                info.getConfig().getParams().getVectorsConfig().getParams().getDistance());

        PayloadSchemaInfo namespaceIndex = info.getPayloadSchemaMap().get("namespace");
        assertEquals(PayloadSchemaType.Keyword, namespaceIndex.getDataType());
        assertTrue(namespaceIndex.getParams().getKeywordIndexParams().getIsTenant());
    }

    @Test
    public void testStoreAndSearchRoundTrip() {
        Map<String, Object> metadata =
                Map.of(
                        "source",
                        "handbook",
                        "page",
                        7,
                        "rating",
                        4.5,
                        "published",
                        true,
                        "tags",
                        List.of("hr", "policy"),
                        "author",
                        Map.of("name", "Ada"));
        StoreEmbeddingsInput input = storeInput("handbook-p7", "items", List.of(1f, 0f, 0f));
        input.setMetadata(metadata);
        workers.storeEmbeddings(input);
        workers.storeEmbeddings(storeInput("other", "items", List.of(0f, 1f, 0f)));

        List<IndexedDoc> results = workers.searchUsingEmbeddings(searchInput("items", 2));

        assertEquals(2, results.size());
        IndexedDoc top = results.get(0);
        assertEquals("handbook-p7", top.getDocId());
        assertEquals(1.0, top.getScore(), 1e-5);
        assertEquals("handbook", top.getMetadata().get("source"));
        assertEquals(7L, top.getMetadata().get("page"));
        assertEquals(4.5, top.getMetadata().get("rating"));
        assertEquals(true, top.getMetadata().get("published"));
        assertEquals(List.of("hr", "policy"), top.getMetadata().get("tags"));
        assertEquals(Map.of("name", "Ada"), top.getMetadata().get("author"));
        assertEquals("other", results.get(1).getDocId());
    }

    @Test
    public void testTextAndParentDocIdAreReturned() {
        VectorDB db = vectorDBProvider.get(INSTANCE, TaskContext.get());
        db.updateEmbeddings(
                collection,
                "items",
                "Conductor runs durable workflows.",
                "handbook",
                "handbook#chunk-3",
                List.of(1f, 0f, 0f),
                Map.of());

        IndexedDoc doc = db.search(collection, "items", List.of(1f, 0f, 0f), 1).get(0);
        assertEquals("handbook#chunk-3", doc.getDocId());
        assertEquals("handbook", doc.getParentDocId());
        assertEquals("Conductor runs durable workflows.", doc.getText());
    }

    @Test
    public void testNamespacesAreIsolated() throws Exception {
        workers.storeEmbeddings(storeInput("shared-id", "tenant-a", List.of(1f, 0f, 0f)));
        workers.storeEmbeddings(storeInput("shared-id", "tenant-b", List.of(1f, 0f, 0f)));
        workers.storeEmbeddings(storeInput("b-only", "tenant-b", List.of(0f, 1f, 0f)));

        // The same document id in two namespaces is two points, not one.
        assertEquals(3L, rawClient.countAsync(collection).get());

        List<IndexedDoc> tenantA = workers.searchUsingEmbeddings(searchInput("tenant-a", 10));
        assertEquals(1, tenantA.size());
        assertEquals("shared-id", tenantA.get(0).getDocId());

        List<IndexedDoc> tenantB = workers.searchUsingEmbeddings(searchInput("tenant-b", 10));
        assertEquals(2, tenantB.size());
    }

    @Test
    public void testStoringSameIdReplacesPoint() throws Exception {
        StoreEmbeddingsInput first = storeInput("doc-1", "items", List.of(1f, 0f, 0f));
        first.setMetadata(Map.of("version", 1));
        workers.storeEmbeddings(first);
        StoreEmbeddingsInput second = storeInput("doc-1", "items", List.of(0f, 1f, 0f));
        second.setMetadata(Map.of("version", 2));
        workers.storeEmbeddings(second);

        assertEquals(1L, rawClient.countAsync(collection).get());
        IndexedDoc doc = workers.searchUsingEmbeddings(searchInput("items", 1)).get(0);
        assertEquals(2L, doc.getMetadata().get("version"));
    }

    @Test
    public void testConcurrentFirstWritesCreateCollectionOnce() throws Exception {
        // Fresh instances have no cached collection state, so every thread races to create the
        // collection. Several rounds make the creation window more likely to be hit.
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int round = 0; round < 3; round++) {
                String roundCollection = collection + "_" + round;
                List<Callable<Integer>> writes = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    String id = "doc-" + i;
                    writes.add(
                            () ->
                                    qdrantConfig
                                            .get("qdrant-race")
                                            .updateEmbeddings(
                                                    roundCollection,
                                                    "items",
                                                    "text",
                                                    null,
                                                    id,
                                                    List.of(1f, 0f, 0f),
                                                    Map.of()));
                }
                for (Future<Integer> result : pool.invokeAll(writes)) {
                    assertEquals(1, result.get());
                }
                assertEquals((long) threads, rawClient.countAsync(roundCollection).get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testSearchMissingCollectionReturnsNoResults() {
        assertTrue(workers.searchUsingEmbeddings(searchInput("items", 5)).isEmpty());
    }

    @Test
    public void testBlankNamespacesAreOneNamespace() throws Exception {
        for (String namespace : java.util.Arrays.asList(null, "", "   ")) {
            workers.storeEmbeddings(storeInput("w1", namespace, List.of(1f, 0f, 0f)));
        }
        assertEquals(1L, rawClient.countAsync(collection).get());
        assertEquals(1, workers.searchUsingEmbeddings(searchInput("  ", 10)).size());
    }

    @Test
    public void testIntegersBeyond64BitsAreKeptExactly() {
        StoreEmbeddingsInput input = storeInput("big", "items", List.of(1f, 0f, 0f));
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("beyond", new java.math.BigInteger("123456789012345678901234567890"));
        metadata.put("max", new java.math.BigInteger(String.valueOf(Long.MAX_VALUE)));
        metadata.put("min", Long.MIN_VALUE);
        input.setMetadata(metadata);
        workers.storeEmbeddings(input);

        Map<String, Object> got =
                workers.searchUsingEmbeddings(searchInput("items", 1)).get(0).getMetadata();
        assertEquals("123456789012345678901234567890", got.get("beyond"));
        assertEquals(Long.MAX_VALUE, got.get("max"));
        assertEquals(Long.MIN_VALUE, got.get("min"));
    }

    @Test
    public void testMaxResultsBelowOneFailsTerminally() {
        workers.storeEmbeddings(storeInput("d", "items", List.of(1f, 0f, 0f)));
        for (int maxResults : new int[] {0, -1}) {
            NonRetryableException e =
                    assertThrows(
                            NonRetryableException.class,
                            () -> workers.searchUsingEmbeddings(searchInput("items", maxResults)));
            assertTrue(e.getMessage().contains("maxResults"), e.getMessage());
        }
    }

    @Test
    public void testInvalidInputFailsTerminally() {
        assertThrows(
                NonRetryableException.class,
                () -> workers.storeEmbeddings(storeInput("d", "items", List.of())));
        assertThrows(
                NonRetryableException.class,
                () ->
                        workers.storeEmbeddings(
                                storeInput("d", "items", java.util.Arrays.asList(1f, null))));
        assertThrows(
                NonRetryableException.class,
                () -> workers.storeEmbeddings(storeInput("d", "items", List.of(Float.NaN))));
        StoreEmbeddingsInput noIndex = storeInput("d", "items", List.of(1f, 0f, 0f));
        noIndex.setIndex(" ");
        assertThrows(NonRetryableException.class, () -> workers.storeEmbeddings(noIndex));
    }

    @Test
    public void testDimensionMismatchFailsTerminally() throws Exception {
        workers.storeEmbeddings(storeInput("d", "items", List.of(1f, 0f, 0f)));
        NonRetryableException e =
                assertThrows(
                        NonRetryableException.class,
                        () -> workers.storeEmbeddings(storeInput("d2", "items", List.of(1f, 0f))));
        assertTrue(e.getMessage().contains("dimension"), e.getMessage());
        assertEquals(1L, rawClient.countAsync(collection).get());
    }

    @Test
    public void testDeletedCollectionIsCreatedAgain() throws Exception {
        workers.storeEmbeddings(storeInput("d1", "items", List.of(1f, 0f, 0f)));
        rawClient.deleteCollectionAsync(collection).get();

        workers.storeEmbeddings(storeInput("d2", "items", List.of(0f, 1f, 0f)));

        assertEquals(1L, rawClient.countAsync(collection).get());
        CollectionInfo info = rawClient.getCollectionInfoAsync(collection).get();
        assertTrue(
                info.getPayloadSchemaMap()
                        .get("namespace")
                        .getParams()
                        .getKeywordIndexParams()
                        .getIsTenant());
    }

    @Test
    public void testExistingCollectionGetsNamespaceIndex() throws Exception {
        rawClient
                .createCollectionAsync(
                        collection,
                        io.qdrant.client.grpc.Collections.VectorParams.newBuilder()
                                .setSize(3)
                                .setDistance(Distance.Dot)
                                .build())
                .get();

        workers.storeEmbeddings(storeInput("d", "items", List.of(1f, 0f, 0f)));

        CollectionInfo info = rawClient.getCollectionInfoAsync(collection).get();
        assertEquals(
                Distance.Dot,
                info.getConfig().getParams().getVectorsConfig().getParams().getDistance());
        assertTrue(
                info.getPayloadSchemaMap()
                        .get("namespace")
                        .getParams()
                        .getKeywordIndexParams()
                        .getIsTenant());
        assertEquals(1, workers.searchUsingEmbeddings(searchInput("items", 5)).size());
    }

    @Test
    public void testUnreachableQdrantFailsRetryably() {
        QdrantConfig config = new QdrantConfig();
        config.setHost("localhost");
        config.setPort(1);
        config.setTimeout(java.time.Duration.ofSeconds(5));
        RuntimeException e =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                config.get("unreachable")
                                        .search(collection, "items", List.of(1f, 0f, 0f), 1));
        assertFalse(e instanceof NonRetryableException, e.getMessage());
        assertTrue(e.getMessage().contains("UNAVAILABLE"), e.getMessage());
    }

    @Test
    public void testUnresponsiveQdrantTimesOut() throws Exception {
        // A listener that accepts connections and never answers.
        try (java.net.ServerSocket silent = new java.net.ServerSocket(0)) {
            QdrantConfig config = new QdrantConfig();
            config.setHost("127.0.0.1");
            config.setPort(silent.getLocalPort());
            config.setTimeout(java.time.Duration.ofSeconds(2));
            QdrantVectorDB db = config.get("unresponsive");
            long start = System.nanoTime();
            RuntimeException e =
                    assertThrows(
                            RuntimeException.class,
                            () ->
                                    db.updateEmbeddings(
                                            collection,
                                            "items",
                                            "",
                                            null,
                                            "d",
                                            List.of(1f, 0f, 0f),
                                            Map.of()));
            long seconds =
                    java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            assertFalse(e instanceof NonRetryableException, e.getMessage());
            assertTrue(e.getMessage().contains("DEADLINE_EXCEEDED"), e.getMessage());
            assertTrue(seconds < 15, "took " + seconds + "s");
        }
    }

    @Test
    public void testDocumentWithoutMetadataHasEmptyMetadata() {
        StoreEmbeddingsInput input = storeInput("bare", "items", List.of(1f, 0f, 0f));
        input.setMetadata(null);
        workers.storeEmbeddings(input);

        IndexedDoc doc = workers.searchUsingEmbeddings(searchInput("items", 1)).get(0);
        assertTrue(doc.getMetadata().isEmpty());
        assertEquals("", doc.getText());
    }

    @Test
    public void testConfiguredDistanceIsUsed() throws Exception {
        QdrantConfig config = containerConfig();
        // Postgres-style names map to the matching Qdrant distance.
        config.setDistance("inner_product");
        config.get("dot-instance")
                .updateEmbeddings(
                        collection, "items", "", null, "doc", List.of(1f, 0f, 0f), Map.of());

        CollectionInfo info = rawClient.getCollectionInfoAsync(collection).get();
        assertEquals(
                Distance.Dot,
                info.getConfig().getParams().getVectorsConfig().getParams().getDistance());
    }

    @Test
    public void testUnsupportedDistanceFails() {
        QdrantConfig config = containerConfig();
        config.setDistance("hamming");
        QdrantVectorDB db = config.get("bad-distance");
        NonRetryableException e =
                assertThrows(
                        NonRetryableException.class,
                        () ->
                                db.updateEmbeddings(
                                        collection,
                                        "items",
                                        "",
                                        null,
                                        "doc",
                                        List.of(1f, 0f, 0f),
                                        Map.of()));
        assertTrue(e.getMessage().contains("Unsupported Qdrant distance"));
    }

    @Test
    public void testSpringPropertiesBindToQdrantConfig() {
        Map<String, String> properties =
                Map.of(
                        "conductor.vectordb.instances[0].name", "qdrant-cloud",
                        "conductor.vectordb.instances[0].type", "qdrant",
                        "conductor.vectordb.instances[0].qdrant.host", "example.cloud.qdrant.io",
                        "conductor.vectordb.instances[0].qdrant.port", "6334",
                        "conductor.vectordb.instances[0].qdrant.use-tls", "true",
                        "conductor.vectordb.instances[0].qdrant.api-key", "secret",
                        "conductor.vectordb.instances[0].qdrant.distance", "dot",
                        "conductor.vectordb.instances[0].qdrant.timeout", "5s");
        VectorDBInstanceConfig bound =
                new Binder(new MapConfigurationPropertySource(properties))
                        .bind("conductor.vectordb", VectorDBInstanceConfig.class)
                        .get();

        QdrantConfig config = bound.getInstances().get(0).getQdrant();
        assertEquals("example.cloud.qdrant.io", config.getHost());
        assertEquals(6334, config.getPort());
        assertEquals(true, config.getUseTls());
        assertEquals("secret", config.getApiKey());
        assertEquals("dot", config.getDistance());
        assertEquals(java.time.Duration.ofSeconds(5), config.getTimeout());
    }

    @Test
    public void testConfigDefaults() {
        QdrantConfig config = new QdrantConfig();
        assertEquals("localhost", config.getHost());
        assertEquals(6334, config.getPort());
        assertEquals(false, config.getUseTls());
        assertEquals("cosine", config.getDistance());
        assertNull(config.getApiKey());
        assertEquals(java.time.Duration.ofSeconds(30), config.getTimeout());
    }

    private static QdrantConfig containerConfig() {
        QdrantConfig config = new QdrantConfig();
        config.setHost(qdrantContainer.getHost());
        config.setPort(qdrantContainer.getMappedPort(6334));
        return config;
    }

    private StoreEmbeddingsInput storeInput(String id, String namespace, List<Float> vector) {
        StoreEmbeddingsInput input = new StoreEmbeddingsInput();
        input.setVectorDB(INSTANCE);
        input.setIndex(collection);
        input.setNamespace(namespace);
        input.setId(id);
        input.setEmbeddings(vector);
        input.setMetadata(Map.of("key1", "val1"));
        return input;
    }

    private VectorDBInput searchInput(String namespace, int maxResults) {
        VectorDBInput input = new VectorDBInput();
        input.setVectorDB(INSTANCE);
        input.setIndex(collection);
        input.setNamespace(namespace);
        input.setEmbeddings(List.of(1f, 0f, 0f));
        input.setMaxResults(maxResults);
        return input;
    }
}
