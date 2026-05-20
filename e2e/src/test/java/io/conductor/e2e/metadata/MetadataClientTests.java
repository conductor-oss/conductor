/*
 * Copyright 2022 Conductor Authors.
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
package io.conductor.e2e.metadata;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;

import io.conductor.e2e.util.ApiUtil;
import io.conductor.e2e.util.Commons;
import io.conductor.e2e.util.WorkflowUtil;

import static org.junit.jupiter.api.Assertions.*;

public class MetadataClientTests {
    private final MetadataClient metadataClient = ApiUtil.METADATA_CLIENT;

    @Test
    void testTaskDefinition() {
        try {
            metadataClient.unregisterTaskDef(Commons.TASK_NAME);
        } catch (Exception ignored) {
            // server returns 500 (not 404) for non-existent resources
        }
        TaskDef taskDef = Commons.getTaskDef();
        metadataClient.registerTaskDefs(List.of(taskDef));
        metadataClient.updateTaskDef(taskDef);
        TaskDef receivedTaskDef = metadataClient.getTaskDef(Commons.TASK_NAME);
        assertTrue(taskDef.getName().equals(receivedTaskDef.getName()));
    }

    @Test
    void testWorkflow() {
        try {
            metadataClient.unregisterWorkflowDef(Commons.WORKFLOW_NAME, Commons.WORKFLOW_VERSION);
        } catch (Exception ignored) {
            // server returns 500 (not 404) for non-existent resources
        }
        metadataClient.registerTaskDefs(List.of(Commons.getTaskDef()));
        WorkflowDef workflowDef = WorkflowUtil.getWorkflowDef();
        metadataClient.updateWorkflowDefs(List.of(workflowDef));
        metadataClient.updateWorkflowDefs(List.of(workflowDef));
        WorkflowDef receivedWorkflowDef =
                metadataClient.getWorkflowDef(Commons.WORKFLOW_NAME, Commons.WORKFLOW_VERSION);
        assertTrue(receivedWorkflowDef.getName().equals(Commons.WORKFLOW_NAME));
        assertEquals(receivedWorkflowDef.getVersion(), Commons.WORKFLOW_VERSION);
    }
}
