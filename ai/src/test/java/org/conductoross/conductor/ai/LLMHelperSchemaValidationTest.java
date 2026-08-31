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
package org.conductoross.conductor.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.conductoross.conductor.ai.model.ChatMessage;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.conductoross.conductor.common.JsonSchemaValidator;
import org.conductoross.conductor.dao.schema.SchemaDAO;
import org.conductoross.conductor.service.SchemaCacheProperties;
import org.conductoross.conductor.service.SchemaService;
import org.conductoross.conductor.service.SchemaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.Task;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Output-schema validation on an LLM task.
 *
 * <p>Three defects are pinned here, all of which turned a documented feature into a failure: the
 * guard read {@code outputSchema} while the validation read {@code inputSchema}, so attaching an
 * output schema alone dereferenced null; a schema carrying only a name and version was never
 * resolved against the registry and failed with an empty message; and a null branch guarded a
 * condition that could not occur.
 *
 * <p>The repair is covered here rather than end to end: it concerns schema resolution, not model
 * behaviour, so a live model would add a provider credential to CI for no extra signal.
 */
class LLMHelperSchemaValidationTest {

    private static final Map<String, Object> REQUIRES_NAME =
            Map.of(
                    "$schema", "https://json-schema.org/draft/2020-12/schema",
                    "type", "object",
                    "required", List.of("name"));

    private InMemoryDAO dao;
    private SchemaService schemaService;
    private LLMHelper helper;

    @BeforeEach
    void setUp() {
        dao = new InMemoryDAO();
        schemaService =
                new SchemaServiceImpl(
                        dao,
                        new SchemaCacheProperties(),
                        new JsonSchemaValidator(new ObjectMapperProvider().getObjectMapper()));
        helper = new LLMHelper(schemaService, new ArrayList<>());
    }

    private static SchemaDef inlineRequiringName() {
        SchemaDef schema = new SchemaDef();
        schema.setName("person");
        schema.setVersion(1);
        schema.setType(SchemaDef.Type.JSON);
        schema.setData(REQUIRES_NAME);
        return schema;
    }

    private static SchemaDef reference(String name, int version) {
        SchemaDef schema = new SchemaDef();
        schema.setName(name);
        schema.setVersion(version);
        return schema;
    }

    private static ChatCompletion completion(SchemaDef outputSchema) {
        ChatCompletion in = new ChatCompletion();
        in.setLlmProvider("fake");
        in.setModel("fake-1");
        in.setJsonOutput(true);
        in.setOutputSchema(outputSchema);
        in.getMessages().add(new ChatMessage(ChatMessage.Role.user, "Who?"));
        return in;
    }

    private LLMResponse run(ChatCompletion in, String... modelReplies) {
        LLMHelperChatCompleteTest.StagedChatModel model =
                new LLMHelperChatCompleteTest.StagedChatModel();
        model.stage(
                new ChatResponse(
                        java.util.Arrays.stream(modelReplies)
                                .map(
                                        reply ->
                                                new Generation(
                                                        new AssistantMessage(reply),
                                                        ChatGenerationMetadata.builder()
                                                                .finishReason("stop")
                                                                .build()))
                                .toList()));
        Task task = new Task();
        task.setTaskId("t1");
        return helper.chatComplete(
                task, new LLMHelperChatCompleteTest.FakeAIModel(model), in, null, usage -> {});
    }

    @Test
    void anOutputSchemaAloneIsValidatedRatherThanThrowing() {
        ChatCompletion in = completion(inlineRequiringName());

        LLMResponse out = assertDoesNotThrow(() -> run(in, "{\"name\": \"ada\"}"));

        assertEquals(Map.of("name", "ada"), out.getResult());
    }

    @Test
    void anOutputThatBreaksTheSchemaIsReported() {
        ChatCompletion in = completion(inlineRequiringName());

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> run(in, "{\"nickname\": \"ada\"}"));

        assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
    }

    /**
     * The guard and the validation read the same field. With an input schema attached as well, a
     * response that satisfies the output schema must pass — it used to be checked against the input
     * schema instead.
     */
    @Test
    void theOutputIsCheckedAgainstTheOutputSchemaNotTheInputSchema() {
        SchemaDef inputSchema = new SchemaDef();
        inputSchema.setName("question");
        inputSchema.setVersion(1);
        inputSchema.setType(SchemaDef.Type.JSON);
        inputSchema.setData(
                Map.of(
                        "$schema", "https://json-schema.org/draft/2020-12/schema",
                        "type", "object",
                        "required", List.of("question")));

        ChatCompletion in = completion(inlineRequiringName());
        in.setInputSchema(inputSchema);

        assertDoesNotThrow(() -> run(in, "{\"name\": \"ada\"}"));
    }

    @Test
    void aSchemaNamedByVersionResolvesAgainstTheRegistry() {
        schemaService.saveSchema(inlineRequiringName(), false);
        ChatCompletion in = completion(reference("person", 1));

        assertDoesNotThrow(() -> run(in, "{\"name\": \"ada\"}"));

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> run(in, "{\"nickname\": \"ada\"}"));
        assertTrue(thrown.getMessage().contains("name"), thrown.getMessage());
    }

    @Test
    void aSchemaNamedWithoutAVersionResolvesTheLatest() {
        schemaService.saveSchema(inlineRequiringName(), false);
        ChatCompletion in = completion(reference("person", 0));

        assertDoesNotThrow(() -> run(in, "{\"name\": \"ada\"}"));
    }

    @Test
    void anUnregisteredReferenceFailsWithARealMessage() {
        ChatCompletion in = completion(reference("person", 3));

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> run(in, "{\"name\": \"ada\"}"));

        assertFalse(
                thrown.getMessage() == null || thrown.getMessage().isBlank(),
                "an unresolvable reference used to fail with an empty message");
        assertTrue(thrown.getMessage().contains("person"), thrown.getMessage());
    }

    @Test
    void anExternalRefIsNotResolved() {
        SchemaDef external = reference("person", 1);
        external.setType(SchemaDef.Type.JSON);
        external.setExternalRef("registry://person");
        ChatCompletion in = completion(external);

        RuntimeException thrown =
                assertThrows(RuntimeException.class, () -> run(in, "{\"name\": \"ada\"}"));

        assertTrue(thrown.getMessage().contains("person"), thrown.getMessage());
    }

    @Test
    void noOutputSchemaMeansNoValidation() {
        ChatCompletion in = completion(null);

        assertDoesNotThrow(() -> run(in, "{\"nickname\": \"ada\"}"));
    }

    /**
     * The multi-generation branch carried the same field mismatch as the single-response one, and
     * is a separate code path.
     */
    @Test
    void everyGenerationIsCheckedAgainstTheOutputSchema() {
        ChatCompletion in = completion(inlineRequiringName());

        LLMResponse out =
                assertDoesNotThrow(() -> run(in, "{\"name\": \"ada\"}", "{\"name\": \"grace\"}"));

        assertEquals(List.of(Map.of("name", "ada"), Map.of("name", "grace")), out.getResult());
    }

    /** Stores what it is given. The registry's own tests cover the DAO contract. */
    private static class InMemoryDAO implements SchemaDAO {

        private final Map<String, SchemaDef> stored = new ConcurrentHashMap<>();

        private static String key(String name, Integer version) {
            return name + "/" + version;
        }

        @Override
        public void save(SchemaDef schemaDef) {
            stored.put(key(schemaDef.getName(), schemaDef.getVersion()), schemaDef);
        }

        @Override
        public boolean createSchemaIfAbsent(SchemaDef schemaDef) {
            return stored.putIfAbsent(key(schemaDef.getName(), schemaDef.getVersion()), schemaDef)
                    == null;
        }

        @Override
        public SchemaDef findByNameAndVersion(String name, Integer version) {
            Objects.requireNonNull(version, "Schema version cannot be null");
            return stored.get(key(name, version));
        }

        @Override
        public SchemaDef findLatestVersionByName(String name) {
            return stored.values().stream()
                    .filter(def -> def.getName().equals(name))
                    .max(java.util.Comparator.comparingInt(SchemaDef::getVersion))
                    .orElse(null);
        }

        @Override
        public List<SchemaDef> getAll() {
            return List.copyOf(stored.values());
        }

        @Override
        public int deleteByNameAndVersion(String name, Integer version) {
            Objects.requireNonNull(version, "Schema version cannot be null");
            return stored.remove(key(name, version)) == null ? 0 : 1;
        }

        @Override
        public int deleteAllByName(String name) {
            int removed = 0;
            for (var entries = stored.values().iterator(); entries.hasNext(); ) {
                if (entries.next().getName().equals(name)) {
                    entries.remove();
                    removed++;
                }
            }
            return removed;
        }
    }
}
