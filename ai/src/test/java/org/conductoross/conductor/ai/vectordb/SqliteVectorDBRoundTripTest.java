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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.model.IndexedDoc;
import org.conductoross.conductor.ai.vectordb.sqlite.SqliteConfig;
import org.conductoross.conductor.ai.vectordb.sqlite.SqliteVecExtensions;
import org.conductoross.conductor.ai.vectordb.sqlite.SqliteVectorDB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end test exercising the real sqlite-vec {@code vec0} extension bundled in the jar: it
 * creates a database, stores embeddings and runs KNN searches against an actual SQLite instance.
 *
 * <p>Skipped (rather than failed) when no bundled binary is available for the host platform, e.g.
 * an offline build where {@code downloadSqliteVec} did not run.
 */
public class SqliteVectorDBRoundTripTest {

    @TempDir Path tempDir;

    private SqliteVectorDB vectorDB;

    @BeforeAll
    static void requireBundledExtension() {
        assumeTrue(
                SqliteVecExtensions.resolveBundledExtensionPath() != null,
                "No bundled sqlite-vec extension for this platform; skipping e2e test");
    }

    @BeforeEach
    void setup() {
        SqliteConfig config = new SqliteConfig();
        config.setDbPath(tempDir.resolve("roundtrip.db").toString());
        config.setDimensions(4);
        config.setDistanceMetric("cosine");
        // extensionPath left null on purpose: the bundled vec0 binary is auto-resolved.
        vectorDB = new SqliteVectorDB("e2e-sqlite", config);
    }

    @Test
    void testStoreAndSearchReturnsNearestNeighborFirst() {
        vectorDB.updateEmbeddings(
                "idx", "docs", "the cat sat", "p1", "a", List.of(1f, 0f, 0f, 0f), Map.of("n", 1));
        vectorDB.updateEmbeddings(
                "idx", "docs", "the dog ran", "p2", "b", List.of(0f, 1f, 0f, 0f), Map.of("n", 2));
        vectorDB.updateEmbeddings(
                "idx", "docs", "a feline", "p3", "c", List.of(0.9f, 0.1f, 0f, 0f), Map.of("n", 3));

        // Query closest to "a": exact match should rank first, the near "c" second.
        List<IndexedDoc> results = vectorDB.search("idx", "docs", List.of(1f, 0f, 0f, 0f), 2);

        assertEquals(2, results.size());
        assertEquals("a", results.get(0).getDocId());
        assertEquals("c", results.get(1).getDocId());
        // Cosine distance: exact match is ~0 and strictly closer than the near neighbor.
        assertTrue(results.get(0).getScore() <= results.get(1).getScore());
        // Doc text, parent id and metadata round-trip.
        assertEquals("the cat sat", results.get(0).getText());
        assertEquals("p1", results.get(0).getParentDocId());
        assertEquals(1, results.get(0).getMetadata().get("n"));
    }

    @Test
    void testUpsertReplacesExistingVector() {
        vectorDB.updateEmbeddings(
                "idx", "docs", "v1", "p", "same-id", List.of(1f, 0f, 0f, 0f), Map.of());
        // Re-index the same id with a different vector and document.
        vectorDB.updateEmbeddings(
                "idx", "docs", "v2", "p", "same-id", List.of(0f, 1f, 0f, 0f), Map.of());

        List<IndexedDoc> results = vectorDB.search("idx", "docs", List.of(0f, 1f, 0f, 0f), 10);

        assertEquals(1, results.size());
        assertEquals("same-id", results.get(0).getDocId());
        assertEquals("v2", results.get(0).getText());
    }

    @Test
    void testSearchRespectsK() {
        for (int i = 0; i < 5; i++) {
            vectorDB.updateEmbeddings(
                    "idx",
                    "docs",
                    "doc" + i,
                    "p",
                    "id" + i,
                    List.of((float) i, 1f, 0f, 0f),
                    Map.of());
        }
        assertEquals(3, vectorDB.search("idx", "docs", List.of(0f, 1f, 0f, 0f), 3).size());
    }
}
