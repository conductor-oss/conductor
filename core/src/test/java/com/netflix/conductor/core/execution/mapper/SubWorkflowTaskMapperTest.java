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
package com.netflix.conductor.core.execution.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_SUB_WORKFLOW;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubWorkflowTaskMapperTest {

    private SubWorkflowTaskMapper subWorkflowTaskMapper;
    private ParametersUtils parametersUtils;
    private DeciderService deciderService;
    private IDGenerator idGenerator;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        subWorkflowTaskMapper = new SubWorkflowTaskMapper(parametersUtils, metadataDAO);
        deciderService = mock(DeciderService.class);
        idGenerator = new IDGenerator();
    }

    @Test
    public void getMappedTasks() {
        // Given
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        WorkflowTask workflowTask = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        workflowTask.setSubWorkflowParam(subWorkflowParams);
        workflowTask.setStartDelay(30);
        Map<String, Object> taskInput = new HashMap<>();
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };

        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name", "FooWorkFlow");
        subWorkflowParamMap.put("version", 2);
        subWorkflowParamMap.put("taskToDomain", taskToDomain);
        when(parametersUtils.getTaskInputV2(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(subWorkflowParamMap);

        // When
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(idGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = subWorkflowTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertFalse(mappedTasks.isEmpty());
        assertEquals(1, mappedTasks.size());

        TaskModel subWorkFlowTask = mappedTasks.get(0);
        assertEquals(TaskModel.Status.SCHEDULED, subWorkFlowTask.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, subWorkFlowTask.getTaskType());
        assertEquals(30, subWorkFlowTask.getCallbackAfterSeconds());
        assertEquals(taskToDomain, subWorkFlowTask.getInputData().get("subWorkflowTaskToDomain"));
    }

    @Test
    public void testTaskToDomain() {
        // Given
        WorkflowDef workflowDef = new WorkflowDef();
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        WorkflowTask workflowTask = new WorkflowTask();
        Map<String, String> taskToDomain =
                new HashMap<>() {
                    {
                        put("*", "unittest");
                    }
                };
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        subWorkflowParams.setTaskToDomain(taskToDomain);
        workflowTask.setSubWorkflowParam(subWorkflowParams);
        Map<String, Object> taskInput = new HashMap<>();

        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name", "FooWorkFlow");
        subWorkflowParamMap.put("version", 2);

        when(parametersUtils.getTaskInputV2(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(subWorkflowParamMap);

        // When
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(taskInput)
                        .withRetryCount(0)
                        .withTaskId(new IDGenerator().generate())
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = subWorkflowTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertFalse(mappedTasks.isEmpty());
        assertEquals(1, mappedTasks.size());

        TaskModel subWorkFlowTask = mappedTasks.get(0);
        assertEquals(TaskModel.Status.SCHEDULED, subWorkFlowTask.getStatus());
        assertEquals(TASK_TYPE_SUB_WORKFLOW, subWorkFlowTask.getTaskType());
    }

    @Test
    public void getSubWorkflowParams() {
        WorkflowTask workflowTask = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion(2);
        workflowTask.setSubWorkflowParam(subWorkflowParams);

        assertEquals(subWorkflowParams, subWorkflowTaskMapper.getSubWorkflowParams(workflowTask));
    }

    @Test
    public void getExceptionWhenNoSubWorkflowParamsPassed() {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("FooWorkFLow");

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                String.format(
                        "Task %s is defined as sub-workflow and is missing subWorkflowParams. "
                                + "Please check the workflow definition",
                        workflowTask.getName()));

        subWorkflowTaskMapper.getSubWorkflowParams(workflowTask);
    }

    /**
     * Validates that a String expression in SubWorkflowParams.workflowDefinition is resolved at
     * runtime to the concrete object it references. This is the inline sub-workflow path: the
     * caller sets workflowDefinition to "${someTask.output.result}" and expects the mapper to
     * resolve it to the actual Map (which SubWorkflow.start() then converts to a WorkflowDef).
     *
     * <p>Uses a real ParametersUtils instance so that the expression resolution logic is exercised
     * rather than mocked.
     */
    @Test
    public void workflowDefinitionStringExpressionIsResolvedToRuntimeValue() {
        // Build a real ParametersUtils — only needs an ObjectMapper, no database.
        ParametersUtils realParametersUtils =
                new ParametersUtils(new ObjectMapperProvider().getObjectMapper());
        MetadataDAO metadataDAO = mock(MetadataDAO.class);
        SubWorkflowTaskMapper mapper = new SubWorkflowTaskMapper(realParametersUtils, metadataDAO);

        // Set up a workflow model that has a completed task whose output contains the
        // inline workflow definition Map we want to pass to the sub-workflow.
        Map<String, Object> inlineWfDef = new HashMap<>();
        inlineWfDef.put("name", "dynamic_plan_wf");
        inlineWfDef.put("version", 1);
        inlineWfDef.put("tasks", List.of());

        TaskModel planTask = new TaskModel();
        planTask.setReferenceTaskName("planTask");
        planTask.setTaskType("SIMPLE");
        planTask.setStatus(TaskModel.Status.COMPLETED);
        planTask.addOutput("result", inlineWfDef);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("parentWf");
        workflowDef.setSchemaVersion(2);

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        workflowModel.getTasks().add(planTask);

        // subWorkflowParams.workflowDefinition is a String expression — not a concrete object.
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("dynamic_plan_wf");
        subWorkflowParams.setVersion(1);
        subWorkflowParams.setWorkflowDefinition("${planTask.output.result}");

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setSubWorkflowParam(subWorkflowParams);

        TaskMapperContext context =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(workflowTask)
                        .withTaskInput(new HashMap<>())
                        .withRetryCount(0)
                        .withTaskId(new IDGenerator().generate())
                        .withDeciderService(mock(DeciderService.class))
                        .build();

        List<TaskModel> mappedTasks = mapper.getMappedTasks(context);

        assertFalse(mappedTasks.isEmpty());
        TaskModel subWorkflowTask = mappedTasks.get(0);
        assertEquals(TASK_TYPE_SUB_WORKFLOW, subWorkflowTask.getTaskType());

        // The critical assertion: the String expression must have been resolved to the actual Map.
        // If the bug is present, subWorkflowDefinition is the literal String
        // "${planTask.output.result}".
        Object resolved = subWorkflowTask.getInputData().get("subWorkflowDefinition");
        assertNotNull("subWorkflowDefinition must not be null", resolved);
        assertFalse(
                "subWorkflowDefinition must be the resolved Map, not the raw expression String",
                resolved instanceof String);
        @SuppressWarnings("unchecked")
        Map<String, Object> resolvedMap = (Map<String, Object>) resolved;
        assertEquals("dynamic_plan_wf", resolvedMap.get("name"));
    }
}
