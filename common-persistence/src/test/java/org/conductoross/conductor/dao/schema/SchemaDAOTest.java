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
package org.conductoross.conductor.dao.schema;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.conductoross.conductor.service.SchemaService;
import org.conductoross.conductor.service.SchemaServiceImpl;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The behaviour every {@link SchemaDAO} implementation owes its callers, run once per backend.
 *
 * <p>Each test names its own schemas, so the suite makes no assumption about the store being empty
 * and two backends' suites cannot interfere when they share a container.
 */
public abstract class SchemaDAOTest {

    /** The DAO under test. */
    protected abstract SchemaDAO getSchemaDAO();

    /**
     * A DAO over a <em>newly opened</em> connection to the same store, standing in for the server
     * coming back up. Implementations must open a fresh connection or pool rather than hand back
     * the one the DAO under test already holds — otherwise the test proves only that the row
     * outlived a Java object.
     */
    protected abstract SchemaDAO reopenStore();

    private static String uniqueName() {
        return "schema_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static SchemaDef schema(String name, int version) {
        SchemaDef def = new SchemaDef();
        def.setName(name);
        def.setVersion(version);
        def.setType(SchemaDef.Type.JSON);
        def.setData(Map.of("type", "object", "properties", Map.of("id", Map.of("type", "string"))));
        return def;
    }

