/*
 * Copyright 2022 Netflix, Inc.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;
import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_JOIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class ForkJoinDynamicTaskMapperTest {

    private ParametersUtils parametersUtils;
    private ObjectMapper objectMapper;
    private DeciderService deciderService;
    private ForkJoinDynamicTaskMapper forkJoinDynamicTaskMapper;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        MetadataDAO metadataDAO = Mockito.mock(MetadataDAO.class);
        parametersUtils = Mockito.mock(ParametersUtils.class);
        objectMapper = Mockito.mock(ObjectMapper.class);
        deciderService = Mockito.mock(DeciderService.class);

        forkJoinDynamicTaskMapper =
                new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper, metadataDAO);
    }

    @Test
    public void getMappedTasksException() {

        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(def);

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

        when(deciderService.getTasksToBeScheduled(workflowInstance, wt2, 0))
                .thenReturn(Collections.singletonList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowInstance, wt3, 0))
                .thenReturn(Collections.singletonList(simpleTask2));

        String taskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(def)
                        .withWorkflowInstance(workflowInstance)
                        .withTaskToSchedule(dynamicForkJoinToSchedule)
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

        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(def);

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

        when(deciderService.getTasksToBeScheduled(workflowInstance, wt2, 0))
                .thenReturn(Collections.singletonList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowInstance, wt3, 0))
                .thenReturn(Collections.singletonList(simpleTask2));

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(def)
                        .withWorkflowInstance(workflowInstance)
                        .withTaskToSchedule(dynamicForkJoinToSchedule)
                        .withRetryCount(0)
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
                        dynamicForkJoinToSchedule, new WorkflowModel());
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
                dynamicForkJoinToSchedule, new WorkflowModel());
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

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> dynamicTasks =
                forkJoinDynamicTaskMapper.getDynamicForkTasksAndInput(
                        dynamicForkJoinToSchedule, new WorkflowModel(), "dynamicTasks");

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
                dynamicForkJoinToSchedule, new WorkflowModel(), "dynamicTasks");
    }

    @Test
    public void testDynamicTaskDuplicateTaskRefName() {
        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowModel workflowInstance = new WorkflowModel();
        workflowInstance.setWorkflowDefinition(def);

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
        when(deciderService.getTasksToBeScheduled(workflowInstance, wt2, 0))
                .thenReturn(Lists.newArrayList());

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(def)
                        .withWorkflowInstance(workflowInstance)
                        .withTaskToSchedule(dynamicForkJoinToSchedule)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        expectedException.expect(TerminateWorkflowException.class);
        forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);
    }
}
