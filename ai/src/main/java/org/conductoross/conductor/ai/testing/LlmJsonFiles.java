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
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Publishes completed recordings using the application's JSON mapper. */
public final class LlmJsonFiles {
    public static final String FILE_EXTENSION = ".json";
    private static final String TEMP_FILE_PREFIX = ".llm-recording-";
    private static final String TEMP_FILE_SUFFIX = ".tmp";
    private final ObjectMapper objectMapper;

    public LlmJsonFiles(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Path write(Path directory, LlmSavedResponses savedResponses, boolean refresh)
            throws IOException {
        return write(directory, savedResponses.scenario(), savedResponses, refresh);
    }

    public Path writeRecording(Path directory, LlmSavedResponses savedResponses)
            throws IOException {
        return write(directory, UUID.randomUUID().toString(), savedResponses, false);
    }

    private Path write(
            Path directory, String name, LlmSavedResponses savedResponses, boolean refresh)
            throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(name + FILE_EXTENSION);
        Path temporary = Files.createTempFile(directory, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
        try {
            objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(temporary.toFile(), savedResponses);
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
