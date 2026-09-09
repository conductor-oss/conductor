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
package org.conductoross.conductor.ai.testing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class LlmJsonFilesTest {
    private static final String MAPPER_ISOLATION_SCENARIO = "mapper_isolation";
    private static final String GREETING = "hello";
    private static final String BLOCKED_SCENARIO = "blocked_before_llm";
    private static final String SCENARIO_NAME = "scenario";
    private static final String VALID_RECORDING_JSON =
            "{\"schemaVersion\":1,\"scenario\":\"weather\",\"entries\":[]}";
    private static final String EMPTY_ENTRIES_JSON = "\"entries\":[]";
    private static final String UNKNOWN_FIELD_JSON = "\"entries\":[],\"secret\":\"value\"";
    private static final String DUPLICATE_SCENARIO_JSON =
            "\"entries\":[],\"scenario\":\"duplicate\"";
    private static final String SCHEMA_VERSION_PREFIX_JSON = "\"schemaVersion\":1,";
    private static final String SUPPORTED_SCHEMA_VERSION_JSON = "\"schemaVersion\":1";
    private static final String UNSUPPORTED_SCHEMA_VERSION_JSON = "\"schemaVersion\":2";
    private static final String TRAILING_DOCUMENT_JSON = " {}";

    @TempDir Path directory;

    @Test
    void savedResponsesJsonDoesNotChangeSharedMapperConfiguration() throws Exception {
        ObjectMapper shared = new ObjectMapperProvider().getObjectMapper();
        LlmSavedResponses savedResponses =
                new LlmSavedResponses(1, MAPPER_ISOLATION_SCENARIO, List.of());
        Path path = LlmJsonFiles.write(directory, savedResponses, false);
        try (InputStream source = Files.newInputStream(path)) {
            assertEquals(savedResponses, LlmJsonFiles.read(source));
        }
        new LlmRequestResponseConverter()
                .toSavedRequest(new Prompt(GREETING), new ChatCompletion());
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertEquals(
                JsonInclude.Include.NON_NULL,
                shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());
    }

    @Test
    void writesAndReadsSavedResponsesWithoutExposingTemporaryFiles() throws Exception {
        LlmSavedResponses savedResponses = new LlmSavedResponses(1, BLOCKED_SCENARIO, List.of());
        Path target = LlmJsonFiles.write(directory, savedResponses, false);
        try (InputStream source = Files.newInputStream(target)) {
            assertEquals(savedResponses, LlmJsonFiles.read(source));
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void preservesExistingSavedResponsesUnlessRefreshIsExplicit() throws Exception {
        LlmSavedResponses old = new LlmSavedResponses(1, SCENARIO_NAME, List.of());
        LlmSavedResponses replacement = new LlmSavedResponses(1, SCENARIO_NAME, List.of(entry()));
        Path target = LlmJsonFiles.write(directory, old, false);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> LlmJsonFiles.write(directory, replacement, false));
        try (InputStream source = Files.newInputStream(target)) {
            assertEquals(old, LlmJsonFiles.read(source));
        }
        LlmJsonFiles.write(directory, replacement, true);
        try (InputStream source = Files.newInputStream(target)) {
            assertEquals(replacement, LlmJsonFiles.read(source));
        }
    }

    @Test
    void concurrentPublishersCannotOverwriteEachOther() throws Exception {
        LlmSavedResponses savedResponses = new LlmSavedResponses(1, SCENARIO_NAME, List.of());
        Callable<Boolean> write =
                () -> {
                    try {
                        LlmJsonFiles.write(directory, savedResponses, false);
                        return true;
                    } catch (FileAlreadyExistsException expected) {
                        return false;
                    }
                };
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = executor.invokeAll(List.of(write, write));
            assertNotEquals(results.get(0).get(), results.get(1).get());
        }
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void rejectsUnknownFieldsMissingFieldsDuplicateKeysAndTrailingDocuments() {
        String valid = VALID_RECORDING_JSON;
        for (String invalid :
                List.of(
                        valid.replace(EMPTY_ENTRIES_JSON, UNKNOWN_FIELD_JSON),
                        valid.replace(EMPTY_ENTRIES_JSON, DUPLICATE_SCENARIO_JSON),
                        valid.replace(SCHEMA_VERSION_PREFIX_JSON, StringUtils.EMPTY),
                        valid.replace(
                                SUPPORTED_SCHEMA_VERSION_JSON, UNSUPPORTED_SCHEMA_VERSION_JSON),
                        valid + TRAILING_DOCUMENT_JSON)) {
            assertThrows(
                    IOException.class,
                    () ->
                            LlmJsonFiles.read(
                                    new ByteArrayInputStream(
                                            invalid.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static LlmSavedResponses.Entry entry() {
        LlmRequestResponseConverter converter = new LlmRequestResponseConverter();
        LlmSavedResponses.Request request =
                converter.toSavedRequest(new Prompt(GREETING), new ChatCompletion());
        LlmSavedResponses.Message message =
                new LlmSavedResponses.Message(
                        MessageType.ASSISTANT.getValue(), GREETING, List.of(), List.of());
        LlmSavedResponses.Response response =
                new LlmSavedResponses.Response(
                        List.of(
                                new LlmSavedResponses.Completion(
                                        message,
                                        org.conductoross.conductor.ai.model.FinishReason.STOP)));
        return new LlmSavedResponses.Entry(request, response);
    }
}
