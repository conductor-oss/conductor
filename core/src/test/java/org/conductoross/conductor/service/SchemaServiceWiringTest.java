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

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry has no default storage on purpose. A backend with no {@link SchemaDAO} must stop the
 * server at startup rather than let it accept schema writes it cannot store — an in-memory fallback
 * would accept them, report success, and lose them on restart.
 */
class SchemaServiceWiringTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(
                            JsonSchemaValidator.class,
                            () ->
                                    new JsonSchemaValidator(
                                            new ObjectMapperProvider().getObjectMapper()))
                    .withUserConfiguration(SchemaServiceImpl.class);

    @Test
    void contextFailsWhenNoBackendProvidesASchemaDao() {
        runner.run(
                context -> {
                    assertNotNull(
                            context.getStartupFailure(),
                            "the context must refuse to start without a SchemaDAO");
                    assertTrue(
                            context.getStartupFailure()
                                    .getMessage()
                                    .contains(SchemaDAO.class.getName()),
                            "the failure must name the missing SchemaDAO, so an operator can tell "
                                    + "the backend is unsupported: "
                                    + context.getStartupFailure().getMessage());
                });
    }

    @Test
    void contextStartsWhenABackendProvidesOne() {
        runner.withBean(SchemaDAO.class, InMemorySchemaDAO::new)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertNotNull(context.getBean(SchemaService.class));
                        });
    }
}
