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
import java.util.UUID;

import org.apache.commons.lang3.ObjectUtils;
import org.conductoross.conductor.common.JsonSchemaValidator;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;

/** Strict JSON IO for completed recordings; callers finalize execution before publishing a file. */
public final class LlmJsonFiles {
    private static final String SCHEMA_RESOURCE = "/llm-saved-responses.schema.json";
    private static final String MISSING_SCHEMA = "LLM saved responses schema is missing";
    private static final String SCHEMA_LOAD_FAILED = "Cannot load LLM saved responses schema";
    private static final String RECORDING_TOO_LARGE =
            "LLM saved responses exceeds the 16 MiB limit";
    private static final String INVALID_SCHEMA = "Invalid LLM saved responses schema";
    public static final String FILE_EXTENSION = ".json";
    private static final String TEMP_FILE_PREFIX = ".llm-recording-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";

    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final JsonSchema SCHEMA = loadSchema();

    private LlmJsonFiles() {}

    private static JsonSchema loadSchema() {
        try (InputStream source = LlmJsonFiles.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (source == null) throw new IllegalStateException(MISSING_SCHEMA);
            return new JsonSchemaValidator(MAPPER)
                    .getJsonSchema(
                            new String(
                                    source.readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(SCHEMA_LOAD_FAILED, e);
        }
    }

    /**
     * The caller owns the stream, allowing the same reader for classpath and filesystem recordings.
     */
    public static LlmSavedResponses read(InputStream source) throws IOException {
        byte[] bytes = source.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new IOException(RECORDING_TOO_LARGE);
        }
        JsonNode node = MAPPER.readTree(bytes);
        if (node == null || ObjectUtils.isNotEmpty(SCHEMA.validate(node))) {
            throw new IOException(INVALID_SCHEMA);
        }
        return MAPPER.treeToValue(node, LlmSavedResponses.class);
    }

    public static Path write(Path directory, LlmSavedResponses savedResponses, boolean refresh)
            throws IOException {
        return write(directory, savedResponses.scenario(), savedResponses, refresh);
    }

    public static Path writeRecording(Path directory, LlmSavedResponses savedResponses)
            throws IOException {
        return write(directory, UUID.randomUUID().toString(), savedResponses, false);
    }

    private static Path write(
            Path directory, String name, LlmSavedResponses savedResponses, boolean refresh)
            throws IOException {
        if (ObjectUtils.isNotEmpty(SCHEMA.validate(MAPPER.valueToTree(savedResponses)))) {
            throw new IOException(INVALID_SCHEMA);
        }
        byte[] bytes = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(savedResponses);
        if (bytes.length > MAX_BYTES) {
            throw new IOException(RECORDING_TOO_LARGE);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(name + FILE_EXTENSION);
        Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
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
