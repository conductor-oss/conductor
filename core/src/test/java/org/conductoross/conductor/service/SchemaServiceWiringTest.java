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

import java.util.List;
import java.util.Map;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.conductoross.conductor.dao.schema.InMemorySchemaDAO;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Not every metadata backend implements a {@link SchemaDAO} — Cassandra does not. {@link
 * InMemorySchemaDAO} stands in for those, so the registry works everywhere; these tests exercise
 * the service over it.
 */
class SchemaServiceWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            JsonSchemaValidator.class,
                            () ->
                                    new JsonSchemaValidator(
                                            new ObjectMapperProvider().getObjectMapper()))
                    .withBean(SchemaDAO.class, InMemorySchemaDAO::new)
                    .withUserConfiguration(SchemaService.class);

    @Test
    void contextStartsOverInMemoryStorage() {
        runner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(SchemaService.class));
                });
    }

    @Test
    void theRegistryReadsAsEmptyBeforeAnythingIsRegistered() {
        runner.run(
                context -> {
                    SchemaService service = context.getBean(SchemaService.class);
                    assertTrue(service.getAllSchemas().isEmpty());
                    assertTrue(service.getAllShortenedSchemas().isEmpty());
                    assertTrue(service.getSchemasByName("absent").isEmpty());
                    assertNull(service.getSchemaByNameWithLatestVersion("absent"));
                    assertNull(service.getSchemaByNameAndVersion("absent", 1));
                });
    }

    /** Writes are accepted on the fallback, and read back — for this server's lifetime. */
    @Test
    void writesAreServedFromInMemoryStorage() {
        runner.run(
                context -> {
                    SchemaService service = context.getBean(SchemaService.class);
                    service.saveSchema(requiresName(), false);

                    SchemaDef stored = service.getSchemaByNameAndVersion("requires_name", 1);
                    assertNotNull(stored);
                    assertEquals(1, service.getAllSchemas().size());

                    service.deleteSchemaByNameAndVersion("requires_name", 1);
                    assertTrue(service.getAllSchemas().isEmpty());
                });
    }

    /** Validation against an inline schema needs no registry at all. */
    @Test
    void inlineSchemasAreEnforcedWithoutTouchingStorage() {
        runner.run(
                context -> {
                    SchemaService service = context.getBean(SchemaService.class);
                    assertThrows(
                            SchemaValidationException.class,
                            () -> service.validate(requiresName(), Map.of("age", 42)));
                    service.validate(requiresName(), Map.of("name", "ada"));
                });
    }

    /** The service reads whatever DAO the backend contributed, not one of its own making. */
    @Test
    void theServiceReadsThroughTheContributedDao() {
        InMemorySchemaDAO backendDao = new InMemorySchemaDAO();
        backendDao.save(requiresName());

        new ApplicationContextRunner()
                .withBean(
                        JsonSchemaValidator.class,
                        () -> new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()))
                .withBean(SchemaDAO.class, () -> backendDao)
                .withUserConfiguration(SchemaService.class)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            SchemaService service = context.getBean(SchemaService.class);
                            assertSame(backendDao, context.getBean(SchemaDAO.class));
                            assertEquals(1, service.getAllSchemas().size());
                        });
    }

    /** An inline JSON schema that demands a {@code name}. */
    private static SchemaDef requiresName() {
        SchemaDef schema = new SchemaDef();
        schema.setName("requires_name");
        schema.setVersion(1);
        schema.setType(SchemaDef.Type.JSON);
        schema.setData(
                Map.of(
                        "$schema",
                        "https://json-schema.org/draft/2020-12/schema",
                        "type",
                        "object",
                        "required",
                        List.of("name")));
        return schema;
    }
}
