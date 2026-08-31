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
package org.conductoross.conductor.service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaServiceImplTest {

    private InMemorySchemaDAO dao;
    private SchemaCacheProperties cacheProperties;
    private SchemaServiceImpl service;

    @BeforeEach
    void setUp() {
        dao = new InMemorySchemaDAO();
        cacheProperties = new SchemaCacheProperties();
        service = newService();
    }

    private SchemaServiceImpl newService() {
        return new SchemaServiceImpl(
                dao,
                cacheProperties,
                new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
    }

    private static SchemaDef schema(String name, int version) {
        SchemaDef def = new SchemaDef();
        def.setName(name);
        def.setVersion(version);
        def.setType(SchemaDef.Type.JSON);
        def.setData(Map.of("type", "object"));
        return def;
    }

    @Test
    void savedSchemaIsReadableByNameAndVersion() {
        service.saveSchema(schema("order", 1), false);

        SchemaDef found = service.getSchema("order", 1);

        assertEquals("order", found.getName());
        assertEquals(1, found.getVersion());
        assertEquals(Map.of("type", "object"), found.getData());
    }

    @Test
    void saveWithoutAVersionLandsAtVersionOne() {
        SchemaDef def = schema("order", 0);

        SchemaDef saved = service.saveSchema(def, false);

        assertEquals(1, saved.getVersion());
        assertNotNull(service.getSchema("order", 1));
    }

    @Test
    void externalRefRoundTrips() {
        SchemaDef def = schema("order", 1);
        def.setType(SchemaDef.Type.AVRO);
        def.setExternalRef("registry://orders/v1");

        service.saveSchema(def, false);

        SchemaDef found = service.getSchema("order", 1);
        assertEquals("registry://orders/v1", found.getExternalRef());
        assertEquals(SchemaDef.Type.AVRO, found.getType());
    }

    @Test
    void savingWithoutNewVersionOverwritesInPlace() {
        service.saveSchema(schema("order", 1), false);

        SchemaDef corrected = schema("order", 1);
        corrected.setData(Map.of("type", "array"));
        service.saveSchema(corrected, false);

        assertEquals(Map.of("type", "array"), service.getSchema("order", 1).getData());
        assertEquals(1, service.getAllSchemas().size());
    }

    @Test
    void newVersionAllocatesOnePastTheHighest() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 5), false);

        SchemaDef saved = service.saveSchema(schema("order", 1), true);

        assertEquals(6, saved.getVersion());
        assertEquals(6, service.getSchema("order").getVersion());
    }

    @Test
    void newVersionOfAnUnknownNameStartsAtOne() {
        SchemaDef saved = service.saveSchema(schema("order", 0), true);

        assertEquals(1, saved.getVersion());
    }

    @Test
    void newVersionRetriesPastAVersionClaimedByAnotherWriter() {
        service.saveSchema(schema("order", 1), false);
        // Another writer takes version 2 between this caller's read of the maximum and its insert.
        dao.queueRacer(schema("order", 2));

        SchemaDef saved = service.saveSchema(schema("order", 0), true);

        assertEquals(3, saved.getVersion());
        assertEquals(2, dao.createAttempts.get());
    }

    @Test
    void newVersionGivesUpWithAConflictWhenItKeepsLosing() {
        service.saveSchema(schema("order", 1), false);
        IntStream.rangeClosed(2, 20).forEach(v -> dao.queueRacer(schema("order", v)));

        assertThrows(ConflictException.class, () -> service.saveSchema(schema("order", 0), true));
    }

    @Test
    void concurrentNewVersionSavesEachGetTheirOwnVersion() throws Exception {
        int writers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<SchemaDef>> futures =
                    IntStream.range(0, writers)
                            .mapToObj(
                                    i ->
                                            pool.submit(
                                                    () -> {
                                                        start.await();
                                                        return service.saveSchema(
                                                                schema("order", 0), true);
                                                    }))
                            .toList();
            start.countDown();
            List<Integer> versions = new java.util.ArrayList<>();
            for (java.util.concurrent.Future<SchemaDef> future : futures) {
                versions.add(future.get(30, TimeUnit.SECONDS).getVersion());
            }
            assertEquals(writers, versions.stream().distinct().count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void saveSchemasStoresEveryEntry() {
        List<SchemaDef> saved =
                service.saveSchemas(List.of(schema("order", 1), schema("payment", 1)), false);

        assertEquals(2, saved.size());
        assertEquals(2, service.getAllSchemas().size());
    }

    @Test
    void getLatestReturnsTheHighestVersion() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 3), false);
        service.saveSchema(schema("order", 2), false);

        assertEquals(3, service.getSchema("order").getVersion());
    }

    @Test
    void missingSchemaIsNotFoundRatherThanNull() {
        assertThrows(NotFoundException.class, () -> service.getSchema("absent"));
        assertThrows(NotFoundException.class, () -> service.getSchema("absent", 4));

        service.saveSchema(schema("order", 1), false);
        NotFoundException notFound =
                assertThrows(NotFoundException.class, () -> service.getSchema("order", 9));
        assertTrue(notFound.getMessage().contains("order"));
        assertTrue(notFound.getMessage().contains("9"));
    }

    @Test
    void deletingOneVersionLeavesTheRestOfTheHistory() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);

        service.deleteSchema("order", 2);

        assertEquals(1, service.getSchema("order").getVersion());
        assertThrows(NotFoundException.class, () -> service.getSchema("order", 2));
    }

    @Test
    void deletingByNameRemovesEveryVersion() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);
        service.saveSchema(schema("payment", 1), false);

        service.deleteSchema("order");

        assertEquals(1, service.getAllSchemas().size());
        assertEquals("payment", service.getAllSchemas().get(0).getName());
    }

    @Test
    void creatingStampsACreateTimeAndNoUpdateTime() {
        SchemaDef saved = service.saveSchema(schema("order", 1), false);

        assertTrue(saved.getCreateTime() > 0);
        assertEquals(0L, saved.getUpdateTime());
        // OSS has no authenticated principal, so nothing claims authorship.
        assertNull(saved.getCreatedBy());
        assertNull(saved.getUpdatedBy());
    }

    @Test
    void updatingInPlaceKeepsTheCreateTimeAndStampsTheUpdate() {
        long createdAt = service.saveSchema(schema("order", 1), false).getCreateTime();

        SchemaDef corrected = schema("order", 1);
        corrected.setData(Map.of("type", "array"));
        SchemaDef updated = service.saveSchema(corrected, false);

        assertEquals(createdAt, updated.getCreateTime());
        assertTrue(updated.getUpdateTime() > 0);
    }

    @Test
    void aNewVersionIsACreationRatherThanAnUpdate() {
        SchemaDef first = schema("order", 1);
        first.setUpdateTime(1L);
        service.saveSchema(first, false);

        SchemaDef second = service.saveSchema(schema("order", 0), true);

        assertEquals(2, second.getVersion());
        assertTrue(second.getCreateTime() > 0);
        assertEquals(0L, second.getUpdateTime());
    }

    @Test
    void blankNameIsRejected() {
        SchemaDef def = schema("  ", 1);
        assertThrows(IllegalArgumentException.class, () -> service.saveSchema(def, false));
    }

    @Test
    void theCacheIsOffUntilATimeToLiveIsConfigured() {
        assertFalse(new SchemaCacheProperties().isEnabled());

        SchemaCacheProperties configured = new SchemaCacheProperties();
        configured.setTtl(Duration.ofSeconds(30));
        assertTrue(configured.isEnabled());
    }

    @Test
    void cachedReadsStillSeeAnUpdateMadeThroughTheService() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        service.saveSchema(schema("order", 1), false);
        assertEquals(Map.of("type", "object"), service.getSchema("order", 1).getData());

        SchemaDef corrected = schema("order", 1);
        corrected.setData(Map.of("type", "array"));
        service.saveSchema(corrected, false);

        assertEquals(Map.of("type", "array"), service.getSchema("order", 1).getData());
        assertEquals(Map.of("type", "array"), service.getSchema("order").getData());
    }

    @Test
    void cachedLatestIsDroppedWhenANewVersionArrives() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        service.saveSchema(schema("order", 1), false);
        assertEquals(1, service.getSchema("order").getVersion());

        service.saveSchema(schema("order", 0), true);

        assertEquals(2, service.getSchema("order").getVersion());
    }

    @Test
    void cachedEntryIsDroppedOnDelete() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        service.saveSchema(schema("order", 1), false);
        assertNotNull(service.getSchema("order", 1));

        service.deleteSchema("order", 1);

        assertThrows(NotFoundException.class, () -> service.getSchema("order", 1));
    }

    @Test
    void aMissingSchemaIsNotCachedAsMissing() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        assertThrows(NotFoundException.class, () -> service.getSchema("order", 1));

        service.saveSchema(schema("order", 1), false);

        assertNotNull(service.getSchema("order", 1));
    }
}