    @Test
    public void savedSchemaComesBackByNameAndVersion() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));

        SchemaDef found = getSchemaDAO().findByNameAndVersion(name, 1);

        assertNotNull(found);
        assertEquals(name, found.getName());
        assertEquals(1, found.getVersion());
        assertEquals(SchemaDef.Type.JSON, found.getType());
        assertEquals(schema(name, 1).getData(), found.getData());
    }

    /**
     * The compiler stopped checking this half when the finders started returning null, so what each
     * backend does on a miss is pinned here: null, rather than an exception or an empty schema.
     */
    @Test
    public void missingSchemaIsNull() {
        assertNull(getSchemaDAO().findByNameAndVersion(uniqueName(), 1));
        assertNull(getSchemaDAO().findLatestVersionByName(uniqueName()));
    }

    @Test
    public void everySchemaTypeIsStored() {
        for (SchemaDef.Type type : SchemaDef.Type.values()) {
            String name = uniqueName();
            SchemaDef def = schema(name, 1);
            def.setType(type);
            getSchemaDAO().save(def);

            assertEquals(type, getSchemaDAO().findByNameAndVersion(name, 1).getType());
        }
    }

    @Test
    public void externalRefRoundTripsUnresolved() {
        String name = uniqueName();
        SchemaDef def = schema(name, 1);
        def.setType(SchemaDef.Type.AVRO);
        def.setExternalRef("registry://" + name);
        def.setData(null);
        getSchemaDAO().save(def);

        SchemaDef found = getSchemaDAO().findByNameAndVersion(name, 1);

        assertEquals("registry://" + name, found.getExternalRef());
    }

    @Test
    public void savingTheSameVersionOverwritesInPlace() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));

        SchemaDef corrected = schema(name, 1);
        corrected.setData(Map.of("type", "array"));
        getSchemaDAO().save(corrected);

        assertEquals(
                Map.of("type", "array"), getSchemaDAO().findByNameAndVersion(name, 1).getData());
        assertEquals(1, schemasNamed(name).size());
    }

    @Test
    public void latestIsTheHighestVersionRatherThanTheLastWritten() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 10));
        getSchemaDAO().save(schema(name, 2));

        assertEquals(10, getSchemaDAO().findLatestVersionByName(name).getVersion());
    }

    @Test
    public void conditionalInsertSucceedsOnceAndRefusesTheSecondTime() {
        String name = uniqueName();

        assertTrue(getSchemaDAO().createSchemaIfAbsent(schema(name, 1)));

        SchemaDef loser = schema(name, 1);
        loser.setData(Map.of("type", "array"));
        assertFalse(getSchemaDAO().createSchemaIfAbsent(loser));

        // The refused insert must leave the stored schema untouched, not partially applied.
        assertEquals(
                schema(name, 1).getData(), getSchemaDAO().findByNameAndVersion(name, 1).getData());
    }

    /**
     * Redis stores an opaque value under a field and has no column constraints to violate, so it
     * has no way to reject a malformed row and this expectation does not apply to it.
     */
    protected boolean rejectsMalformedRows() {
        return true;
    }

    @Test
    public void conditionalInsertReportsARealFailureRatherThanALostRace() {
        assumeTrue(rejectsMalformedRows());
        // A null name violates NOT NULL. Returning false would tell the caller some other writer
        // took the version, sending it round the allocation loop and reporting a conflict that
        // never happened; the underlying error has to reach the caller instead.
        SchemaDef malformed = schema(null, 1);

        assertThrows(
                RuntimeException.class,
                () -> getSchemaDAO().createSchemaIfAbsent(malformed),
                "a constraint violation must surface as an error, not as a lost version race");
    }

    @Test
    public void conditionalInsertAcceptsANewVersionOfAKnownName() {
        String name = uniqueName();
        assertTrue(getSchemaDAO().createSchemaIfAbsent(schema(name, 1)));

        assertTrue(getSchemaDAO().createSchemaIfAbsent(schema(name, 2)));

        assertEquals(2, schemasNamed(name).size());
    }

    /**
     * Highest version first, which is the opposite of {@link SchemaDAO#getAll()}. The first element
     * being the latest is the part a caller is most likely to rely on.
     */
    @Test
    public void everyVersionOfOneNameComesBackNewestFirst() {
        String name = uniqueName();
        String neighbour = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 10));
        getSchemaDAO().save(schema(name, 2));
        getSchemaDAO().save(schema(neighbour, 5));

        List<SchemaDef> versions = getSchemaDAO().findAllVersionsByName(name);

        assertEquals(List.of(10, 2, 1), versions.stream().map(SchemaDef::getVersion).toList());
        assertTrue(versions.stream().allMatch(def -> def.getName().equals(name)));
    }

    @Test
    public void anUnknownNameHasNoVersions() {
        assertTrue(getSchemaDAO().findAllVersionsByName(uniqueName()).isEmpty());
    }

    @Test
    public void allSchemasCarriesEveryVersionInOrder() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 3));
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));

        assertEquals(
                List.of(1, 2, 3), schemasNamed(name).stream().map(SchemaDef::getVersion).toList());
    }

    /** Name and version only: enough to identify a schema, none of its body. */
    @Test
    public void theShortenedListingCarriesNoSchemaBody() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));

        List<SchemaDef> shortened =
                getSchemaDAO().getAllShortenedSchemas().stream()
                        .filter(def -> name.equals(def.getName()))
                        .toList();

        assertEquals(List.of(1, 2), shortened.stream().map(SchemaDef::getVersion).toList());
        assertTrue(shortened.stream().allMatch(def -> def.getData() == null));
        assertTrue(shortened.stream().allMatch(def -> def.getType() == null));
        assertTrue(shortened.stream().allMatch(def -> def.getExternalRef() == null));
    }

    @Test
    public void deletingSeveralNamesRemovesEveryVersionOfEach() {
        String first = uniqueName();
        String second = uniqueName();
        String survivor = uniqueName();
        getSchemaDAO().save(schema(first, 1));
        getSchemaDAO().save(schema(first, 2));
        getSchemaDAO().save(schema(second, 1));
        getSchemaDAO().save(schema(survivor, 1));

        assertEquals(3, getSchemaDAO().deleteAllByNames(List.of(first, second)));

        assertTrue(schemasNamed(first).isEmpty());
        assertTrue(schemasNamed(second).isEmpty());
        assertEquals(1, schemasNamed(survivor).size());
    }

    @Test
    public void deletingNoNamesRemovesNothing() {
        assertEquals(0, getSchemaDAO().deleteAllByNames(List.of()));
        assertEquals(0, getSchemaDAO().deleteAllByNames(null));
    }

    @Test
    public void deletingOneVersionLeavesTheRest() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));

        assertEquals(1, getSchemaDAO().deleteByNameAndVersion(name, 1));

        assertNull(getSchemaDAO().findByNameAndVersion(name, 1));
        assertNotNull(getSchemaDAO().findByNameAndVersion(name, 2));
        assertEquals(2, getSchemaDAO().findLatestVersionByName(name).getVersion());
    }

    @Test
    public void deletingByNameRemovesEveryVersion() {
        String name = uniqueName();
        String survivor = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));
        getSchemaDAO().save(schema(survivor, 1));

        assertEquals(2, getSchemaDAO().deleteAllByName(name));

        assertTrue(schemasNamed(name).isEmpty());
        assertNull(getSchemaDAO().findLatestVersionByName(name));
        assertEquals(1, schemasNamed(survivor).size());
    }

    /**
     * A delete of something that is not there removes nothing and says so. The count is what a
     * caller would use to tell a missing schema from a deleted one, so a backend reporting a
     * removal it did not make would be reporting a schema that never existed.
     */
    @Test
    public void deletingSomethingAbsentRemovesNothing() {
        String name = uniqueName();

        assertEquals(0, getSchemaDAO().deleteByNameAndVersion(name, 1));
        assertEquals(0, getSchemaDAO().deleteAllByName(name));

        assertTrue(schemasNamed(name).isEmpty());
    }

    /** A null version reaches the driver as an unhelpful failure, so it is refused at the seam. */
    @Test
    public void aNullVersionIsRefused() {
        String name = uniqueName();

        assertThrows(
                NullPointerException.class, () -> getSchemaDAO().findByNameAndVersion(name, null));
        assertThrows(
                NullPointerException.class,
                () -> getSchemaDAO().deleteByNameAndVersion(name, null));
    }

    /**
     * The versioning race, run against the real store rather than asserted about it.
     *
     * <p>Driven through {@link SchemaServiceImpl} because allocation is split across the two: the
     * service reads the current maximum and retries, the DAO decides the race in the database.
     * Testing either half alone would miss the seam between them. The end-to-end versioning journey
     * that would otherwise cover this arrives with the HTTP surface, so it is caught here.
     */
    @Test
    public void concurrentVersionCreatingSavesAllSurviveWithDistinctVersions() throws Exception {
        String name = uniqueName();
        SchemaService service =
                new SchemaServiceImpl(
                        getSchemaDAO(),
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));

        int writers = 4;
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<SchemaDef>> saves = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                saves.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return service.saveSchema(schema(name, 0), true);
                                }));
            }
            start.countDown();

            Set<Integer> allocated = new HashSet<>();
            for (Future<SchemaDef> save : saves) {
                allocated.add(save.get(60, TimeUnit.SECONDS).getVersion());
            }

            assertEquals(writers, allocated.size(), "every writer must get a version of its own");
            // And every one of them is actually in the store, so none was overwritten by a later
            // winner after being reported as saved.
            assertEquals(writers, schemasNamed(name).size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void aSchemaSurvivesAReopenedStore() {
        String name = uniqueName();
        SchemaDef def = schema(name, 4);
        def.setExternalRef("registry://" + name);
        getSchemaDAO().save(def);

        SchemaDef reread = reopenStore().findByNameAndVersion(name, 4);

        assertEquals(name, reread.getName());
        assertEquals(4, reread.getVersion());
        assertEquals(def.getType(), reread.getType());
        assertEquals(def.getData(), reread.getData());
        assertEquals(def.getExternalRef(), reread.getExternalRef());
    }

    private List<SchemaDef> schemasNamed(String name) {
        return getSchemaDAO().getAll().stream().filter(def -> def.getName().equals(name)).toList();
    }
}
