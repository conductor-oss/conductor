/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.core.execution.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_WAIT_FOR_WEBHOOK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class WaitForWebhookTaskMapperTest {

    @Test
    public void getTaskType() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);
        assertEquals(TaskType.WAIT_FOR_WEBHOOK.name(), mapper.getTaskType());
    }

    @Test
    public void getMappedTasks_createsOneTask() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);

        TaskMapperContext context = buildContext(parametersUtils, new HashMap<>());
        List<TaskModel> tasks = mapper.getMappedTasks(context);

        assertEquals(1, tasks.size());
    }

    @Test
    public void getMappedTasks_taskIsInProgress() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);

        TaskMapperContext context = buildContext(parametersUtils, new HashMap<>());
        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(TaskModel.Status.IN_PROGRESS, task.getStatus());
    }

    @Test
    public void getMappedTasks_taskTypeIsWaitForWebhook() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);

        TaskMapperContext context = buildContext(parametersUtils, new HashMap<>());
        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(TASK_TYPE_WAIT_FOR_WEBHOOK, task.getTaskType());
    }

    @Test
    public void getMappedTasks_callbackSetToMaxValue() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);

        TaskMapperContext context = buildContext(parametersUtils, new HashMap<>());
        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertEquals(Integer.MAX_VALUE, task.getCallbackAfterSeconds());
    }

    @Test
    public void getMappedTasks_resolvedInputStoredOnTask() {
        ParametersUtils parametersUtils = mock(ParametersUtils.class);
        Map<String, Object> resolvedInput =
                Map.of("matches", Map.of("$['event']['type']", "payment.completed"));
        doReturn(resolvedInput).when(parametersUtils).getTaskInputV2(any(), any(), any(), any());

        WaitForWebhookTaskMapper mapper = new WaitForWebhookTaskMapper(parametersUtils);
        TaskMapperContext context = buildContext(parametersUtils, resolvedInput);
        TaskModel task = mapper.getMappedTasks(context).get(0);

        assertTrue(task.getInputData().containsKey("matches"));
    }

    private TaskMapperContext buildContext(
            ParametersUtils parametersUtils, Map<String, Object> inputParameters) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("wait_for_webhook_task");
        workflowTask.setType(TaskType.WAIT_FOR_WEBHOOK.name());
        workflowTask.setInputParameters(inputParameters);

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        String taskId = new IDGenerator().generate();

        return TaskMapperContext.newBuilder()
                .withWorkflowModel(workflow)
                .withTaskDefinition(new TaskDef())
                .withWorkflowTask(workflowTask)
                .withTaskInput(inputParameters)
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();
    }
}
