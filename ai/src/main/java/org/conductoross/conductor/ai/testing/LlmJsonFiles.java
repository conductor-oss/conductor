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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.conductoross.conductor.common.JsonSchemaValidator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;

/** Strict JSON IO for completed recordings; callers finalize execution before publishing a file. */
public final class LlmJsonFiles {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final JsonSchema SCHEMA = loadSchema();

    private LlmJsonFiles() {}

    private static JsonSchema loadSchema() {
        try (var source =
                LlmJsonFiles.class.getResourceAsStream("/llm-saved-responses.schema.json")) {
            if (source == null)
                throw new IllegalStateException("LLM saved responses schema is missing");
            return new JsonSchemaValidator(MAPPER)
                    .getJsonSchema(
                            new String(
                                    source.readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load LLM saved responses schema", e);
        }
    }

    /**
     * The caller owns the stream, allowing the same reader for classpath and filesystem recordings.
     */
    public static LlmSavedResponses read(InputStream source) throws IOException {
        byte[] bytes = source.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("LLM saved responses exceeds the 16 MiB limit");
        }
        var node = MAPPER.readTree(bytes);
        if (node == null || !SCHEMA.validate(node).isEmpty()) {
            throw new IOException("Invalid LLM saved responses schema");
        }
        return MAPPER.treeToValue(node, LlmSavedResponses.class);
    }

    public static Path write(Path directory, LlmSavedResponses savedResponses, boolean refresh)
            throws IOException {
        if (!SCHEMA.validate(MAPPER.valueToTree(savedResponses)).isEmpty()) {
            throw new IOException("Invalid LLM saved responses schema");
        }
        byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(savedResponses);
        if (bytes.length > MAX_BYTES) {
            throw new IOException("LLM saved responses exceeds the 16 MiB limit");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(savedResponses.scenario() + ".json");
        Path temporary = Files.createTempFile(directory, ".llm-recording-", ".tmp");
        try {
            Files.write(temporary, bytes);
            if (refresh) {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                // A hard link publishes the complete file atomically and fails if target exists.
                // ATOMIC_MOVE alone may overwrite an existing file even without REPLACE_EXISTING.
                // Fail explicitly on filesystems without hard links rather than risk replacement.
                Files.createLink(target, temporary);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
