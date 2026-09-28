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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiDecisionTaskMapperTest {

    @Test
    void schedulesDecisionTaskWithoutRegisteredTaskDefinition() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(AiDecisionTaskMapper.TASK_TYPE);
        workflowTask.setType(AiDecisionTaskMapper.TASK_TYPE);
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());
        Map<String, Object> input = Map.of("model", "jev-1.13", "state", "refund request");
        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(new IDGenerator().generate())
                        .build();

        List<TaskModel> mapped = new AiDecisionTaskMapper().getMappedTasks(context);

        assertEquals(1, mapped.size());
        assertEquals(AiDecisionTaskMapper.TASK_TYPE, mapped.get(0).getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, mapped.get(0).getStatus());
        assertEquals("refund request", mapped.get(0).getInputData().get("state"));
    }
}
