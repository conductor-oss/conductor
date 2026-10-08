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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.config.TestObjectMapperConfiguration;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.execution.evaluators.JavascriptEvaluator;
import com.netflix.conductor.core.execution.evaluators.ValueParamEvaluator;
import com.netflix.conductor.core.execution.tasks.Switch;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ContextConfiguration(
        classes = {
            TestObjectMapperConfiguration.class,
            SwitchTaskMapperTest.TestConfiguration.class
        })
@RunWith(SpringRunner.class)
public class SwitchTaskMapperTest {

    private IDGenerator idGenerator;
    private ParametersUtils parametersUtils;
    private DeciderService deciderService;
    // Subject
    private SwitchTaskMapper switchTaskMapper;

    @Configuration
    @ComponentScan(basePackageClasses = {Evaluator.class}) // load all Evaluator beans.
    public static class TestConfiguration {}

    @Autowired private ObjectMapper objectMapper;

    @Autowired private Map<String, Evaluator> evaluators;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    Map<String, Object> ip1;
    WorkflowTask task1;
    WorkflowTask task2;
    WorkflowTask task3;

    @Before
    public void setUp() {
        parametersUtils = new ParametersUtils(objectMapper);
        idGenerator = new IDGenerator();

        ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        ip1.put("case", "${workflow.input.case}");

        task1 = new WorkflowTask();
        task1.setName("Test1");
        task1.setInputParameters(ip1);
        task1.setTaskReferenceName("t1");

        task2 = new WorkflowTask();
        task2.setName("Test2");
        task2.setInputParameters(ip1);
        task2.setTaskReferenceName("t2");

        task3 = new WorkflowTask();
        task3.setName("Test3");
        task3.setInputParameters(ip1);
        task3.setTaskReferenceName("t3");
        deciderService = mock(DeciderService.class);
        switchTaskMapper = new SwitchTaskMapper(evaluators);
    }

