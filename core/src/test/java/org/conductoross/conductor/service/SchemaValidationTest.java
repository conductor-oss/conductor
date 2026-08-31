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

import java.util.Map;

import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.core.exception.SchemaValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry's one validation entry point. Both the engine's enforcement hooks and the AI layer
 * come through here, so resolution, the null-type check, the registry miss and the non-JSON refusal
 * are asserted once, against the thing that owns them.
 */
class SchemaValidationTest {

    private static final Map<String, Object> PERSON =
            Map.of(
                    "$schema",
                    "https://json-schema.org/draft/2020-12/schema",
                    "type",
                    "object",
                    "properties",
                    Map.of("name", Map.of("type", "string")),
                    "required",
                    java.util.List.of("name"));

    private InMemorySchemaDAO dao;
    private SchemaServiceImpl service;

    @BeforeEach
    void setUp() {
        dao = new InMemorySchemaDAO();
        service =
                new SchemaServiceImpl(
                        dao,
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
    }

    private static SchemaDef inline() {
        SchemaDef def = new SchemaDef();
        def.setName("person");
        def.setVersion(1);
        def.setType(SchemaDef.Type.JSON);
        def.setData(PERSON);
        return def;
    }

    /** What a definition carries when it points at the registry instead of inlining a schema. */
    private static SchemaDef reference(String name, int version) {
        SchemaDef def = new SchemaDef();
        def.setName(name);
        def.setVersion(version);
        return def;
    }

    @Test
    void conformingDataPasses() {
        assertDoesNotThrow(() -> service.validate(inline(), Map.of("name", "ada")));
    }

    @Test
    void nonConformingDataFailsAndNamesWhatFailed() {
        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(inline(), Map.of("nickname", "ada")));

        assertTrue(
                thrown.getMessage().contains("name"),
                "the message must name the field that failed: " + thrown.getMessage());
    }

    @Test
    void aReferenceResolvesAgainstTheRegistry() {
        service.saveSchema(inline(), false);

        assertDoesNotThrow(() -> service.validate(reference("person", 1), Map.of("name", "ada")));
        assertThrows(
                SchemaValidationException.class,
                () -> service.validate(reference("person", 1), Map.of()));
    }

    @Test
    void aReferenceWithoutAVersionResolvesTheLatest() {
        SchemaDef v1 = inline();
        v1.setData(Map.of("type", "object"));
        service.saveSchema(v1, false);
        service.saveSchema(inline(), true);

        // Version 2 is the one that requires `name`; resolving v1 instead would pass.
        assertThrows(
                SchemaValidationException.class,
                () -> service.validate(reference("person", 0), Map.of()));
    }

    @Test
    void inlineDataWinsOverTheRegistry() {
        SchemaDef registered = inline();
        registered.setData(Map.of("type", "object", "required", java.util.List.of("absent")));
        service.saveSchema(registered, false);

        assertDoesNotThrow(() -> service.validate(inline(), Map.of("name", "ada")));
    }

    @Test
    void anUnresolvableReferenceFailsLoudlyNamingTheSchemaAndVersion() {
        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(reference("person", 7), Map.of("name", "ada")));

        assertTrue(thrown.getMessage().contains("person"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("7"), thrown.getMessage());
    }

    @Test
    void aSchemaWithNoTypeFails() {
        SchemaDef untyped = inline();
        untyped.setType(null);

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(untyped, Map.of("name", "ada")));

        assertTrue(thrown.getMessage().contains("person"), thrown.getMessage());
    }

    @Test
    void aNonJsonSchemaFailsRatherThanPassingUnvalidated() {
        SchemaDef avro = inline();
        avro.setType(SchemaDef.Type.AVRO);

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(avro, Map.of("name", "ada")));

        assertTrue(thrown.getMessage().contains("AVRO"), thrown.getMessage());
    }

    @Test
    void anExternalRefIsNotResolved() {
        SchemaDef external = new SchemaDef();
        external.setName("person");
        external.setVersion(1);
        external.setType(SchemaDef.Type.JSON);
        external.setExternalRef("registry://person");

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(external, Map.of("name", "ada")));

        assertTrue(thrown.getMessage().contains("person"), thrown.getMessage());
    }

    /** A document that constrains nothing is still a document: it permits anything. */
    @Test
    void anInlineDocumentThatConstrainsNothingPermitsAnything() {
        SchemaDef permissive = inline();
        permissive.setData(Map.of("$schema", "https://json-schema.org/draft/2020-12/schema"));

        assertDoesNotThrow(() -> service.validate(permissive, Map.of("anything", 1)));
    }

    /**
     * Carrying {@code data} is what makes a schema inline, not carrying a non-empty one. An empty
     * document is unusable on this server — the validator needs a {@code $schema} tag — but it must
     * fail as the unusable document it is, rather than quietly resolving to whatever the registry
     * happens to hold under the same name.
     */
    @Test
    void anEmptyInlineDocumentIsNotSilentlyReplacedByTheRegistry() {
        service.saveSchema(inline(), false);
        SchemaDef empty = inline();
        empty.setData(Map.of());

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(empty, Map.of("name", "ada")));

        assertTrue(
                thrown.getMessage().contains("not a usable JSON schema"),
                "the empty document is what failed, not the registered schema: "
                        + thrown.getMessage());
    }

    /**
     * A schema with neither a document nor a name cannot be resolved. It has to fail as a schema
     * failure like any other: every caller catches {@link SchemaValidationException} and nothing
     * else, so anything else escapes as an unhandled server fault.
     */
    @Test
    void aSchemaWithNeitherADocumentNorANameFailsAsAValidationFailure() {
        SchemaDef nameless = new SchemaDef();
        nameless.setType(SchemaDef.Type.JSON);

        assertThrows(
                SchemaValidationException.class,
                () -> service.validate(nameless, Map.of("name", "ada")));
    }

    @Test
    void aMalformedSchemaDocumentFailsWithAMessage() {
        SchemaDef malformed = inline();
        malformed.setData(Map.of("type", 7));

        SchemaValidationException thrown =
                assertThrows(
                        SchemaValidationException.class,
                        () -> service.validate(malformed, Map.of("name", "ada")));

        assertTrue(
                thrown.getMessage() != null && !thrown.getMessage().isBlank(),
                "a bad schema must not fail with an empty message");
    }
}
