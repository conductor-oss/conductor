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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_FORK;

import static org.junit.Assert.assertEquals;

public class ForkJoinTaskMapperTest {

    private DeciderService deciderService;
    private ForkJoinTaskMapper forkJoinTaskMapper;

    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        deciderService = Mockito.mock(DeciderService.class);
        forkJoinTaskMapper = new ForkJoinTaskMapper();
    }

    @Test
    public void getMappedTasks() {

        WorkflowDef def = new WorkflowDef();
        def.setName("FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType(TaskType.FORK_JOIN.name());
        forkTask.setTaskReferenceName("forktask");

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        wft3.setInputParameters(ip1);
        wft3.setTaskReferenceName("t3");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "workflow.input.param1");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        WorkflowTask wft4 = new WorkflowTask();
        wft4.setName("junit_task_4");
        wft4.setInputParameters(ip2);
        wft4.setTaskReferenceName("t4");

        forkTask.getForkTasks().add(Arrays.asList(wft1, wft3));
        forkTask.getForkTasks().add(Collections.singletonList(wft2));

        def.getTasks().add(forkTask);

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("forktask_join");
        join.setJoinOn(Arrays.asList("t3", "t2"));

        def.getTasks().add(join);
        def.getTasks().add(wft4);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);

        TaskModel task1 = new TaskModel();
        task1.setReferenceTaskName(wft1.getTaskReferenceName());

        TaskModel task3 = new TaskModel();
        task3.setReferenceTaskName(wft3.getTaskReferenceName());

        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft1, 0))
                .thenReturn(Collections.singletonList(task1));
        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft2, 0))
                .thenReturn(Collections.singletonList(task3));

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(def)
                        .withWorkflowInstance(workflow)
                        .withTaskToSchedule(forkTask)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        List<TaskModel> mappedTasks = forkJoinTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(3, mappedTasks.size());
        assertEquals(TASK_TYPE_FORK, mappedTasks.get(0).getTaskType());
    }

    @Test
    public void getMappedTasksException() {

        WorkflowDef def = new WorkflowDef();
        def.setName("FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        WorkflowTask forkTask = new WorkflowTask();
        forkTask.setType(TaskType.FORK_JOIN.name());
        forkTask.setTaskReferenceName("forktask");

        WorkflowTask wft1 = new WorkflowTask();
        wft1.setName("junit_task_1");
        Map<String, Object> ip1 = new HashMap<>();
        ip1.put("p1", "workflow.input.param1");
        ip1.put("p2", "workflow.input.param2");
        wft1.setInputParameters(ip1);
        wft1.setTaskReferenceName("t1");

        WorkflowTask wft3 = new WorkflowTask();
        wft3.setName("junit_task_3");
        wft3.setInputParameters(ip1);
        wft3.setTaskReferenceName("t3");

        WorkflowTask wft2 = new WorkflowTask();
        wft2.setName("junit_task_2");
        Map<String, Object> ip2 = new HashMap<>();
        ip2.put("tp1", "workflow.input.param1");
        wft2.setInputParameters(ip2);
        wft2.setTaskReferenceName("t2");

        WorkflowTask wft4 = new WorkflowTask();
        wft4.setName("junit_task_4");
        wft4.setInputParameters(ip2);
        wft4.setTaskReferenceName("t4");

        forkTask.getForkTasks().add(Arrays.asList(wft1, wft3));
        forkTask.getForkTasks().add(Collections.singletonList(wft2));

        def.getTasks().add(forkTask);

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("forktask_join");
        join.setJoinOn(Arrays.asList("t3", "t2"));

        def.getTasks().add(wft4);

        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(def);

        TaskModel task1 = new TaskModel();
        task1.setReferenceTaskName(wft1.getTaskReferenceName());

        TaskModel task3 = new TaskModel();
        task3.setReferenceTaskName(wft3.getTaskReferenceName());

        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft1, 0))
                .thenReturn(Collections.singletonList(task1));
        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft2, 0))
                .thenReturn(Collections.singletonList(task3));

        String taskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(def)
                        .withWorkflowInstance(workflow)
                        .withTaskToSchedule(forkTask)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .withDeciderService(deciderService)
                        .build();

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(
                "Fork task definition is not followed by a join task.  Check the blueprint");
        forkJoinTaskMapper.getMappedTasks(taskMapperContext);
    }
}
