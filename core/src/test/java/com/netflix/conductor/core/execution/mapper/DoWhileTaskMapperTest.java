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
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_DO_WHILE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DoWhileTaskMapperTest {

    private TaskModel task1;
    private DeciderService deciderService;
    private WorkflowModel workflow;
    private WorkflowTask workflowTask1;
    private TaskMapperContext taskMapperContext;
    private MetadataDAO metadataDAO;

    @Before
    public void setup() {
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(TaskType.DO_WHILE.name());
        taskToSchedule.setTaskReferenceName("Test");
        task1 = new TaskModel();
        task1.setReferenceTaskName("task1");
        TaskModel task2 = new TaskModel();
        task2.setReferenceTaskName("task2");
        workflowTask1 = new WorkflowTask();
        workflowTask1.setTaskReferenceName("task1");
        WorkflowTask workflowTask2 = new WorkflowTask();
        workflowTask2.setTaskReferenceName("task2");
        task1.setWorkflowTask(workflowTask1);
        task2.setWorkflowTask(workflowTask2);
        taskToSchedule.setLoopOver(Arrays.asList(task1.getWorkflowTask(), task2.getWorkflowTask()));
        taskToSchedule.setLoopCondition(
                "if ($.second_task + $.first_task > 10) { false; } else { true; }");

        String taskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(workflowDef);

        deciderService = Mockito.mock(DeciderService.class);
        metadataDAO = Mockito.mock(MetadataDAO.class);

        taskMapperContext =
                TaskMapperContext.newBuilder()
                        .withWorkflowDefinition(workflowDef)
                        .withDeciderService(deciderService)
                        .withWorkflowInstance(workflow)
                        .withTaskDefinition(new TaskDef())
                        .withTaskToSchedule(taskToSchedule)
                        .withRetryCount(0)
                        .withTaskId(taskId)
                        .build();
    }

    @Test
    public void getMappedTasks() {

        Mockito.doReturn(Collections.singletonList(task1))
                .when(deciderService)
                .getTasksToBeScheduled(workflow, workflowTask1, 0);

        List<TaskModel> mappedTasks =
                new DoWhileTaskMapper(metadataDAO).getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(mappedTasks.size(), 2);
        assertEquals("task1__1", mappedTasks.get(1).getReferenceTaskName());
        assertEquals(1, mappedTasks.get(1).getIteration());
        assertEquals(TASK_TYPE_DO_WHILE, mappedTasks.get(0).getTaskType());
    }

    @Test
    public void shouldNotScheduleCompletedTask() {

        task1.setStatus(TaskModel.Status.COMPLETED);

        List<TaskModel> mappedTasks =
                new DoWhileTaskMapper(metadataDAO).getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(mappedTasks.size(), 1);
    }

    @Test
    public void testAppendIteration() {
        assertEquals("task__1", TaskUtils.appendIteration("task", 1));
    }
}
