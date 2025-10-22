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

import java.util.*;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class ForkJoinDynamicTaskMapperTest {

    private IDGenerator idGenerator;
    private ParametersUtils parametersUtils;
    private ObjectMapper objectMapper;
    private DeciderService deciderService;
    private ForkJoinDynamicTaskMapper forkJoinDynamicTaskMapper;
    private SystemTaskRegistry systemTaskRegistry;
    private MetadataDAO metadataDAO;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        metadataDAO = Mockito.mock(MetadataDAO.class);
        idGenerator = new IDGenerator();
        parametersUtils = Mockito.mock(ParametersUtils.class);
        objectMapper = Mockito.mock(ObjectMapper.class);
        deciderService = Mockito.mock(DeciderService.class);
        systemTaskRegistry = Mockito.mock(SystemTaskRegistry.class);

        forkJoinDynamicTaskMapper =
                new ForkJoinDynamicTaskMapper(
                        idGenerator,
                        parametersUtils,
                        objectMapper,
                        metadataDAO,
                        systemTaskRegistry);
    }

    @Test
    public void getMappedTasksException() {

        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(def);

        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamictask_join");

        def.getTasks().add(dynamicForkJoinToSchedule);

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        // when
        when(parametersUtils.getTaskInput(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class)))
                .thenReturn(Arrays.asList(wt2, wt3));

        TaskModel simpleTask1 = new TaskModel();
        simpleTask1.setReferenceTaskName("xdt1");

        TaskModel simpleTask2 = new TaskModel();
        simpleTask2.setReferenceTaskName("xdt2");

        when(deciderService.getTasksToBeScheduled(workflowModel, wt2, 0))
                .thenReturn(Collections.singletonList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowModel, wt3, 0))
                .thenReturn(Collections.singletonList(simpleTask2));

        String taskId = idGenerator.generate();

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withTaskInput(Map.of())
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(dynamicForkJoinToSchedule)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        // then
        expectedException.expect(TerminateWorkflowException.class);
        forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);
    }

    @Test
    public void getMappedTasks() {

        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(def);

        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamictask_join");

        def.getTasks().add(dynamicForkJoinToSchedule);
        def.getTasks().add(join);

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        // when
        when(parametersUtils.getTaskInput(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(dynamicTasksInput);
        when(objectMapper.convertValue(any(), any(TypeReference.class)))
                .thenReturn(Arrays.asList(wt2, wt3));

        TaskModel simpleTask1 = new TaskModel();
        simpleTask1.setReferenceTaskName("xdt1");

        TaskModel simpleTask2 = new TaskModel();
        simpleTask2.setReferenceTaskName("xdt2");

        when(deciderService.getTasksToBeScheduled(workflowModel, wt2, 0))
                .thenReturn(Collections.singletonList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowModel, wt3, 0))
                .thenReturn(Collections.singletonList(simpleTask2));

        String taskId = idGenerator.generate();
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(dynamicForkJoinToSchedule)
                        .withRetryCount(0)
                        .withTaskInput(Map.of())
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        // then
        List<TaskModel> mappedTasks = forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(4, mappedTasks.size());

        assertEquals(TASK_TYPE_FORK, mappedTasks.get(0).getTaskType());
        assertEquals(TASK_TYPE_JOIN, mappedTasks.get(3).getTaskType());
        List<String> joinTaskNames = (List<String>) mappedTasks.get(3).getInputData().get("joinOn");
        assertEquals("xdt1, xdt2", String.join(", ", joinTaskNames));
    }

    @Test
    public void getDynamicForkJoinTasksAndInput() {
        // Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkJoinTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        DynamicForkJoinTaskList dtasks = new DynamicForkJoinTaskList();

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "v1");
        dtasks.add("junit_task_2", null, "xdt1", input);

        HashMap<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");
        dtasks.add("junit_task_3", null, "xdt2", input2);

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("dynamicTasks", dtasks);

        // when
        when(parametersUtils.getTaskInput(
                        anyMap(), any(WorkflowModel.class), any(TaskDef.class), anyString()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(Class.class))).thenReturn(dtasks);

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> dynamicForkJoinTasksAndInput =
                forkJoinDynamicTaskMapper.getDynamicForkJoinTasksAndInput(
                        dynamicForkJoinToSchedule, new WorkflowModel(), Map.of());
        // then
        assertNotNull(dynamicForkJoinTasksAndInput.getLeft());
        assertEquals(2, dynamicForkJoinTasksAndInput.getLeft().size());
        assertEquals(2, dynamicForkJoinTasksAndInput.getRight().size());
    }

    @Test
    public void getDynamicForkJoinTasksAndInputException() {
        // Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkJoinTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        DynamicForkJoinTaskList dtasks = new DynamicForkJoinTaskList();

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "v1");
        dtasks.add("junit_task_2", null, "xdt1", input);

        HashMap<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");
        dtasks.add("junit_task_3", null, "xdt2", input2);

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("dynamicTasks", dtasks);

        // when
        when(parametersUtils.getTaskInput(
                        anyMap(), any(WorkflowModel.class), any(TaskDef.class), anyString()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(Class.class))).thenReturn(null);

        // then
        expectedException.expect(TerminateWorkflowException.class);

        forkJoinDynamicTaskMapper.getDynamicForkJoinTasksAndInput(
                dynamicForkJoinToSchedule, new WorkflowModel(), Map.of());
    }

    @Test
    public void getDynamicForkTasksAndInput() {
        // Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        // when
        when(parametersUtils.getTaskInput(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class)))
                .thenReturn(Arrays.asList(wt2, wt3));

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> dynamicTasks =
                forkJoinDynamicTaskMapper.getDynamicForkTasksAndInput(
                        dynamicForkJoinToSchedule,
                        new WorkflowModel(),
                        "dynamicTasks",
                        dynamicTasksInput);

        // then
        assertNotNull(dynamicTasks.getLeft());
    }

    @Test
    public void getDynamicForkTasksAndInputException() {

        // Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", null);

        when(parametersUtils.getTaskInput(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class)))
                .thenReturn(Arrays.asList(wt2, wt3));
        // then
        expectedException.expect(TerminateWorkflowException.class);
        // when
        forkJoinDynamicTaskMapper.getDynamicForkTasksAndInput(
                dynamicForkJoinToSchedule, new WorkflowModel(), "dynamicTasks", Map.of());
    }

    @Test
    public void testDynamicTaskDuplicateTaskRefName() {
        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowModel workflowModel = new WorkflowModel();
        workflowModel.setWorkflowDefinition(def);

        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule
                .getInputParameters()
                .put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamictask_join");

        def.getTasks().add(dynamicForkJoinToSchedule);
        def.getTasks().add(join);

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        // dynamic
        when(parametersUtils.getTaskInput(anyMap(), any(WorkflowModel.class), any(), any()))
                .thenReturn(dynamicTasksInput);
        when(objectMapper.convertValue(any(), any(TypeReference.class)))
                .thenReturn(Arrays.asList(wt2, wt3));

        TaskModel simpleTask1 = new TaskModel();
        simpleTask1.setReferenceTaskName("xdt1");

        // Empty list, this is a bad state, workflow should terminate
        when(deciderService.getTasksToBeScheduled(workflowModel, wt2, 0))
                .thenReturn(new ArrayList<>());

        String taskId = idGenerator.generate();
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withTaskInput(Map.of())
                        .withWorkflowModel(workflowModel)
                        .withWorkflowTask(dynamicForkJoinToSchedule)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        expectedException.expect(TerminateWorkflowException.class);
        forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);
    }

    @Test
    public void dynamicForkInputsRemainUnwrappedWhenMapsProvided() {
        ObjectMapper realObjectMapper = new ObjectMapperProvider().getObjectMapper();
        ForkJoinDynamicTaskMapper mapper =
                new ForkJoinDynamicTaskMapper(
                        idGenerator,
                        parametersUtils,
                        realObjectMapper,
                        metadataDAO,
                        systemTaskRegistry);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("fork_join_dynamic");
        workflowTask.setType(TaskType.FORK_JOIN_DYNAMIC.name());

        Map<String, Object> forkInput1 = new HashMap<>();
        forkInput1.put("param1", "value1");
        Map<String, Object> forkInput2 = new HashMap<>();
        forkInput2.put("param1", "value2");

        Map<String, Object> mapperInput = new HashMap<>();
        mapperInput.put("forkTaskWorkflow", "sub_workflow_definition_name");
        mapperInput.put("forkTaskWorkflowVersion", "1");
        mapperInput.put("forkTaskInputs", Arrays.asList(forkInput1, forkInput2));

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> result =
                mapper.getDynamicTasksSimple(workflowTask, mapperInput);

        assertNotNull(result);
        result.getLeft()
                .forEach(task -> assertFalse(task.getInputParameters().containsKey("input")));
        result.getRight().values().forEach(input -> assertFalse(input.containsKey("input")));
        WorkflowTask firstTask = result.getLeft().get(0);
        WorkflowTask secondTask = result.getLeft().get(1);
        assertEquals("value1", firstTask.getInputParameters().get("param1"));
        assertEquals("value2", secondTask.getInputParameters().get("param1"));
        assertEquals(
                "value1", result.getRight().get(firstTask.getTaskReferenceName()).get("param1"));
        assertEquals(
                "value2", result.getRight().get(secondTask.getTaskReferenceName()).get("param1"));
    }

    @Test
    public void dynamicForkInputsRemainUnwrappedWhenInlineTaskOutputProvided() {
        ObjectMapper realObjectMapper = new ObjectMapperProvider().getObjectMapper();
        ForkJoinDynamicTaskMapper mapper =
                new ForkJoinDynamicTaskMapper(
                        idGenerator,
                        parametersUtils,
                        realObjectMapper,
                        metadataDAO,
                        systemTaskRegistry);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("fork_join_dynamic");
        workflowTask.setType(TaskType.FORK_JOIN_DYNAMIC.name());

        // Simulate INLINE task output after normalization
        // The normalization converts JavaScript objects to plain Java Maps
        Map<String, Object> inlineOutput1 = new HashMap<>();
        inlineOutput1.put("param1", "value1");
        inlineOutput1.put("param2", "data1");

        Map<String, Object> inlineOutput2 = new HashMap<>();
        inlineOutput2.put("param1", "value2");
        inlineOutput2.put("param2", "data2");

        Map<String, Object> mapperInput = new HashMap<>();
        mapperInput.put("forkTaskWorkflow", "sub_workflow_definition_name");
        mapperInput.put("forkTaskWorkflowVersion", "1");
        mapperInput.put("forkTaskInputs", Arrays.asList(inlineOutput1, inlineOutput2));

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> result =
                mapper.getDynamicTasksSimple(workflowTask, mapperInput);

        assertNotNull(result);
        // Verify that inputs are not wrapped under an "input" key
        result.getLeft()
                .forEach(task -> assertFalse(task.getInputParameters().containsKey("input")));
        result.getRight().values().forEach(input -> assertFalse(input.containsKey("input")));

        // Verify that the parameters are directly accessible
        WorkflowTask firstTask = result.getLeft().get(0);
        WorkflowTask secondTask = result.getLeft().get(1);
        assertEquals("value1", firstTask.getInputParameters().get("param1"));
        assertEquals("data1", firstTask.getInputParameters().get("param2"));
        assertEquals("value2", secondTask.getInputParameters().get("param1"));
        assertEquals("data2", secondTask.getInputParameters().get("param2"));
        assertEquals(
                "value1", result.getRight().get(firstTask.getTaskReferenceName()).get("param1"));
        assertEquals(
                "data1", result.getRight().get(firstTask.getTaskReferenceName()).get("param2"));
        assertEquals(
                "value2", result.getRight().get(secondTask.getTaskReferenceName()).get("param1"));
        assertEquals(
                "data2", result.getRight().get(secondTask.getTaskReferenceName()).get("param2"));
    }
}
