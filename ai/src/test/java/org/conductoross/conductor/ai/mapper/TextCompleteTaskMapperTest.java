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

import org.conductoross.conductor.ai.tasks.mapper.TextCompleteTaskMapper;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.conductoross.conductor.ai.tasks.mapper.AIModelTaskMapper.LLM_PROVIDER;
import static org.conductoross.conductor.ai.tasks.mapper.AIModelTaskMapper.MODEL_NAME;
import static org.conductoross.conductor.ai.tasks.mapper.AIModelTaskMapper.PROMPT_NAME_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextCompleteTaskMapperTest {

    @Test
    public void testTaskMapperValidations() {
        // Given
        String taskType = "LLM_TEXT_COMPLETE";
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("text_complete_task");
        workflowTask.setType(taskType);
        String provider = "azure_openai";
        String model = "gpt-3";
        String promptName = "some-prompt";
        String taskId = new IDGenerator().generate();

        WorkflowModel workflow = new WorkflowModel();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext =
                getTaskMapperContext(workflow, workflowTask, taskId, null);

        TextCompleteTaskMapper textCompleteTaskMapper = new TextCompleteTaskMapper();
        // Without any input parameters
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals(
                    "No provider provided for task null. Please provide it using 'llmProvider' input parameter",
                    e.getMessage());
        }
        // We add 'llmProvider' input parameter
        Map<String, Object> taskInputs = new HashMap<>();
        taskInputs.put(LLM_PROVIDER, provider);
        taskMapperContext = getTaskMapperContext(workflow, workflowTask, taskId, taskInputs);
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals(
                    "No model provided for task null. Please provide it using 'model' input parameter",
                    e.getMessage());
        }

        // We add 'model' input parameter
        taskInputs.put(MODEL_NAME, model);
        taskMapperContext = getTaskMapperContext(workflow, workflowTask, taskId, taskInputs);
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals(
                    "User anonymous does not have access to the Integration "
                            + provider
                            + ":"
                            + model,
                    e.getMessage());
        }

        // Now we use the partially mocked OrkesPermissionEvaluator
        textCompleteTaskMapper = new TextCompleteTaskMapper();
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals(
                    "No promptName provided for task null. Please provide it using 'promptName' input parameter",
                    e.getMessage());
        }

        // We add 'promptName' input parameter
        taskInputs.put(PROMPT_NAME_KEY, promptName);
        taskMapperContext = getTaskMapperContext(workflow, workflowTask, taskId, taskInputs);
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals(
                    "User anonymous does not have EXECUTE permission over prompt: " + promptName,
                    e.getMessage());
        }

        // We use the fully mocked OrkesPermissionEvaluator
        textCompleteTaskMapper = new TextCompleteTaskMapper();
        try {
            textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        } catch (TerminateWorkflowException e) {
            assertEquals("No prompt template found by name '" + promptName + "'", e.getMessage());
        }

        textCompleteTaskMapper.getMappedTasks(taskMapperContext);

        List<TaskModel> mappedTasks = textCompleteTaskMapper.getMappedTasks(taskMapperContext);
        assertEquals(1, mappedTasks.size());
        assertEquals(taskType, mappedTasks.get(0).getTaskType());
        assertEquals(TaskModel.Status.SCHEDULED, mappedTasks.get(0).getStatus());
    }

    protected TaskMapperContext getTaskMapperContext(
            WorkflowModel workflowModel,
            WorkflowTask workflowTask,
            String taskId,
            Map<String, Object> inputs) {
        return TaskMapperContext.newBuilder()
                .withWorkflowModel(workflowModel)
                .withTaskDefinition(new TaskDef())
                .withWorkflowTask(workflowTask)
                .withTaskInput(inputs != null ? inputs : new HashMap<>())
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();
    }
}