    @Test
    public void getMappedTasks() {

        // Given
        // Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("Id", "${workflow.input.Id}");
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        // Switch task instance
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType(TaskType.SWITCH.name());
        switchTask.setName("Switch");
        switchTask.setTaskReferenceName("switchTask");
        switchTask.setDefaultCase(Collections.singletonList(task1));
        switchTask.getInputParameters().put("Id", "${workflow.input.Id}");
        switchTask.setEvaluatorType(JavascriptEvaluator.NAME);
        switchTask.setExpression(
                "if ($.Id == null) 'bad input'; else if ( ($.Id != null && $.Id % 2 == 0)) 'even'; else 'odd'; ");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Collections.singletonList(task2));
        decisionCases.put("odd", Collections.singletonList(task3));
        switchTask.setDecisionCases(decisionCases);
        // Workflow instance
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("Id", "22");
        workflowModel.setInput(workflowInput);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        switchTask.getInputParameters(), workflowModel, null, null);

        TaskModel theTask = new TaskModel();
        theTask.setReferenceTaskName("Foo");
        theTask.setTaskId(idGenerator.generate());

        when(deciderService.getTasksToBeScheduled(workflowModel, task2, 0, null))
                .thenReturn(Collections.singletonList(theTask));

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(switchTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(idGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        // When
        List<TaskModel> mappedTasks = switchTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(2, mappedTasks.size());
        assertEquals("switchTask", mappedTasks.get(0).getReferenceTaskName());
        assertEquals("Foo", mappedTasks.get(1).getReferenceTaskName());
    }

    @Test
    public void getMappedTasksWithValueParamEvaluator() {

        // Given
        // Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("Id", "${workflow.input.Id}");
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        // Switch task instance
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType(TaskType.SWITCH.name());
        switchTask.setName("Switch");
        switchTask.setTaskReferenceName("switchTask");
        switchTask.setDefaultCase(Collections.singletonList(task1));
        switchTask.getInputParameters().put("Id", "${workflow.input.Id}");
        switchTask.setEvaluatorType(ValueParamEvaluator.NAME);
        switchTask.setExpression("Id");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Collections.singletonList(task2));
        decisionCases.put("odd", Collections.singletonList(task3));
        switchTask.setDecisionCases(decisionCases);
        // Workflow instance
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("Id", "even");
        workflowModel.setInput(workflowInput);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        switchTask.getInputParameters(), workflowModel, null, null);

        TaskModel theTask = new TaskModel();
        theTask.setReferenceTaskName("Foo");
        theTask.setTaskId(idGenerator.generate());

        when(deciderService.getTasksToBeScheduled(workflowModel, task2, 0, null))
                .thenReturn(Collections.singletonList(theTask));

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(switchTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(idGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        // When
        List<TaskModel> mappedTasks = switchTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(2, mappedTasks.size());
        assertEquals("switchTask", mappedTasks.get(0).getReferenceTaskName());
        assertEquals("Foo", mappedTasks.get(1).getReferenceTaskName());
    }

    @Test
    public void getMappedTasksWithEmptyMatchedCase() {
        // A case key matches but has no tasks — should NOT fall through to defaultCase.
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType(TaskType.SWITCH.name());
        switchTask.setName("Switch");
        switchTask.setTaskReferenceName("switchTask");
        switchTask.setDefaultCase(Collections.singletonList(task1));
        switchTask.setEvaluatorType(JavascriptEvaluator.NAME);
        switchTask.setExpression("'true'");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("true", Collections.emptyList());
        switchTask.setDecisionCases(decisionCases);

        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        switchTask.getInputParameters(), workflowModel, null, null);

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(switchTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(idGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = switchTaskMapper.getMappedTasks(taskMapperContext);

        // Only the SWITCH task itself; defaultCase task must not be scheduled.
        assertEquals(1, mappedTasks.size());
        assertEquals("switchTask", mappedTasks.get(0).getReferenceTaskName());
    }

    @Test
    public void getMappedTasksWhenEvaluatorThrowsException() {

        // Given
        // Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        // Switch task instance
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType(TaskType.SWITCH.name());
        switchTask.setName("Switch");
        switchTask.setTaskReferenceName("switchTask");
        switchTask.setDefaultCase(Collections.singletonList(task1));
        switchTask.setEvaluatorType(JavascriptEvaluator.NAME);
        switchTask.setExpression("undefinedVariable");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Collections.singletonList(task2));
        switchTask.setDecisionCases(decisionCases);
        // Workflow instance
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);

        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        switchTask.getInputParameters(), workflowModel, null, null);

        TaskModel theTask = new TaskModel();
        theTask.setReferenceTaskName("Foo");
        theTask.setTaskId(idGenerator.generate());

        when(deciderService.getTasksToBeScheduled(workflowModel, task2, 0, null))
                .thenReturn(Collections.singletonList(theTask));

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(switchTask)
                        .withTaskInput(input)
                        .withRetryCount(0)
                        .withTaskId(idGenerator.generate())
                        .withDeciderService(deciderService)
                        .build();

        // When
        List<TaskModel> mappedTasks = switchTaskMapper.getMappedTasks(taskMapperContext);

        // Then
        assertEquals(1, mappedTasks.size());
        assertEquals("switchTask", mappedTasks.get(0).getReferenceTaskName());
        assertEquals(TaskModel.Status.FAILED, mappedTasks.get(0).getStatus());
    }

    @Test
    public void valueParamSwitchIsEvaluatedInTheMapperAndBranchScheduledInTheSameCall() {
        WorkflowTask switchTask = switchTask(ValueParamEvaluator.NAME, "route");
        WorkflowModel workflowModel = workflowWithInput("route", "even");
        Map<String, Object> input =
                parametersUtils.getTaskInput(
                        switchTask.getInputParameters(), workflowModel, null, null);
        TaskModel branchTask = new TaskModel();
        branchTask.setReferenceTaskName("t2");
        when(deciderService.getTasksToBeScheduled(workflowModel, task2, 0, null))
                .thenReturn(Collections.singletonList(branchTask));

        List<TaskModel> mapped =
                switchTaskMapper.getMappedTasks(context(switchTask, workflowModel, input));

        assertEquals(2, mapped.size());
        TaskModel sw = mapped.get(0);
        assertEquals(TaskModel.Status.IN_PROGRESS, sw.getStatus());
        assertEquals("even", sw.getOutputData().get("selectedCase"));
        assertEquals("true", sw.getInputData().get("hasChildren"));
        assertEquals("t2", mapped.get(1).getReferenceTaskName());
        assertFalse("regular SWITCH is never async", new Switch().isAsync());
    }

    @Test
    public void structuredResultIsEvaluatedInMapperAndSchedulesSelectedBranch() {
        Map<String, Object> output =
                Map.of(
                        "selectedCase",
                        "even",
                        "model",
                        "jev-1.13",
                        "answers",
                        Map.of("route", Map.of("choice", "even")),
                        "usage",
                        Map.of("inputTokens", 10),
                        "latencyMs",
                        42L);
        Evaluator decision =
                (expression, input) -> {
                    assertEquals("route", expression);
                    assertEquals(Map.of("route", "refund"), input);
                    return output;
                };
        SwitchTaskMapper mapper = new SwitchTaskMapper(Map.of("decision", decision));
        WorkflowTask switchTask = switchTask("decision", "route");
        WorkflowModel workflowModel = workflowWithInput("route", "refund");
        TaskModel branchTask = new TaskModel();
        branchTask.setReferenceTaskName("t2");
        when(deciderService.getTasksToBeScheduled(workflowModel, task2, 0, null))
                .thenReturn(List.of(branchTask));

        List<TaskModel> mapped =
                mapper.getMappedTasks(
                        context(switchTask, workflowModel, Map.of("route", "refund")));

        assertEquals(2, mapped.size());
        TaskModel sw = mapped.get(0);
        assertEquals(TaskModel.Status.IN_PROGRESS, sw.getStatus());
        output.forEach((key, value) -> assertEquals(value, sw.getOutputData().get(key)));
        assertEquals(List.of("even"), sw.getOutputData().get(Switch.EVALUATION_RESULT));
        assertEquals("even", sw.getInputData().get("case"));
        assertEquals("true", sw.getInputData().get(Switch.HAS_CHILDREN));
        assertEquals(branchTask, mapped.get(1));
        assertFalse(new Switch().isAsync());
    }

    @Test
    public void structuredResultWithUnmatchedCaseSchedulesDefault() {
        SwitchTaskMapper mapper =
                new SwitchTaskMapper(
                        Map.of(
                                "decision",
                                (expression, input) -> Map.of("selectedCase", "unknown")));
        WorkflowTask switchTask = switchTask("decision", "route");
        WorkflowModel workflowModel = workflowWithInput("route", "refund");
        TaskModel branchTask = new TaskModel();
        when(deciderService.getTasksToBeScheduled(workflowModel, task1, 0, null))
                .thenReturn(List.of(branchTask));

        List<TaskModel> mapped =
                mapper.getMappedTasks(
                        context(switchTask, workflowModel, Map.of("route", "refund")));

        assertEquals(List.of(mapped.get(0), branchTask), mapped);
        assertEquals("unknown", mapped.get(0).getOutputData().get(Switch.SELECTED_CASE));
    }

    @Test
    public void structuredResultWithEmptyMatchedCaseDoesNotScheduleDefault() {
        SwitchTaskMapper mapper =
                new SwitchTaskMapper(
                        Map.of("decision", (expression, input) -> Map.of("selectedCase", "even")));
        WorkflowTask switchTask = switchTask("decision", "route");
        switchTask.setDecisionCases(Map.of("even", List.of()));
        WorkflowModel workflowModel = workflowWithInput("route", "refund");

        List<TaskModel> mapped =
                mapper.getMappedTasks(
                        context(switchTask, workflowModel, Map.of("route", "refund")));

        assertEquals(1, mapped.size());
        assertEquals("even", mapped.get(0).getOutputData().get(Switch.SELECTED_CASE));
        assertNull(mapped.get(0).getInputData().get(Switch.HAS_CHILDREN));
        verify(deciderService, never()).getTasksToBeScheduled(any(), any(), anyInt(), any());
    }

    private WorkflowTask switchTask(String evaluatorType, String expression) {
        WorkflowTask switchTask = new WorkflowTask();
        switchTask.setType(TaskType.SWITCH.name());
        switchTask.setName("Switch");
        switchTask.setTaskReferenceName("switchTask");
        switchTask.getInputParameters().put("route", "${workflow.input.route}");
        switchTask.setEvaluatorType(evaluatorType);
        switchTask.setExpression(expression);
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Collections.singletonList(task2));
        decisionCases.put("odd", Collections.singletonList(task3));
        switchTask.setDecisionCases(decisionCases);
        switchTask.setDefaultCase(Collections.singletonList(task1));
        return switchTask;
    }

    private WorkflowModel workflowWithInput(String key, String value) {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);
        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(workflowDef);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put(key, value);
        workflowModel.setInput(workflowInput);
        return workflowModel;
    }

    private TaskMapperContext context(
            WorkflowTask switchTask, WorkflowModel workflowModel, Map<String, Object> input) {
        return TaskMapperContext.newBuilder()
                .withWorkflowModel(workflowModel)
                .withTaskDefinition(new TaskDef())
                .withWorkflowTask(switchTask)
                .withTaskInput(input)
                .withRetryCount(0)
                .withTaskId(idGenerator.generate())
                .withDeciderService(deciderService)
                .build();
    }
}
