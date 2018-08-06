package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SimpleTaskMapperTest {

    ParametersUtils parametersUtils;
    MetadataDAO metadataDAO;

    //subject
    SimpleTaskMapper simpleTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);
        simpleTaskMapper = new SimpleTaskMapper(parametersUtils, metadataDAO);
    }

    @Test
    public void getMappedTasks() throws Exception {

        WorkflowTask  taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("simple_task");

        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        when(metadataDAO.getTaskDef("simple_task")).thenReturn(new TaskDef());
        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(new WorkflowDef())
                .withWorkflowInstance(new Workflow())
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(new HashMap<>())
                .withRetryCount(0)
                .withRetryTaskId(retriedTaskId)
                .withTaskId(taskId)
                .build();

        List<Task> mappedTasks = simpleTaskMapper.getMappedTasks(taskMapperContext);
        assertNotNull(mappedTasks);
        assertEquals(1, mappedTasks.size());
    }

    @Test
    public void getMappedTasksException() throws Exception {

        //Given
        WorkflowTask  taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("simple_task");
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(new WorkflowDef())
                .withWorkflowInstance(new Workflow())
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(new HashMap<>())
                .withRetryCount(0)
                .withRetryTaskId(retriedTaskId)
                .withTaskId(taskId)
                .build();

        when(metadataDAO.getTaskDef("simple_task")).thenReturn(null);
        //then
        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("Invalid task specified. Cannot find task by name %s in the task definitions", taskToSchedule.getName()));

        //when
        simpleTaskMapper.getMappedTasks(taskMapperContext);


    }

}