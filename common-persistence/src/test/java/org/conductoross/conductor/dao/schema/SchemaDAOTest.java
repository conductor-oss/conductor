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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for {@link SchemaDAO} implementations. Each test generates its own schema names
 * so the suite is safe to run against a shared container.
 */
public abstract class SchemaDAOTest {

    /** The DAO under test. */
    protected abstract SchemaDAO getSchemaDAO();

    /**
     * A DAO over a freshly opened connection to the same store. Must not reuse the connection from
     * {@link #getSchemaDAO()} — the test verifies persistence across a connection boundary.
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
    public void allSchemasCarriesEveryVersionInOrder() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 3));
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));

        assertEquals(
                List.of(1, 2, 3), schemasNamed(name).stream().map(SchemaDef::getVersion).toList());
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

    /** Delete of a missing schema returns 0, not an exception. */
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

    @Test
    public void everyVersionOfANameComesBackNewestFirst() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 3));
        getSchemaDAO().save(schema(name, 2));
        getSchemaDAO().save(schema(uniqueName(), 1));

        List<SchemaDef> versions = getSchemaDAO().findAllVersionsByName(name);

        assertEquals(List.of(3, 2, 1), versions.stream().map(SchemaDef::getVersion).toList());
        assertEquals(name, versions.get(0).getName());
    }

    /** An unknown name has no versions, and says so with an empty list rather than a null. */
    @Test
    public void aNameWithNoVersionsHasNone() {
        assertEquals(List.of(), getSchemaDAO().findAllVersionsByName(uniqueName()));
    }

    @Test
    public void deletingManyNamesRemovesEveryVersionOfEach() {
        String first = uniqueName();
        String second = uniqueName();
        String survivor = uniqueName();
        getSchemaDAO().save(schema(first, 1));
        getSchemaDAO().save(schema(first, 2));
        getSchemaDAO().save(schema(second, 1));
        getSchemaDAO().save(schema(survivor, 1));

        // Count is versions removed; an unknown name contributes 0.
        assertEquals(3, getSchemaDAO().deleteAllByNames(List.of(first, second, uniqueName())));

        assertTrue(schemasNamed(first).isEmpty());
        assertTrue(schemasNamed(second).isEmpty());
        assertEquals(1, schemasNamed(survivor).size());
    }

    /** Nothing to delete is not an error, and must not be read as "delete everything". */
    @Test
    public void deletingNoNamesRemovesNothing() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));

        assertEquals(0, getSchemaDAO().deleteAllByNames(List.of()));
        assertEquals(0, getSchemaDAO().deleteAllByNames(null));

        assertEquals(1, schemasNamed(name).size());
    }

    /** The shortened listing returns name and version only — no type, data, or externalRef. */
    @Test
    public void shortenedSchemasCarryNameAndVersionOnly() {
        String name = uniqueName();
        getSchemaDAO().save(schema(name, 1));
        getSchemaDAO().save(schema(name, 2));

        List<SchemaDef> shortened =
                getSchemaDAO().getAllShortenedSchemas().stream()
                        .filter(def -> name.equals(def.getName()))
                        .toList();

        assertEquals(List.of(1, 2), shortened.stream().map(SchemaDef::getVersion).toList());
        for (SchemaDef def : shortened) {
            assertNull(def.getType());
            assertNull(def.getData());
            assertNull(def.getExternalRef());
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
