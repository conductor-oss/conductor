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
package com.netflix.conductor.model;

import org.junit.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;

import tools.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WorkflowModelTest {

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    @Test
    public void theApiWorkflowNamesTheTaskItFailedAt() throws Exception {
        WorkflowModel model = new WorkflowModel();
        model.setWorkflowId("wf-1");
        model.setStatus(WorkflowModel.Status.FAILED);
        WorkflowDef def = new WorkflowDef();
        def.setName("orders");
        model.setWorkflowDefinition(def);
        model.setFailedTaskId("task-7");

        Workflow workflow = model.toWorkflow();

        assertEquals("task-7", workflow.getFailedTaskId());
        assertEquals(
                "task-7",
                objectMapper
                        .readTree(objectMapper.writeValueAsString(workflow))
                        .get("failedTaskId")
                        .asText());
    }

    @Test
    public void aWorkflowThatDidNotFailAtATaskNamesNone() {
        WorkflowModel model = new WorkflowModel();
        model.setWorkflowId("wf-1");
        model.setStatus(WorkflowModel.Status.COMPLETED);

        assertNull(model.toWorkflow().getFailedTaskId());
    }
}
