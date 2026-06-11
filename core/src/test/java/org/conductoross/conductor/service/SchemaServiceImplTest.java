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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.dao.InMemorySchemaDAO;
import org.junit.Test;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.exception.NotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class SchemaServiceImplTest {

    @Test
    public void saveCreatesNewVersionsAndReturnsShortSchemas() {
        WorkflowContext.set(new WorkflowContext("test-app"));
        SchemaService service = new SchemaServiceImpl(new InMemorySchemaDAO());

        SchemaDef schema = schema("customer", 1, "first");
        service.save(schema, false);

        schema.setData(new HashMap<>(Map.of("title", "updated")));
        service.save(schema, true);

        SchemaDef latest = service.getSchemaByNameWithLatestVersion("customer");
        assertEquals(2, latest.getVersion());
        assertEquals("updated", latest.getData().get("title"));
        assertEquals("test-app", latest.getCreatedBy());

        List<SchemaDef> shortSchemas = service.getAllSchemas(true);
        assertEquals(2, shortSchemas.size());
        assertNull(shortSchemas.get(0).getData());
    }

    @Test
    public void missingSchemaThrowsNotFound() {
        SchemaService service = new SchemaServiceImpl(new InMemorySchemaDAO());

        assertThrows(
                NotFoundException.class,
                () -> service.getSchemaByNameAndVersion("missing", 1));
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
