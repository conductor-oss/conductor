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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.valkey.ValkeyConfig;
import org.conductoross.conductor.ai.vectordb.valkey.ValkeyVectorDB;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end round trip for the Valkey vector store against a real {@code valkey-search} server
 * started by Testcontainers.
 *
 * <p>Requires a Docker-API-compatible container runtime to be reachable. Docker Desktop works with
 * no configuration. Other runtimes (Rancher Desktop, Finch with {@code dockercompat: true}, Podman)
 * require {@code DOCKER_HOST} to point at their socket; that is an environment concern and is
 * deliberately not encoded in this test or in the build.
 *
 * <p>Nothing here is hardcoded to a host or port: the host and mapped port are read back from the
 * container, so the test runs unchanged alongside an existing Valkey on the default port.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ValkeyVectorDBRoundTripTest {

    /**
     * Pinned so the test is reproducible. {@code valkey-bundle} ships the {@code valkey-search}
     * module; the plain {@code valkey} image does not and would fail with "unknown command".
     */
    private static final DockerImageName VALKEY_BUNDLE =
            DockerImageName.parse("valkey/valkey-bundle:9.1.2");

    private static final int VALKEY_PORT = 6379;
    private static final int DIMENSIONS = 4;

    /** Query vector; distances below are derived from it. */
    private static final List<Float> QUERY = List.of(1f, 0f, 0f, 0f);

    private static GenericContainer<?> valkey;

    @BeforeAll
    void startContainer() {
        valkey = new GenericContainer<>(VALKEY_BUNDLE).withExposedPorts(VALKEY_PORT);
        valkey.start();
    }

    @AfterAll
    void stopContainer() {
        if (valkey != null) {
            valkey.stop();
        }
    }

    /** Builds a config pointed at the container's mapped address. Never a fixed host or port. */
    private ValkeyConfig configFor(String keyPrefix) {
        ValkeyConfig config = new ValkeyConfig();
        config.setHost(valkey.getHost());
        config.setPort(valkey.getMappedPort(VALKEY_PORT));
        config.setDimensions(DIMENSIONS);
        config.setKeyPrefix(keyPrefix);
        config.setRequestTimeoutMs(5000);
        return config;
    }

    /**
     * Indexing in valkey-search is asynchronous, so poll until the expected number of documents is
     * visible rather than sleeping a fixed amount.
     */
    private List<IndexedDoc> searchUntil(
            ValkeyVectorDB db, String index, String namespace, int maxResults, int expected) {
        List<IndexedDoc> results = List.of();
        for (int attempt = 0; attempt < 50; attempt++) {
            results = db.search(index, namespace, QUERY, maxResults);
            if (results.size() == expected) {
                return results;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for indexing", e);
            }
        }
        return results;
    }

    @Test
    void storesAndRetrievesByKnnInDistanceOrder() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-order", configFor("rt-order"))) {
            db.updateEmbeddings("docs", "ns", "identical", null, "a", QUERY, Map.of("rank", 1));
            db.updateEmbeddings(
                    "docs",
                    "ns",
                    "oblique",
                    null,
                    "b",
                    List.of(.7f, .7f, 0f, 0f),
                    Map.of("rank", 2));
            db.updateEmbeddings(
                    "docs",
                    "ns",
                    "orthogonal",
                    null,
                    "c",
                    List.of(0f, 0f, 0f, 1f),
                    Map.of("rank", 3));

            List<IndexedDoc> results = searchUntil(db, "docs", "ns", 3, 3);

            assertEquals(
                    List.of("a", "b", "c"),
                    results.stream().map(IndexedDoc::getDocId).toList(),
                    "KNN results must be ordered by ascending cosine distance");

            // Score is raw cosine distance: lower is better. An exact match scores 0.
            assertEquals(0.0, results.get(0).getScore(), 1e-9);
            assertTrue(
                    results.get(0).getScore() < results.get(1).getScore()
                            && results.get(1).getScore() < results.get(2).getScore(),
                    "scores must increase with distance, proving they are not inverted");
            // Orthogonal vectors are at cosine distance 1.
            assertEquals(1.0, results.get(2).getScore(), 1e-6);
        }
    }

    @Test
    void roundTripsTextParentIdAndMetadata() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-fields", configFor("rt-fields"))) {
            db.updateEmbeddings(
                    "docs",
                    "ns",
                    "the quick brown fox",
                    "parent-42",
                    "doc-1",
                    QUERY,
                    Map.of("source", "unit-test", "page", 7));

            IndexedDoc doc = searchUntil(db, "docs", "ns", 1, 1).get(0);

            assertEquals("doc-1", doc.getDocId());
            assertEquals("parent-42", doc.getParentDocId());
            assertEquals("the quick brown fox", doc.getText());
            assertEquals("unit-test", doc.getMetadata().get("source"));
            assertEquals(7, doc.getMetadata().get("page"));
        }
    }

    @Test
    void nullParentDocIdDefaultsToDocId() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-parent", configFor("rt-parent"))) {
            db.updateEmbeddings("docs", "ns", "text", null, "solo", QUERY, Map.of());

            IndexedDoc doc = searchUntil(db, "docs", "ns", 1, 1).get(0);

            assertEquals("solo", doc.getDocId());
            assertEquals("solo", doc.getParentDocId());
        }
    }

    @Test
    void upsertReplacesVectorAndText() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-upsert", configFor("rt-upsert"))) {
            // First write is far from the query vector.
            db.updateEmbeddings(
                    "docs", "ns", "original", null, "same-id", List.of(0f, 0f, 0f, 1f), Map.of());
            IndexedDoc before = searchUntil(db, "docs", "ns", 1, 1).get(0);
            assertEquals("original", before.getText());
            assertEquals(1.0, before.getScore(), 1e-6);

            // Second write to the same id replaces it with an exact match.
            db.updateEmbeddings("docs", "ns", "revised", null, "same-id", QUERY, Map.of());

            IndexedDoc after = null;
            for (int attempt = 0; attempt < 50 && after == null; attempt++) {
                IndexedDoc candidate = searchUntil(db, "docs", "ns", 1, 1).get(0);
                if ("revised".equals(candidate.getText())) {
                    after = candidate;
                    break;
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted", e);
                }
            }

            assertNotNull(after, "upsert did not replace the stored document");
            assertEquals("revised", after.getText());
            assertEquals(0.0, after.getScore(), 1e-9);

            // Still a single document, not a duplicate.
            assertEquals(1, searchUntil(db, "docs", "ns", 10, 1).size());
        }
    }

    @Test
    void maxResultsIsHonouredBeyondDefaultLimitOfTen() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-limit", configFor("rt-limit"))) {
            // 12 documents: more than the RediSearch-family default LIMIT of 10.
            for (int i = 0; i < 12; i++) {
                db.updateEmbeddings(
                        "docs",
                        "ns",
                        "doc " + i,
                        null,
                        "id-" + i,
                        List.of(1f, i / 100f, 0f, 0f),
                        Map.of());
            }

            assertEquals(12, searchUntil(db, "docs", "ns", 12, 12).size());
            // And a smaller cap is still respected.
            assertEquals(5, searchUntil(db, "docs", "ns", 5, 5).size());
        }
    }

    @Test
    void namespacesAreIsolatedUnderTheSameIndexName() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-ns", configFor("rt-ns"))) {
            db.updateEmbeddings("docs", "tenantA", "belongs to A", null, "a1", QUERY, Map.of());
            db.updateEmbeddings("docs", "tenantB", "belongs to B", null, "b1", QUERY, Map.of());

            List<IndexedDoc> fromA = searchUntil(db, "docs", "tenantA", 10, 1);
            List<IndexedDoc> fromB = searchUntil(db, "docs", "tenantB", 10, 1);

            assertEquals(List.of("a1"), fromA.stream().map(IndexedDoc::getDocId).toList());
            assertEquals(List.of("b1"), fromB.stream().map(IndexedDoc::getDocId).toList());
            assertEquals("belongs to A", fromA.get(0).getText());
            assertEquals("belongs to B", fromB.get(0).getText());
        }
    }

    @Test
    void concurrentFirstWritesToSameIndexAllSucceed() throws Exception {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-concurrent", configFor("rt-concurrent"))) {
            int writers = 8;
            ExecutorService pool = Executors.newFixedThreadPool(writers);
            try {
                List<Callable<Integer>> tasks = new ArrayList<>();
                for (int i = 0; i < writers; i++) {
                    String id = "c-" + i;
                    tasks.add(
                            () ->
                                    db.updateEmbeddings(
                                            "docs", "ns", "concurrent", null, id, QUERY, Map.of()));
                }

                // Every write must succeed: the first triggers FT.CREATE, the rest must not fail
                // with "index already exists".
                for (Future<Integer> future : pool.invokeAll(tasks)) {
                    assertEquals(1, future.get(30, TimeUnit.SECONDS));
                }
            } finally {
                pool.shutdownNow();
            }

            assertEquals(writers, searchUntil(db, "docs", "ns", writers, writers).size());
        }
    }

    @Test
    void secondInstanceReusesExistingIndexWithoutFailing() {
        ValkeyConfig config = configFor("rt-exists");

        // First instance creates the physical index via FT.CREATE.
        try (ValkeyVectorDB first = new ValkeyVectorDB("rt-exists-1", config)) {
            assertEquals(
                    1,
                    first.updateEmbeddings(
                            "docs", "ns", "from first", null, "d1", QUERY, Map.of()));
        }

        // A second instance has an empty index cache, so its first write issues FT.CREATE again
        // against an index that already exists. That error must be suppressed, not propagated.
        // This is the real-world path after a server restart or with two configured providers
        // pointing at the same Valkey.
        try (ValkeyVectorDB second = new ValkeyVectorDB("rt-exists-2", configFor("rt-exists"))) {
            assertEquals(
                    1,
                    second.updateEmbeddings(
                            "docs", "ns", "from second", null, "d2", QUERY, Map.of()),
                    "second instance must tolerate the pre-existing index");

            // Both documents are present and searchable through the reused index.
            List<IndexedDoc> results = searchUntil(second, "docs", "ns", 10, 2);
            assertEquals(
                    List.of("d1", "d2"),
                    results.stream().map(IndexedDoc::getDocId).sorted().toList());
        }
    }

    @Test
    void dimensionMismatchIsRejectedAgainstLiveServer() {
        try (ValkeyVectorDB db = new ValkeyVectorDB("rt-dims", configFor("rt-dims"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            db.updateEmbeddings(
                                    "docs", "ns", "text", null, "bad", List.of(1f, 0f), Map.of()));
        }
    }
}
