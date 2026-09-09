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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which version of a registered schema a reference resolves to.
 *
 * <p>Three versions are registered under one name, each requiring a differently named field. That
 * makes the version actually applied observable from the outside: a payload carrying only {@code
 * three} conforms to version 3 and to neither of the others, so a passing validation identifies the
 * version as surely as a failing one does. Asserting a version number would only restate the
 * fixture; asserting which payloads pass shows which document was applied.
 */
class SchemaVersionResolutionTest {

    private static final String NAME = "order";

    private SchemaService service;

    @BeforeEach
    void setUp() {
        service =
                new SchemaService(
                        new InMemorySchemaDAO(),
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));

        // Registered lowest first, so version 3 is both the highest number and the last write.
        // The two ways "latest" could be read agree here, which keeps the fixture out of the
        // argument; SchemaServiceTest covers a registry where they disagree.
        register(1, "one");
        register(2, "two");
        register(3, "three");
    }

    /** Registers version {@code version} of {@link #NAME}, requiring exactly {@code field}. */
    private void register(int version, String field) {
        SchemaDef schema = new SchemaDef();
        schema.setName(NAME);
        schema.setVersion(version);
        schema.setType(SchemaDef.Type.JSON);
        schema.setData(
                Map.of(
                        "$schema",
                        "https://json-schema.org/draft/2020-12/schema",
                        "type",
                        "object",
                        "properties",
                        Map.of(field, Map.of("type", "string")),
                        "required",
                        List.of(field)));
        service.saveSchema(schema, false);
    }

    /**
     * What a workflow or task definition carries when it points at the registry rather than
     * inlining a document: a name, a version, and no {@code data}.
     */
    private static SchemaDef reference(int version) {
        SchemaDef def = new SchemaDef();
        def.setName(NAME);
        def.setVersion(version);
        return def;
    }

    /** Asserts the document applied was the one requiring {@code field}, and no other. */
    private void assertResolvedTo(SchemaDef reference, String field) {
        assertDoesNotThrow(
                () -> service.validate(reference, Map.of(field, "x")),
                "the payload matching the expected version must pass");

        for (String other : List.of("one", "two", "three")) {
            if (other.equals(field)) {
                continue;
            }
            SchemaValidationException thrown =
                    assertThrows(
                            SchemaValidationException.class,
                            () -> service.validate(reference, Map.of(other, "x")),
                            "a payload written for a different version must not pass");
            assertTrue(
                    thrown.getMessage().contains(field),
                    "the failure must name the field the resolved version requires, so the "
                            + "version in force is visible in the message: "
                            + thrown.getMessage());
        }
    }

    @Test
    void aReferenceToVersion3ValidatesAgainstVersion3() {
        assertResolvedTo(reference(3), "three");
    }

    @Test
    void aReferenceToVersion2ValidatesAgainstVersion2() {
        assertResolvedTo(reference(2), "two");
    }

    @Test
    void aReferenceToVersion1ValidatesAgainstVersion1() {
        assertResolvedTo(reference(1), "one");
    }

    /** Zero is the explicit spelling of "whichever is newest", and the field's default. */
    @Test
    void aReferenceWithVersion0ResolvesTheLatest() {
        assertResolvedTo(reference(0), "three");
    }

    /** A newly registered version is picked up by an existing latest-resolving reference. */
    @Test
    void theLatestFollowsTheRegistryForward() {
        SchemaDef latest = reference(0);
        assertResolvedTo(latest, "three");

        register(4, "four");

        assertResolvedTo(latest, "four");
    }

    /**
     * Leaving the version off resolves the latest. {@link SchemaDef}'s {@code version} field
     * defaults to 0, and {@code SchemaService} reads anything below 1 as "whichever is newest", so
     * a reference written without a version follows the registry forward rather than pinning the
     * oldest.
     */
    @Test
    void anOmittedVersionResolvesTheLatest() {
        SchemaDef omitted = new SchemaDef();
        omitted.setName(NAME);

        assertEquals(0, omitted.getVersion(), "the version field defaults to 0, meaning latest");
        assertResolvedTo(omitted, "three");
    }

    /**
     * The builder leaves it at the same default, so a built reference also follows the registry.
     */
    @Test
    void aBuiltReferenceWithNoVersionResolvesTheLatest() {
        SchemaDef built = SchemaDef.builder().name(NAME).build();

        assertEquals(0, built.getVersion());
        assertResolvedTo(built, "three");
    }

    /** The same, arriving as JSON rather than built in code -- a definition posted over REST. */
    @Test
    void aVersionOmittedFromJsonAlsoResolvesTheLatest() throws Exception {
        String json =
                "{\n"
                        + "  \"name\": \"orders\",\n"
                        + "  \"version\": 1,\n"
                        + "  \"schemaVersion\": 2,\n"
                        + "  \"enforceSchema\": true,\n"
                        + "  \"inputSchema\": { \"name\": \"order\", \"type\": \"JSON\" }\n"
                        + "}";
        WorkflowDef def =
                new ObjectMapperProvider().getObjectMapper().readValue(json, WorkflowDef.class);

        assertEquals(
                0,
                def.getInputSchema().getVersion(),
                "a JSON reference with no version deserialises to 0, meaning latest");
        assertResolvedTo(def.getInputSchema(), "three");
    }
}
