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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.conductoross.conductor.service.SchemaCacheProperties;
import org.conductoross.conductor.service.SchemaService;
import org.conductoross.conductor.service.SchemaServiceImpl;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        getSchemaDAO().saveSchema(schema(name, 1));

        Optional<SchemaDef> found = getSchemaDAO().getSchema(name, 1);

        assertTrue(found.isPresent());
        assertEquals(name, found.get().getName());
        assertEquals(1, found.get().getVersion());
        assertEquals(SchemaDef.Type.JSON, found.get().getType());
        assertEquals(schema(name, 1).getData(), found.get().getData());
    }

    @Test
    public void missingSchemaIsEmpty() {
        assertTrue(getSchemaDAO().getSchema(uniqueName(), 1).isEmpty());
        assertTrue(getSchemaDAO().getLatestSchema(uniqueName()).isEmpty());
    }

    @Test
    public void everySchemaTypeIsStored() {
        for (SchemaDef.Type type : SchemaDef.Type.values()) {
            String name = uniqueName();
            SchemaDef def = schema(name, 1);
            def.setType(type);
            getSchemaDAO().saveSchema(def);

            assertEquals(type, getSchemaDAO().getSchema(name, 1).orElseThrow().getType());
        }
    }

    @Test
    public void externalRefRoundTripsUnresolved() {
        String name = uniqueName();
        SchemaDef def = schema(name, 1);
        def.setType(SchemaDef.Type.AVRO);
        def.setExternalRef("registry://" + name);
        def.setData(null);
        getSchemaDAO().saveSchema(def);

        SchemaDef found = getSchemaDAO().getSchema(name, 1).orElseThrow();

        assertEquals("registry://" + name, found.getExternalRef());
    }

    @Test
    public void savingTheSameVersionOverwritesInPlace() {
        String name = uniqueName();
        getSchemaDAO().saveSchema(schema(name, 1));

        SchemaDef corrected = schema(name, 1);
        corrected.setData(Map.of("type", "array"));
        getSchemaDAO().saveSchema(corrected);

        assertEquals(
                Map.of("type", "array"), getSchemaDAO().getSchema(name, 1).orElseThrow().getData());
        assertEquals(1, schemasNamed(name).size());
    }

    @Test
    public void latestIsTheHighestVersionRatherThanTheLastWritten() {
        String name = uniqueName();
        getSchemaDAO().saveSchema(schema(name, 1));
        getSchemaDAO().saveSchema(schema(name, 10));
        getSchemaDAO().saveSchema(schema(name, 2));

        assertEquals(10, getSchemaDAO().getLatestSchema(name).orElseThrow().getVersion());
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
                schema(name, 1).getData(),
                getSchemaDAO().getSchema(name, 1).orElseThrow().getData());
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

    @Test
    public void allSchemasCarriesEveryVersionInOrder() {
        String name = uniqueName();
        getSchemaDAO().saveSchema(schema(name, 3));
        getSchemaDAO().saveSchema(schema(name, 1));
        getSchemaDAO().saveSchema(schema(name, 2));

        assertEquals(
                List.of(1, 2, 3), schemasNamed(name).stream().map(SchemaDef::getVersion).toList());
    }

    @Test
    public void deletingOneVersionLeavesTheRest() {
        String name = uniqueName();
        getSchemaDAO().saveSchema(schema(name, 1));
        getSchemaDAO().saveSchema(schema(name, 2));

        getSchemaDAO().deleteSchema(name, 1);

        assertTrue(getSchemaDAO().getSchema(name, 1).isEmpty());
        assertTrue(getSchemaDAO().getSchema(name, 2).isPresent());
        assertEquals(2, getSchemaDAO().getLatestSchema(name).orElseThrow().getVersion());
    }

    @Test
    public void deletingByNameRemovesEveryVersion() {
        String name = uniqueName();
        String survivor = uniqueName();
        getSchemaDAO().saveSchema(schema(name, 1));
        getSchemaDAO().saveSchema(schema(name, 2));
        getSchemaDAO().saveSchema(schema(survivor, 1));

        getSchemaDAO().deleteSchemaByName(name);

        assertTrue(schemasNamed(name).isEmpty());
        assertTrue(getSchemaDAO().getLatestSchema(name).isEmpty());
        assertEquals(1, schemasNamed(survivor).size());
    }

    @Test
    public void deletingSomethingAbsentIsANoOp() {
        String name = uniqueName();

        getSchemaDAO().deleteSchema(name, 1);
        getSchemaDAO().deleteSchemaByName(name);

        assertTrue(schemasNamed(name).isEmpty());
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
        SchemaService service = new SchemaServiceImpl(getSchemaDAO(), new SchemaCacheProperties());

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
        getSchemaDAO().saveSchema(def);

        SchemaDef reread = reopenStore().getSchema(name, 4).orElseThrow();

        assertEquals(name, reread.getName());
        assertEquals(4, reread.getVersion());
        assertEquals(def.getType(), reread.getType());
        assertEquals(def.getData(), reread.getData());
        assertEquals(def.getExternalRef(), reread.getExternalRef());
    }

    private List<SchemaDef> schemasNamed(String name) {
        return getSchemaDAO().getAllSchemas().stream()
                .filter(def -> def.getName().equals(name))
                .toList();
    }
}
