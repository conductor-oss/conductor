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
package com.netflix.conductor.mysql.dao;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.mysql.config.MySQLConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            MySQLConfiguration.class,
            FlywayAutoConfiguration.class
        })
@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
public class MySQLSchemaDAOTest {

    private static final String ORG_ID = "default";

    @Autowired private MySQLSchemaDAO schemaDAO;

    @Autowired private Flyway flyway;

    @Before
    public void before() {
        flyway.clean();
        flyway.migrate();
    }

    @Test
    public void testSchemaOperations() {
        String name = "schema_" + UUID.randomUUID();
        SchemaDef schema = schema(name, 1, "first");

        schemaDAO.save(ORG_ID, schema);

        SchemaDef found = schemaDAO.getSchemaByNameAndVersion(ORG_ID, name, 1).get();
        assertEquals(schema.getName(), found.getName());
        assertEquals(schema.getVersion(), found.getVersion());
        assertEquals(schema.getType(), found.getType());
        assertEquals("first", found.getData().get("title"));

        schema.setData(new HashMap<>(Map.of("title", "updated")));
        schemaDAO.save(ORG_ID, schema);
        found = schemaDAO.getSchemaByNameAndVersion(ORG_ID, name, 1).get();
        assertEquals("updated", found.getData().get("title"));

        schemaDAO.save(ORG_ID, schema(name, 2, "second"));
        assertEquals(2, (int) schemaDAO.getLatestVersion(ORG_ID, name).get());
        assertEquals(
                2, schemaDAO.getSchemaByNameWithLatestVersion(ORG_ID, name).get().getVersion());

        List<SchemaDef> all = schemaDAO.getAllSchemas(ORG_ID).stream()
                .filter(def -> def.getName().equals(name))
                .toList();
        assertEquals(2, all.size());

        schemaDAO.deleteSchemaByNameAndVersion(ORG_ID, name, 2);
        assertFalse(schemaDAO.getSchemaByNameAndVersion(ORG_ID, name, 2).isPresent());
        assertEquals(
                1, schemaDAO.getSchemaByNameWithLatestVersion(ORG_ID, name).get().getVersion());

        schemaDAO.deleteSchemaByName(ORG_ID, name);
        assertFalse(schemaDAO.getSchemaByNameWithLatestVersion(ORG_ID, name).isPresent());
    }

    @Test
    public void testSchemasAreScopedByOrg() {
        String name = "schema_" + UUID.randomUUID();
        schemaDAO.save(ORG_ID, schema(name, 1, "default"));
        schemaDAO.save("other", schema(name, 1, "other"));

        assertEquals(
                "default",
                schemaDAO.getSchemaByNameWithLatestVersion(ORG_ID, name)
                        .get()
                        .getData()
                        .get("title"));
        assertEquals(
                "other",
                schemaDAO.getSchemaByNameWithLatestVersion("other", name)
                        .get()
                        .getData()
                        .get("title"));
        assertTrue(schemaDAO.getAllSchemas("missing").isEmpty());
    }

    private SchemaDef schema(String name, int version, String title) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        schema.setType(SchemaDef.Type.JSON);
        schema.setData(new HashMap<>(Map.of("title", title, "type", "object")));
        return schema;
    }
}
