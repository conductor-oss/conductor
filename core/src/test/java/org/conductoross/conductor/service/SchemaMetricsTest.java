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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.metrics.Monitors;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an operator sees when enforcement rejects a payload. The tag values are the point: a counter
 * whose schema name is wrong reads as a different event, and nothing else in the build would
 * notice.
 *
 * <p>Recorded by {@link SchemaService#validate}, which is the single point every caller passes
 * through — the engine's enforcement points and the AI layer alike.
 */
class SchemaMetricsTest {

    private SimpleMeterRegistry registry;
    private SchemaService schemaService;

    @BeforeEach
    void setUp() {
        // Meters are global, so assertions are made against a registry added here and read by
        // name: a fresh registry sees only what this test records.
        registry = new SimpleMeterRegistry();
        Monitors.addMeterRegistry(registry);

        schemaService =
                new SchemaService(
                        new InMemorySchemaDAO(),
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
    }

    private static SchemaDef requiresName() {
        SchemaDef def = new SchemaDef();
        def.setName("person");
        def.setVersion(1);
        def.setType(SchemaDef.Type.JSON);
        def.setData(
                Map.of(
                        "$schema", "https://json-schema.org/draft/2020-12/schema",
                        "type", "object",
                        "required", List.of("name")));
        return def;
    }

    @Test
    void aRejectedPayloadIsCountedAgainstItsSchema() {
        assertThrows(
                SchemaValidationException.class,
                () -> schemaService.validate(requiresName(), Map.of()));

        assertEquals(
                1.0,
                registry.get("schema_validation_failure")
                        .tag("schemaName", "person")
                        .counter()
                        .count(),
                0.001);
        assertTrue(
                registry.get("schema_validation").timer().count() >= 1,
                "validating a payload is timed even when it is rejected");
    }

    /**
     * A payload that conforms is timed too, and counted against nothing.
     *
     * <p>Under its own schema name: {@code Monitors} keeps one global composite, so a counter any
     * other test created is visible here with a count of zero. Asserting on a name only this test
     * uses is what makes the assertion independent of what else ran.
     */
    @Test
    void anAcceptedPayloadIsTimedButNotCounted() {
        SchemaDef schema = requiresName();
        schema.setName("greeting");

        assertDoesNotThrow(() -> schemaService.validate(schema, Map.of("name", "ada")));

        assertTrue(registry.get("schema_validation").timer().count() >= 1);
        assertNull(
                registry.find("schema_validation_failure").tag("schemaName", "greeting").counter(),
                "a payload that conforms must not be counted as a failure");
    }

    @Test
    void aReferenceToAnUnregisteredSchemaIsCountedAsAMissRatherThanAFailure() {
        SchemaDef reference = new SchemaDef();
        reference.setName("absent");
        reference.setVersion(3);
        reference.setType(SchemaDef.Type.JSON);

        assertDoesNotThrow(() -> schemaService.validate(reference, Map.of("name", "ada")));

        assertEquals(
                1.0,
                registry.get("schema_registry_miss").tag("schemaName", "absent").counter().count(),
                0.001);
        assertNotNull(registry.find("schema_registry_miss").counter());
    }
}
