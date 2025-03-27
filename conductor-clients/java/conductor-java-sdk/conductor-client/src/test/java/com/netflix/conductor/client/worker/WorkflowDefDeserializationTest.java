/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.client.worker;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowDefDeserializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setup() {
        objectMapper = new ObjectMapperProvider().getObjectMapper();
    }

    @Test
    @DisplayName("Should correctly deserialize subworkflow priority as a dynamic expression")
    public void testSubworkflowPriorityDeserialization() throws Exception {
        WorkflowDef mainWorkflowDef;
        try (InputStream inputStream = getClass().getResourceAsStream("/workflows/main_workflow.json")) {
            if (inputStream == null) {
                throw new IOException("Resource not found: /workflows/main_workflow.json");
            }
            mainWorkflowDef = objectMapper.readValue(inputStream, WorkflowDef.class);
        }

        assertEquals("${fetchPriority.output.priority}",
                mainWorkflowDef.getTasks().get(1).getSubWorkflowParam().getPriority().toString(),
                "Subworkflow priority should be deserialized as a dynamic expression");
    }
}
