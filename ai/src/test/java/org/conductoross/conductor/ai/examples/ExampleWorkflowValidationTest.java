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
package org.conductoross.conductor.ai.examples;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all JSON files in ai/examples/ are valid WorkflowDef definitions that can be
 * deserialized and have required fields populated.
 */
class ExampleWorkflowValidationTest {

    private static final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private static final Path EXAMPLES_DIR = Paths.get(System.getProperty("user.dir"), "examples");

    @Test
    void allExampleJsonFilesDeserializeToWorkflowDef() throws IOException {
        assertTrue(Files.isDirectory(EXAMPLES_DIR), "examples/ directory must exist");

        List<Path> jsonFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(EXAMPLES_DIR, "*.json")) {
            stream.forEach(jsonFiles::add);
        }

        assertFalse(jsonFiles.isEmpty(), "examples/ directory must contain JSON files");

        for (Path jsonFile : jsonFiles) {
            String fileName = jsonFile.getFileName().toString();
            String json = Files.readString(jsonFile);

            WorkflowDef def =
                    assertDoesNotThrow(
                            () -> objectMapper.readValue(json, WorkflowDef.class),
                            fileName + " failed to deserialize to WorkflowDef");

            assertNotNull(def.getName(), fileName + " must have a name");
            assertFalse(def.getName().isBlank(), fileName + " name must not be blank");
            assertTrue(def.getVersion() > 0, fileName + " version must be > 0");
            assertNotNull(def.getTasks(), fileName + " must have tasks");
            assertFalse(def.getTasks().isEmpty(), fileName + " must have at least one task");

            // Verify every task has a name, taskReferenceName, and type
            for (int i = 0; i < def.getTasks().size(); i++) {
                var task = def.getTasks().get(i);
                String taskCtx = fileName + " task[" + i + "]";
                assertNotNull(task.getName(), taskCtx + " must have a name");
                assertNotNull(
                        task.getTaskReferenceName(), taskCtx + " must have a taskReferenceName");
                assertNotNull(task.getType(), taskCtx + " must have a type");
            }
        }
    }
}
