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
 * are asserted once, against the thing that owns them — including which of them refuse a payload
 * and which leave it unvalidated.
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

    /**
     * A reference carrying no version asks for the latest, and gets it: version 2 is the one that
     * requires {@code name}, and resolving version 1 instead would let this payload through.
     */
    @Test
    void aReferenceWithoutAVersionResolvesTheLatest() {
        SchemaDef v1 = inline();
        v1.setData(Map.of("type", "object"));
        service.saveSchema(v1, false);
        service.saveSchema(inline(), true);

        assertThrows(
                SchemaValidationException.class,
                () -> service.validate(reference("person", 0), Map.of()));
        // And a payload the latest version accepts still passes.
        assertDoesNotThrow(() -> service.validate(reference("person", 0), Map.of("name", "ada")));
    }

    @Test
    void inlineDataWinsOverTheRegistry() {
        SchemaDef registered = inline();
        registered.setData(Map.of("type", "object", "required", java.util.List.of("absent")));
        service.saveSchema(registered, false);

        assertDoesNotThrow(() -> service.validate(inline(), Map.of("name", "ada")));
    }

    /**
     * A reference the registry does not hold names no document, so there is nothing to check the
     * payload against and it goes through. The miss is counted instead — see {@link
     * SchemaMetricsTest} — so an operator sees the unregistered reference.
     */
    @Test
    void anUnresolvableReferenceLeavesThePayloadUnvalidated() {
        // Data that the registered `person` schema would reject, to show nothing checked it.
        assertDoesNotThrow(() -> service.validate(reference("person", 7), Map.of()));
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

        assertTrue(thrown.getMessage().contains("not yet supported"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("registry://person"), thrown.getMessage());
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
     * document is unusable on this server — the validator needs a {@code $schema} tag — so the
     * payload is left unvalidated. What must not happen is the registry quietly standing in for it:
     * the registered `person` schema would reject this payload, and nothing does.
     */
    @Test
    void anEmptyInlineDocumentIsNotSilentlyReplacedByTheRegistry() {
        service.saveSchema(inline(), false);
        SchemaDef empty = inline();
        empty.setData(Map.of());

        assertDoesNotThrow(() -> service.validate(empty, Map.of()));
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

    /**
     * A document the validator cannot use is a definition error, not a bad payload: it is logged
     * for whoever registered it, and the payload it could not check goes through.
     */
    @Test
    void aMalformedSchemaDocumentLeavesThePayloadUnvalidated() {
        SchemaDef malformed = inline();
        malformed.setData(Map.of("type", 7));

        assertDoesNotThrow(() -> service.validate(malformed, Map.of()));
    }
}
