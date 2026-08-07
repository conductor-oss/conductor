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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import glide.api.GlideClient;
import glide.api.commands.servermodules.FT;
import glide.api.models.GlideString;
import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTSearchOptions;
import glide.api.models.configuration.GlideClientConfiguration;
import glide.api.models.exceptions.RequestException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValkeyVectorDB that verify encoding, validation, error classification, lifecycle,
 * namespace isolation, and data integrity without requiring a live Valkey server. Uses the
 * package-private constructor for test injection of a mocked GlideClient.
 */
class ValkeyVectorDBTest {

    // ----- Embedding encoding tests -----

    @Test
    void encodeEmbedding_littleEndian_negativeOne() {
        // -1.0f in IEEE 754 little-endian is 0x000080BF (bytes: 00 00 80 BF)
        byte[] result = ValkeyVectorDB.encodeEmbedding(List.of(-1.0f));
        assertEquals(4, result.length);
        assertEquals((byte) 0x00, result[0]);
        assertEquals((byte) 0x00, result[1]);
        assertEquals((byte) 0x80, result[2]);
        assertEquals((byte) 0xBF, result[3]);
    }

    @Test
    void encodeEmbedding_roundTrip() {
        List<Float> input = List.of(1.0f, 0.5f, -0.25f, 0.0f);
        byte[] encoded = ValkeyVectorDB.encodeEmbedding(input);
        assertEquals(16, encoded.length); // 4 floats * 4 bytes

        // Decode back and verify
        ByteBuffer buf = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1.0f, buf.getFloat(), 0.0001f);
        assertEquals(0.5f, buf.getFloat(), 0.0001f);
        assertEquals(-0.25f, buf.getFloat(), 0.0001f);
        assertEquals(0.0f, buf.getFloat(), 0.0001f);
    }

    @Test
    void encodeEmbedding_identicalForStoreAndQuery() {
        List<Float> vector = List.of(0.7f, 0.7f, 0.0f, 0.0f);
        byte[] first = ValkeyVectorDB.encodeEmbedding(vector);
        byte[] second = ValkeyVectorDB.encodeEmbedding(vector);
        assertArrayEquals(first, second);
    }

    // ----- Namespace/index isolation tests (Fix #1) -----

    @Test
    void physicalIndexNameFor_distinctPerNamespace() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());

        String physA = db.physicalIndexNameFor("docs", "namespaceA");
        String physB = db.physicalIndexNameFor("docs", "namespaceB");

        assertNotEquals(physA, physB);
        // Both contain the logical name and namespace
        assertTrue(physA.contains("docs"));
        assertTrue(physA.contains("namespaceA"));
        assertTrue(physB.contains("docs"));
        assertTrue(physB.contains("namespaceB"));
    }

    @Test
    void physicalIndexNameFor_includesNormalizedPrefix() {
        ValkeyConfig config = defaultConfig();
        config.setKeyPrefix("myapp");
        ValkeyVectorDB db = createMockValkeyDB(config);

        String phys = db.physicalIndexNameFor("idx", "ns");
        assertEquals("myapp:idx:ns", phys);
    }

    @Test
    void physicalIndexNameFor_defaultPrefix() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());

        String phys = db.physicalIndexNameFor("idx", "ns");
        assertEquals("conductor:idx:ns", phys);
    }

    @Test
    void keyPrefixFor_producesCorrectFormat() {
        ValkeyConfig config = defaultConfig();
        config.setKeyPrefix("myapp");
        ValkeyVectorDB db = createMockValkeyDB(config);

        String prefix = db.keyPrefixFor("myindex", "myns");
        assertEquals("myapp:myindex:myns:", prefix);
    }

    @Test
    void keyPrefixFor_defaultPrefix() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());

        String prefix = db.keyPrefixFor("idx", "ns");
        assertEquals("conductor:idx:ns:", prefix);
    }

    // ----- Name validation tests -----

    @Test
    void updateEmbeddings_invalidIndexName_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.updateEmbeddings(
                                "bad index name", "ns", "doc", "parent", "id", embeddings, null));
        // Fix #4: verify no interaction with client before validation
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_invalidNamespace_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.updateEmbeddings(
                                "index", "ns/bad", "doc", "parent", "id", embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_nullNamespace_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () -> db.updateEmbeddings("index", null, "doc", "parent", "id", embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_nullDoc_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                NullPointerException.class,
                () -> db.updateEmbeddings("index", "ns", null, "parent", "id", embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_nullId_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                NullPointerException.class,
                () -> db.updateEmbeddings("index", "ns", "doc", "parent", null, embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_idWithColon_throws() {
        // Fix #N1: an id with a delimiter character must be rejected before key construction,
        // consistent with how indexName/namespace are already validated.
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        db.updateEmbeddings(
                                "index", "ns", "doc", "parent", "bad:id", embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_dimensionMismatch_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class,
                () -> db.updateEmbeddings("index", "ns", "doc", "parent", "id", embeddings, null));
        verifyNoInteractions(mockClient);
    }

    @Test
    void search_dimensionMismatch_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f);
        assertThrows(IllegalArgumentException.class, () -> db.search("index", "ns", embeddings, 5));
        verifyNoInteractions(mockClient);
    }

    @Test
    void search_maxResultsZero_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> db.search("index", "ns", embeddings, 0));
        assertTrue(ex.getMessage().contains("maxResults"));
        verifyNoInteractions(mockClient);
    }

    @Test
    void search_maxResultsNegative_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        assertThrows(
                IllegalArgumentException.class, () -> db.search("index", "ns", embeddings, -1));
        verifyNoInteractions(mockClient);
    }

    // ----- Embedding element validation (Fix #7) -----

    @Test
    void updateEmbeddings_nullElement_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = Arrays.asList(1.0f, null, 0.0f, 0.0f);
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                db.updateEmbeddings(
                                        "index", "ns", "doc", "parent", "id", embeddings, null));
        assertTrue(ex.getMessage().contains("null"));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_infiniteElement_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, Float.POSITIVE_INFINITY, 0.0f, 0.0f);
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                db.updateEmbeddings(
                                        "index", "ns", "doc", "parent", "id", embeddings, null));
        assertTrue(ex.getMessage().contains("finite"));
        verifyNoInteractions(mockClient);
    }

    @Test
    void updateEmbeddings_nanElement_throws() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        List<Float> embeddings = List.of(1.0f, Float.NaN, 0.0f, 0.0f);
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                db.updateEmbeddings(
                                        "index", "ns", "doc", "parent", "id", embeddings, null));
        assertTrue(ex.getMessage().contains("finite"));
        verifyNoInteractions(mockClient);
    }

    // ----- Close / lifecycle tests (Fix #4) -----

    @Test
    void close_isIdempotent_callsClientCloseOnce() throws Exception {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        // First close should call client.close()
        assertDoesNotThrow(db::close);
        // Second close should be a no-op
        assertDoesNotThrow(db::close);

        // Verify client.close() was called exactly once
        verify(mockClient, times(1)).close();
    }

    @Test
    void operationsAfterClose_throwClearly() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);

        db.close();

        List<Float> embeddings = List.of(1.0f, 0.0f, 0.0f, 0.0f);
        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                db.updateEmbeddings(
                                        "index", "ns", "doc", "parent", "id", embeddings, null));
        assertTrue(ex.getMessage().contains("closed"));

        IllegalStateException searchEx =
                assertThrows(
                        IllegalStateException.class, () -> db.search("index", "ns", embeddings, 5));
        assertTrue(searchEx.getMessage().contains("closed"));
    }

    // ----- Already-exists error classification (Fix #3) -----

    @Test
    void isAlreadyExistsError_requestException_true() {
        // GLIDE RequestException with "already exists." message should be suppressed
        RequestException reqEx =
                new RequestException("Index: test_idx in database 0 already exists.");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertTrue(ValkeyVectorDB.isAlreadyExistsError(wrapper));
    }

    @Test
    void isAlreadyExistsError_plainRuntimeException_false() {
        // A plain RuntimeException with the same text must NOT be classified as already-exists
        RuntimeException plain = new RuntimeException("Index: test_idx already exists.");
        assertFalse(ValkeyVectorDB.isAlreadyExistsError(plain));
    }

    @Test
    void isAlreadyExistsError_unknownCommand_false() {
        // "ERR unknown command" wrapped in RequestException should NOT match already-exists
        RequestException reqEx = new RequestException("ERR unknown command 'FT.CREATE'");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertFalse(ValkeyVectorDB.isAlreadyExistsError(wrapper));
    }

    @Test
    void isAlreadyExistsError_directRequestException_true() {
        // RequestException itself (as the top-level RuntimeException) should work too
        RequestException reqEx = new RequestException("Index already exists.");
        assertTrue(ValkeyVectorDB.isAlreadyExistsError(reqEx));
    }

    @Test
    void isAlreadyExistsError_substringElsewhereInMessage_notMatched() {
        // Fix #M1: a free-floating contains() would have matched this; the anchored endsWith()
        // check must not, since the message does not actually end in "already exists."
        RequestException reqEx =
                new RequestException("already exists. but something else failed after that");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertFalse(ValkeyVectorDB.isAlreadyExistsError(wrapper));
    }

    @Test
    void isAlreadyExistsError_punctuationVariant_notMatched() {
        // Fix #M1: dropping the trailing period must not match. Wording drift should fail loud
        // (surface as a real error) rather than being silently misclassified either way.
        RequestException reqEx =
                new RequestException("Index: test_idx in database 0 already exists");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertFalse(ValkeyVectorDB.isAlreadyExistsError(wrapper));
    }

    // ----- Unknown-command error classification (Fix #M1) -----

    @Test
    void isUnknownCommandError_requestException_true() {
        RequestException reqEx =
                new RequestException(
                        "ERR unknown command 'FT.CREATE', with args beginning with: 'idx'");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertTrue(ValkeyVectorDB.isUnknownCommandError(wrapper));
    }

    @Test
    void isUnknownCommandError_directRequestException_true() {
        RequestException reqEx = new RequestException("ERR unknown command 'FT.SEARCH'");
        assertTrue(ValkeyVectorDB.isUnknownCommandError(reqEx));
    }

    @Test
    void isUnknownCommandError_plainRuntimeException_false() {
        // Fix #M1: a non-GLIDE exception must NOT be classified as unknown-command, even if its
        // message happens to contain the phrase. The prior fallback bypassed the RequestException
        // type guard for this case; it must not anymore.
        RuntimeException plain = new RuntimeException("ERR unknown command 'FT.CREATE'");
        assertFalse(ValkeyVectorDB.isUnknownCommandError(plain));
    }

    @Test
    void isUnknownCommandError_alreadyExistsMessage_false() {
        RequestException reqEx =
                new RequestException("Index: test_idx in database 0 already exists.");
        RuntimeException wrapper = new RuntimeException(reqEx);
        assertFalse(ValkeyVectorDB.isUnknownCommandError(wrapper));
    }

    // ----- ConcurrentHashMap recursion fix (Fix #2) -----

    @Test
    void failedIndexCreate_allowsRetry_preservesOriginalException() {
        ValkeyConfig config = defaultConfig();
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(config, mockClient);
        CompletableFuture<String> failedCreate = new CompletableFuture<>();
        RequestException original = new RequestException("temporary FT.CREATE failure");
        failedCreate.completeExceptionally(original);

        when(mockClient.hset(
                        any(GlideString.class),
                        org.mockito.ArgumentMatchers.<GlideString, GlideString>anyMap()))
                .thenReturn(CompletableFuture.completedFuture(4L));
        try (MockedStatic<FT> ft = mockStatic(FT.class)) {
            ft.when(
                            () ->
                                    FT.create(
                                            eq(mockClient),
                                            eq("conductor:index:ns"),
                                            any(FTCreateOptions.FieldInfo[].class),
                                            any(FTCreateOptions.class)))
                    .thenReturn(failedCreate, CompletableFuture.completedFuture("OK"));

            List<Float> vector = List.of(1.0f, 0.0f, 0.0f, 0.0f);
            RequestException thrown =
                    assertThrows(
                            RequestException.class,
                            () ->
                                    db.updateEmbeddings(
                                            "index", "ns", "doc", "parent", "id", vector,
                                            Map.of()));
            assertSame(original, thrown);

            assertEquals(
                    1, db.updateEmbeddings("index", "ns", "doc", "parent", "id", vector, Map.of()));
            ft.verify(
                    () ->
                            FT.create(
                                    eq(mockClient),
                                    eq("conductor:index:ns"),
                                    any(FTCreateOptions.FieldInfo[].class),
                                    any(FTCreateOptions.class)),
                    times(2));
        }
    }

    // ----- Metadata serialization (Fix #6: no silent data loss) -----

    @Test
    void serializeMetadata_null_returnsEmptyJson() {
        assertEquals("{}", ValkeyVectorDB.serializeMetadata(null));
    }

    @Test
    void serializeMetadata_empty_returnsEmptyJson() {
        assertEquals("{}", ValkeyVectorDB.serializeMetadata(Collections.emptyMap()));
    }

    @Test
    void serializeMetadata_valid_returnsJson() {
        Map<String, Object> meta = Map.of("key", "value", "count", 42);
        String json = ValkeyVectorDB.serializeMetadata(meta);
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
        assertTrue(json.contains("42"));
    }

    @Test
    void serializeMetadata_selfReferential_throwsInsteadOfSilentEmptyJson() {
        // A self-referential object cannot be serialized; must throw, not write "{}"
        Map<String, Object> meta = new HashMap<>();
        meta.put("self", meta); // circular reference

        assertThrows(IllegalStateException.class, () -> ValkeyVectorDB.serializeMetadata(meta));
    }

    @Test
    void deserializeMetadata_malformed_throwsInsteadOfSilentEmpty() {
        assertThrows(
                IllegalStateException.class,
                () -> ValkeyVectorDB.deserializeMetadata("not valid json{{{", "doc1"));
    }

    @Test
    void deserializeMetadata_valid_returnsParsedMap() {
        Map<String, Object> result =
                ValkeyVectorDB.deserializeMetadata("{\"key\":\"value\"}", "doc1");
        assertEquals("value", result.get("key"));
    }

    // ----- Score parsing (Fix #6: no silent data loss) -----

    @Test
    void parseScore_valid_returnsDouble() {
        assertEquals(0.5, ValkeyVectorDB.parseScore("0.5", "doc1"), 0.0001);
    }

    @Test
    void parseScore_null_throwsInsteadOfDefaultZero() {
        assertThrows(IllegalStateException.class, () -> ValkeyVectorDB.parseScore(null, "doc1"));
    }

    @Test
    void parseScore_empty_throwsInsteadOfDefaultZero() {
        assertThrows(IllegalStateException.class, () -> ValkeyVectorDB.parseScore("", "doc1"));
    }

    @Test
    void parseScore_malformed_throwsInsteadOfDefaultZero() {
        assertThrows(
                IllegalStateException.class,
                () -> ValkeyVectorDB.parseScore("not_a_number", "doc1"));
    }

    // ----- Key prefix normalization (Fix #7) -----

    @Test
    void normalizeKeyPrefix_stripsTrailingColons() {
        assertEquals("conductor", ValkeyVectorDB.normalizeKeyPrefix("conductor::"));
        assertEquals("app", ValkeyVectorDB.normalizeKeyPrefix("app:"));
    }

    @Test
    void normalizeKeyPrefix_nullUsesDefault() {
        assertEquals("conductor", ValkeyVectorDB.normalizeKeyPrefix(null));
    }

    @Test
    void normalizeKeyPrefix_blankAfterStrip_throws() {
        assertThrows(
                IllegalArgumentException.class, () -> ValkeyVectorDB.normalizeKeyPrefix(":::"));
    }

    @Test
    void normalizeKeyPrefix_unsafeChars_throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ValkeyVectorDB.normalizeKeyPrefix("bad prefix"));
    }

    @Test
    void normalizeKeyPrefix_validChars_passes() {
        assertEquals("my-app.v1", ValkeyVectorDB.normalizeKeyPrefix("my-app.v1"));
        assertEquals("my_app", ValkeyVectorDB.normalizeKeyPrefix("my_app"));
    }

    // ----- Config validation (Fix #7) -----

    @Test
    void construction_negativePort_throws() {
        ValkeyConfig config = defaultConfig();
        config.setPort(-1);
        GlideClient mockClient = mock(GlideClient.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValkeyVectorDB("test", config, mockClient));
    }

    @Test
    void construction_zeroDimensions_throws() {
        ValkeyConfig config = defaultConfig();
        config.setDimensions(0);
        GlideClient mockClient = mock(GlideClient.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValkeyVectorDB("test", config, mockClient));
    }

    @Test
    void construction_negativeRequestTimeout_throws() {
        ValkeyConfig config = defaultConfig();
        config.setRequestTimeoutMs(-100);
        GlideClient mockClient = mock(GlideClient.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValkeyVectorDB("test", config, mockClient));
    }

    @Test
    void construction_negativeDatabase_throws() {
        ValkeyConfig config = defaultConfig();
        config.setDatabase(-1);
        GlideClient mockClient = mock(GlideClient.class);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValkeyVectorDB("test", config, mockClient));
    }

    // ----- Config resolution tests -----

    @Test
    void resolveDistanceMetric_validValues() {
        assertEquals(
                glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.COSINE,
                ValkeyConfig.resolveDistanceMetric("cosine"));
        assertEquals(
                glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.L2,
                ValkeyConfig.resolveDistanceMetric("l2"));
        assertEquals(
                glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.IP,
                ValkeyConfig.resolveDistanceMetric("ip"));
        // Case insensitive
        assertEquals(
                glide.api.models.commands.FT.FTCreateOptions.DistanceMetric.COSINE,
                ValkeyConfig.resolveDistanceMetric("COSINE"));
    }

    @Test
    void resolveDistanceMetric_unknownThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ValkeyConfig.resolveDistanceMetric("euclidean"));
    }

    @Test
    void resolveIndexingMethod_validValues() {
        assertEquals("hnsw", ValkeyConfig.resolveIndexingMethod("hnsw"));
        assertEquals("flat", ValkeyConfig.resolveIndexingMethod("flat"));
        assertEquals("hnsw", ValkeyConfig.resolveIndexingMethod("HNSW"));
    }

    @Test
    void resolveIndexingMethod_unknownThrows() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ValkeyConfig.resolveIndexingMethod("ivfflat"));
    }

    // ----- Production-path and response-shape tests -----

    @Test
    void createAndSearch_useSamePhysicalIndexName() {
        GlideClient mockClient = mock(GlideClient.class);
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig(), mockClient);
        when(mockClient.hset(
                        any(GlideString.class),
                        org.mockito.ArgumentMatchers.<GlideString, GlideString>anyMap()))
                .thenReturn(CompletableFuture.completedFuture(4L));

        try (MockedStatic<FT> ft = mockStatic(FT.class)) {
            ft.when(
                            () ->
                                    FT.create(
                                            eq(mockClient),
                                            eq("conductor:docs:teamA"),
                                            any(FTCreateOptions.FieldInfo[].class),
                                            any(FTCreateOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture("OK"));
            ft.when(
                            () ->
                                    FT.search(
                                            eq(mockClient),
                                            eq("conductor:docs:teamA"),
                                            anyString(),
                                            any(FTSearchOptions.class)))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    new Object[] {0L, new LinkedHashMap<>()}));

            List<Float> vector = List.of(1.0f, 0.0f, 0.0f, 0.0f);
            assertEquals(
                    1, db.updateEmbeddings("docs", "teamA", "doc", null, "id", vector, Map.of()));
            assertTrue(db.search("docs", "teamA", vector, 3).isEmpty());

            ft.verify(
                    () ->
                            FT.create(
                                    eq(mockClient),
                                    eq("conductor:docs:teamA"),
                                    any(FTCreateOptions.FieldInfo[].class),
                                    any(FTCreateOptions.class)));
            ft.verify(
                    () ->
                            FT.search(
                                    eq(mockClient),
                                    eq("conductor:docs:teamA"),
                                    anyString(),
                                    any(FTSearchOptions.class)));
        }
    }

    @Test
    void parseSearchResults_validShape_roundTripsFields() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());
        Map<GlideString, GlideString> fields =
                Map.of(
                        GlideString.gs("doc"), GlideString.gs("hello"),
                        GlideString.gs("parent_doc_id"), GlideString.gs("parent"),
                        GlideString.gs("metadata"), GlideString.gs("{\"source\":\"test\"}"),
                        GlideString.gs("__embedding_score"), GlideString.gs("0.25"));
        Map<GlideString, Map<GlideString, GlideString>> hits = new LinkedHashMap<>();
        hits.put(GlideString.gs("conductor:index:ns:doc-1"), fields);

        List<org.conductoross.conductor.ai.model.IndexedDoc> result =
                db.parseSearchResults(new Object[] {1L, hits}, "index", "ns");

        assertEquals(1, result.size());
        assertEquals("doc-1", result.get(0).getDocId());
        assertEquals("parent", result.get(0).getParentDocId());
        assertEquals("hello", result.get(0).getText());
        assertEquals(0.25, result.get(0).getScore(), 0.0001);
        assertEquals("test", result.get(0).getMetadata().get("source"));
    }

    @Test
    void parseSearchResults_rejectsNullShortAndMalformedEntries() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());
        assertThrows(IllegalStateException.class, () -> db.parseSearchResults(null, "index", "ns"));
        assertThrows(
                IllegalStateException.class,
                () -> db.parseSearchResults(new Object[] {0L}, "index", "ns"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.parseSearchResults(
                                new Object[] {1L, Map.of("not-a-glide-key", Map.of())},
                                "index",
                                "ns"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        db.parseSearchResults(
                                new Object[] {
                                    1L,
                                    Map.of(
                                            GlideString.gs("conductor:index:ns:doc-1"),
                                            Map.of(GlideString.gs("doc"), GlideString.gs("hello")))
                                },
                                "index",
                                "ns"));
    }

    @Test
    void await_preservesInterruptFlag() {
        ValkeyVectorDB db = createMockValkeyDB(defaultConfig());
        Thread.currentThread().interrupt();
        try {
            RuntimeException thrown =
                    assertThrows(RuntimeException.class, () -> db.await(new CompletableFuture<>()));
            assertTrue(thrown.getMessage().contains("interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void publicConstructor_validatesBeforeAttemptingConnection() {
        ValkeyConfig config = defaultConfig();
        config.setHost("host-that-must-not-be-contacted");
        config.setPort(-1);
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ValkeyVectorDB("invalid", config));
        assertTrue(thrown.getMessage().contains("port"));
    }

    @Test
    void parseScore_nonFinite_throws() {
        assertThrows(IllegalStateException.class, () -> ValkeyVectorDB.parseScore("NaN", "doc1"));
        assertThrows(
                IllegalStateException.class, () -> ValkeyVectorDB.parseScore("Infinity", "doc1"));
    }

    // ----- Helpers -----

    // ----- Client configuration: clientName for CLIENT LIST observability (Fix #L1) -----

    @Test
    void buildClientConfiguration_setsClientName() {
        GlideClientConfiguration built =
                ValkeyVectorDB.buildClientConfiguration("my-instance", defaultConfig(), 2000L);
        assertEquals("conductor-vectordb-my-instance", built.getClientName());
    }

    @Test
    void buildClientConfiguration_sanitizesWhitespaceInName() {
        // A human-readable instance name with a space must not fail CLIENT SETNAME at connection
        // time; the space is sanitized rather than passed through verbatim.
        GlideClientConfiguration built =
                ValkeyVectorDB.buildClientConfiguration("my instance", defaultConfig(), 2000L);
        assertEquals("conductor-vectordb-my_instance", built.getClientName());
    }

    private static ValkeyConfig defaultConfig() {
        ValkeyConfig config = new ValkeyConfig();
        config.setDimensions(4);
        return config;
    }

    /** Creates a ValkeyVectorDB using the package-private constructor with a mock GlideClient. */
    private static ValkeyVectorDB createMockValkeyDB(ValkeyConfig config) {
        return new ValkeyVectorDB("test-valkey", config, mock(GlideClient.class));
    }

    private static ValkeyVectorDB createMockValkeyDB(ValkeyConfig config, GlideClient client) {
        return new ValkeyVectorDB("test-valkey", config, client);
    }
}
