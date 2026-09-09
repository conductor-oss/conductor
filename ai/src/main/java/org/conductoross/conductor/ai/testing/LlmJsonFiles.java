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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Publishes completed recordings using the application's JSON mapper. */
public final class LlmJsonFiles {
    public static final String FILE_EXTENSION = ".json";
    private final ObjectMapper objectMapper;

    public LlmJsonFiles(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path writeRecording(Path directory, LlmSavedResponses savedResponses)
            throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(UUID.randomUUID() + FILE_EXTENSION);
        Path temporary = Files.createTempFile(directory, ".llm-recording-", ".tmp");
        try {
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(temporary.toFile(), savedResponses);
            // Publish the complete file atomically without replacing an existing recording.
            Files.createLink(target, temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
        return target;
    }
}
