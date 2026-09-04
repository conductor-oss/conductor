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

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.dao.schema.InMemorySchemaDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaServiceTest {

    private InMemorySchemaDAO dao;
    private SchemaCacheProperties cacheProperties;
    private SchemaService service;

    @BeforeEach
    void setUp() {
        dao = new InMemorySchemaDAO();
        cacheProperties = new SchemaCacheProperties();
        service = newService();
    }

    private SchemaService newService() {
        return new SchemaService(
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

        SchemaDef found = service.getSchemaByNameAndVersion("order", 1);

        assertEquals("order", found.getName());
        assertEquals(1, found.getVersion());
        assertEquals(Map.of("type", "object"), found.getData());
    }

    @Test
    void saveWithoutAVersionLandsAtVersionOne() {
        SchemaDef def = schema("order", 0);

        SchemaDef saved = service.saveSchema(def, false);

        assertEquals(1, saved.getVersion());
        assertNotNull(service.getSchemaByNameAndVersion("order", 1));
    }

    @Test
    void externalRefRoundTrips() {
        SchemaDef def = schema("order", 1);
        def.setType(SchemaDef.Type.AVRO);
        def.setExternalRef("registry://orders/v1");

        service.saveSchema(def, false);

        SchemaDef found = service.getSchemaByNameAndVersion("order", 1);
        assertEquals("registry://orders/v1", found.getExternalRef());
        assertEquals(SchemaDef.Type.AVRO, found.getType());
    }

    @Test
    void savingWithoutNewVersionOverwritesInPlace() {
        service.saveSchema(schema("order", 1), false);

        SchemaDef corrected = schema("order", 1);
        corrected.setData(Map.of("type", "array"));
        service.saveSchema(corrected, false);

        assertEquals(
                Map.of("type", "array"), service.getSchemaByNameAndVersion("order", 1).getData());
        assertEquals(1, service.getAllSchemas().size());
    }

    @Test
    void newVersionAllocatesOnePastTheHighest() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 5), false);

        SchemaDef saved = service.saveSchema(schema("order", 1), true);

        assertEquals(6, saved.getVersion());
        assertEquals(6, service.getSchemaByNameWithLatestVersion("order").getVersion());
    }

    @Test
    void newVersionOfAnUnknownNameStartsAtOne() {
        SchemaDef saved = service.saveSchema(schema("order", 0), true);

        assertEquals(1, saved.getVersion());
    }

    /**
     * Allocation reads the maximum and saves one past it, with nothing between the two calls, so a
     * version another writer took in that window is overwritten rather than skipped. Pinned here
     * because it is the registry's behaviour, not an oversight in this test.
     */
    @Test
    void newVersionOverwritesAVersionClaimedBetweenTheReadAndTheSave() {
        service.saveSchema(schema("order", 1), false);

        SchemaDef saved = service.saveSchema(schema("order", 0), true);

        assertEquals(2, saved.getVersion());
        assertEquals(2, service.getAllSchemas().size());
    }

    @Test
    void savingDistinctNamesStoresEach() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("payment", 1), false);

        assertEquals(2, service.getAllSchemas().size());
    }

    @Test
    void getLatestReturnsTheHighestVersion() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 3), false);
        service.saveSchema(schema("order", 2), false);

        assertEquals(3, service.getSchemaByNameWithLatestVersion("order").getVersion());
    }

    /**
     * An absent schema is reported as {@code null}, not by throwing. {@link
     * org.conductoross.conductor.controllers.SchemaResource} is what turns that into a 404, and its
     * own tests cover it.
     */
    @Test
    void missingSchemaIsNull() {
        assertNull(service.getSchemaByNameWithLatestVersion("absent"));
        assertNull(service.getSchemaByNameAndVersion("absent", 4));

        service.saveSchema(schema("order", 1), false);
        assertNull(service.getSchemaByNameAndVersion("order", 9));
    }

    /** The dispatcher is the one lookup that still refuses an unregistered version outright. */
    @Test
    void getSchemasDispatchesOnWhatWasAskedFor() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);
        service.saveSchema(schema("payment", 1), false);

        assertEquals(3, service.getSchemas(null, null).size());
        assertEquals(2, service.getSchemas("order", null).size());
        assertEquals(2, service.getSchemas("order", 2).get(0).getVersion());

        NotFoundException notFound =
                assertThrows(NotFoundException.class, () -> service.getSchemas("order", 9));
        assertTrue(notFound.getMessage().contains("order"));
        assertTrue(notFound.getMessage().contains("9"));
    }

    @Test
    void versionsOfANameComeBackNewestFirst() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 3), false);
        service.saveSchema(schema("order", 2), false);

        assertEquals(
                List.of(3, 2, 1),
                service.getSchemasByName("order").stream().map(SchemaDef::getVersion).toList());
        assertEquals(List.of(), service.getSchemasByName("absent"));
    }

    /** The shortened listing names what is registered and carries no document. */
    @Test
    void shortenedSchemasCarryNameAndVersionOnly() {
        service.saveSchema(schema("order", 1), false);

        List<SchemaDef> shortened = service.getAllShortenedSchemas();

        assertEquals(1, shortened.size());
        assertEquals("order", shortened.get(0).getName());
        assertEquals(1, shortened.get(0).getVersion());
        assertNull(shortened.get(0).getData());
        assertNull(shortened.get(0).getType());
    }

    @Test
    void batchDeleteRemovesEveryVersionOfEveryNameGiven() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);
        service.saveSchema(schema("payment", 1), false);
        service.saveSchema(schema("refund", 1), false);

        service.deleteSchemasByNamesBatch(List.of("order", "payment", "never-registered"));

        assertEquals(1, service.getAllSchemas().size());
        assertEquals("refund", service.getAllSchemas().get(0).getName());
    }

    /**
     * A delete that names one schema reports when there is nothing to remove, rather than answering
     * as though it had removed something.
     */
    @Test
    void deletingSomethingUnregisteredIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.deleteSchemaByName("absent"));
        assertThrows(
                NotFoundException.class, () -> service.deleteSchemaByNameAndVersion("absent", 1));

        service.saveSchema(schema("order", 1), false);

        assertThrows(
                NotFoundException.class, () -> service.deleteSchemaByNameAndVersion("order", 9));
        // The version that does exist is untouched by the refused delete.
        assertNotNull(service.getSchemaByNameAndVersion("order", 1));
    }

    /**
     * The batch is the exception: it takes a list, so an unregistered name in it contributes
     * nothing instead of failing every other delete alongside it.
     */
    @Test
    void batchDeleteToleratesANameThatIsNotRegistered() {
        service.saveSchema(schema("order", 1), false);

        service.deleteSchemasByNamesBatch(List.of("order", "never-registered"));

        assertEquals(0, service.getAllSchemas().size());
    }

    /** Nothing to delete is not an error, and must not be read as "delete everything". */
    @Test
    void batchDeleteOfNoNamesRemovesNothing() {
        service.saveSchema(schema("order", 1), false);

        service.deleteSchemasByNamesBatch(List.of());
        service.deleteSchemasByNamesBatch(null);

        assertEquals(1, service.getAllSchemas().size());
    }

    @Test
    void deletingOneVersionLeavesTheRestOfTheHistory() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);

        service.deleteSchemaByNameAndVersion("order", 2);

        assertEquals(1, service.getSchemaByNameWithLatestVersion("order").getVersion());
        assertNull(service.getSchemaByNameAndVersion("order", 2));
    }

    @Test
    void deletingByNameRemovesEveryVersion() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);
        service.saveSchema(schema("payment", 1), false);

        service.deleteSchemaByName("order");

        assertEquals(1, service.getAllSchemas().size());
        assertEquals("payment", service.getAllSchemas().get(0).getName());
    }

    /**
     * Both timestamps are stamped on creation, matching Conductor's commercial build so this
     * service can stand in for it. A newly registered schema therefore reports an update time equal
     * to its create time rather than none at all.
     */
    @Test
    void creatingStampsBothTimestamps() {
        SchemaDef saved = service.saveSchema(schema("order", 1), false);

        assertTrue(saved.getCreateTime() > 0);
        assertTrue(saved.getUpdateTime() > 0);
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

    /** A new version is a fresh row: it does not inherit the previous version's timestamps. */
    @Test
    void aNewVersionGetsItsOwnTimestamps() {
        SchemaDef first = schema("order", 1);
        first.setCreateTime(1L);
        first.setUpdateTime(1L);
        service.saveSchema(first, false);

        SchemaDef second = service.saveSchema(schema("order", 0), true);

        assertEquals(2, second.getVersion());
        assertTrue(second.getCreateTime() > 1L);
        assertTrue(second.getUpdateTime() > 1L);
    }

    /**
     * Naming a version nothing occupies registers that version, rather than allocating the next one
     * — {@code newVersion=true} only increments when the named version is already taken. This is
     * the commercial build's behaviour, kept so the two agree.
     */
    @Test
    void newVersionTrueOnAnUnoccupiedVersionRegistersThatVersion() {
        service.saveSchema(schema("order", 1), false);
        service.saveSchema(schema("order", 2), false);

        SchemaDef saved = service.saveSchema(schema("order", 9), true);

        assertEquals(9, saved.getVersion());
        assertNotNull(service.getSchemaByNameAndVersion("order", 9));
        assertNull(service.getSchemaByNameAndVersion("order", 3));
    }

    /**
     * An in-place save replaces the document and leaves the registered type alone, as the
     * commercial build does. Changing a schema's type needs a new version.
     */
    @Test
    void anInPlaceSaveDoesNotChangeTheRegisteredType() {
        service.saveSchema(schema("order", 1), false);

        SchemaDef retyped = schema("order", 1);
        retyped.setType(SchemaDef.Type.AVRO);
        retyped.setData(Map.of("type", "array"));
        service.saveSchema(retyped, false);

        SchemaDef stored = service.getSchemaByNameAndVersion("order", 1);
        assertEquals(SchemaDef.Type.JSON, stored.getType());
        assertEquals(Map.of("type", "array"), stored.getData());
    }

    /** A save naming no version registers version 1, not version 0. */
    @Test
    void aSaveWithNoVersionRegistersVersion1() {
        SchemaDef noVersion = new SchemaDef();
        noVersion.setName("order");
        noVersion.setType(SchemaDef.Type.JSON);
        noVersion.setData(Map.of("type", "object"));

        assertEquals(0, noVersion.getVersion());
        assertEquals(1, service.saveSchema(noVersion, false).getVersion());
    }

    /** Every field the caller sent survives a save, externalRef included. */
    @Test
    void externalRefSurvivesASave() {
        SchemaDef withRef = schema("order", 1);
        withRef.setExternalRef("registry://order");

        service.saveSchema(withRef, false);

        assertEquals(
                "registry://order", service.getSchemaByNameAndVersion("order", 1).getExternalRef());
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
        assertEquals(
                Map.of("type", "object"), service.getSchemaByNameAndVersion("order", 1).getData());

        SchemaDef corrected = schema("order", 1);
        corrected.setData(Map.of("type", "array"));
        service.saveSchema(corrected, false);

        assertEquals(
                Map.of("type", "array"), service.getSchemaByNameAndVersion("order", 1).getData());
        assertEquals(
                Map.of("type", "array"),
                service.getSchemaByNameWithLatestVersion("order").getData());
    }

    @Test
    void cachedLatestIsDroppedWhenANewVersionArrives() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        service.saveSchema(schema("order", 1), false);
        assertEquals(1, service.getSchemaByNameWithLatestVersion("order").getVersion());

        service.saveSchema(schema("order", 0), true);

        assertEquals(2, service.getSchemaByNameWithLatestVersion("order").getVersion());
    }

    @Test
    void cachedEntryIsDroppedOnDelete() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        service.saveSchema(schema("order", 1), false);
        assertNotNull(service.getSchemaByNameAndVersion("order", 1));

        service.deleteSchemaByNameAndVersion("order", 1);

        assertNull(service.getSchemaByNameAndVersion("order", 1));
    }

    @Test
    void aMissingSchemaIsNotCachedAsMissing() {
        cacheProperties.setTtl(Duration.ofMinutes(5));
        service = newService();

        assertNull(service.getSchemaByNameAndVersion("order", 1));

        service.saveSchema(schema("order", 1), false);

        assertNotNull(service.getSchemaByNameAndVersion("order", 1));
    }
}
