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
package org.conductoross.conductor.ai.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.tasks.mapper.AgentTaskMapper;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentTaskMapperTest {

    @Test
    void producesScheduledTaskWithInput() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("call_agent_task");
        workflowTask.setType(AgentTaskMapper.TASK_TYPE);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());

        Map<String, Object> input = new HashMap<>();
        input.put("agentUrl", "http://agent");
        input.put("text", "convert 100 USD to EUR");

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(new IDGenerator().generate())
                        .build();

        List<TaskModel> mapped = new AgentTaskMapper().getMappedTasks(context);

        assertEquals(1, mapped.size());
        assertEquals(AgentTaskMapper.TASK_TYPE, mapped.get(0).getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, mapped.get(0).getStatus());
        assertEquals("http://agent", mapped.get(0).getInputData().get("agentUrl"));
    }
}
