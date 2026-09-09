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
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.conductoross.conductor.ai.model.ChatCompletion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.prompt.Prompt;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;

import static org.junit.jupiter.api.Assertions.*;

class LlmJsonFilesTest {
    @TempDir Path directory;

    @Test
    void savedResponsesJsonDoesNotChangeSharedMapperConfiguration() throws Exception {
        var shared = new ObjectMapperProvider().getObjectMapper();
        var savedResponses = new LlmSavedResponses(1, "mapper_isolation", List.of());
        var path = LlmJsonFiles.write(directory, savedResponses, false);
        try (var source = Files.newInputStream(path)) {
            assertEquals(savedResponses, LlmJsonFiles.read(source));
        }
        new LlmRequestResponseConverter().toSavedRequest(new Prompt("hello"), new ChatCompletion());
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        assertFalse(shared.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
        assertEquals(
                JsonInclude.Include.NON_NULL,
                shared.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion());
    }

    @Test
    void writesAndReadsSavedResponsesWithoutExposingTemporaryFiles() throws Exception {
        var savedResponses = new LlmSavedResponses(1, "blocked_before_llm", List.of());
        Path target = LlmJsonFiles.write(directory, savedResponses, false);
        try (var source = Files.newInputStream(target)) {
            assertEquals(savedResponses, LlmJsonFiles.read(source));
        }
        try (var files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void preservesExistingSavedResponsesUnlessRefreshIsExplicit() throws Exception {
        var old = new LlmSavedResponses(1, "scenario", List.of());
        var replacement = new LlmSavedResponses(1, "scenario", List.of(entry()));
        Path target = LlmJsonFiles.write(directory, old, false);
        assertThrows(
                FileAlreadyExistsException.class,
                () -> LlmJsonFiles.write(directory, replacement, false));
        try (var source = Files.newInputStream(target)) {
            assertEquals(old, LlmJsonFiles.read(source));
        }
        LlmJsonFiles.write(directory, replacement, true);
        try (var source = Files.newInputStream(target)) {
            assertEquals(replacement, LlmJsonFiles.read(source));
        }
    }

    @Test
    void concurrentPublishersCannotOverwriteEachOther() throws Exception {
        var savedResponses = new LlmSavedResponses(1, "scenario", List.of());
        Callable<Boolean> write =
                () -> {
                    try {
                        LlmJsonFiles.write(directory, savedResponses, false);
                        return true;
                    } catch (FileAlreadyExistsException expected) {
                        return false;
                    }
                };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(write, write));
            assertNotEquals(results.get(0).get(), results.get(1).get());
        }
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void rejectsUnknownFieldsMissingFieldsDuplicateKeysAndTrailingDocuments() {
        String valid = "{\"schemaVersion\":1,\"scenario\":\"weather\",\"entries\":[]}";
        for (String invalid :
                List.of(
                        valid.replace("\"entries\":[]", "\"entries\":[],\"secret\":\"value\""),
                        valid.replace(
                                "\"entries\":[]", "\"entries\":[],\"scenario\":\"duplicate\""),
                        valid.replace("\"schemaVersion\":1,", ""),
                        valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                        valid + " {}")) {
            assertThrows(
                    IOException.class,
                    () ->
                            LlmJsonFiles.read(
                                    new ByteArrayInputStream(
                                            invalid.getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static LlmSavedResponses.Entry entry() {
        var converter = new LlmRequestResponseConverter();
        var request = converter.toSavedRequest(new Prompt("hello"), new ChatCompletion());
        var message = new LlmSavedResponses.Message("assistant", "hello", List.of(), List.of());
        var response =
                new LlmSavedResponses.Response(
                        List.of(
                                new LlmSavedResponses.Completion(
                                        message,
                                        org.conductoross.conductor.ai.model.FinishReason.STOP)));
        return new LlmSavedResponses.Entry(request, response);
    }
}
