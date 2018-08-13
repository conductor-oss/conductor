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
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DynamicTaskMapperTest {

    MetadataDAO metadataDAO;
    ParametersUtils parametersUtils;
    DynamicTaskMapper dynamicTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        metadataDAO = mock(MetadataDAO.class);
        parametersUtils = mock(ParametersUtils.class);
        dynamicTaskMapper = new DynamicTaskMapper(parametersUtils, metadataDAO);
    }

    @Test
    public void getMappedTasks() throws Exception {

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("DynoTask");
        workflowTask.setDynamicTaskNameParam("dynamicTaskName");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("DynoTask");

        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("dynamicTaskName", "DynoTask");

        when(metadataDAO.getTaskDef("DynoTask")).thenReturn(taskDef);
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(TaskDef.class), anyString())).thenReturn(taskInput);

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(new WorkflowDef())
                .withWorkflowInstance(new Workflow())
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(workflowTask)
                .withTaskInput(taskInput)
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();

        List<Task> mappedTasks = dynamicTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());

        Task dynamicTask = mappedTasks.get(0);
        assertEquals(taskId, dynamicTask.getTaskId());
    }

    @Test
    public void getDynamicTaskName() throws Exception {
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("dynamicTaskName", "DynoTask");

        String dynamicTaskName = dynamicTaskMapper.getDynamicTaskName(taskInput, "dynamicTaskName");

        assertEquals("DynoTask", dynamicTaskName);
    }

    @Test
    public void getDynamicTaskNameNotAvailable() throws Exception {
        Map<String, Object> taskInput = new HashMap<>();

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("Cannot map a dynamic task based on the parameter and input. " +
                "Parameter= %s, input= %s", "dynamicTaskName", taskInput));

        dynamicTaskMapper.getDynamicTaskName(taskInput, "dynamicTaskName");

    }

    @Test
    public void getDynamicTaskDefinition() throws Exception {
        //Given
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("Foo");
        TaskDef taskDef = new TaskDef();
        taskDef.setName("Foo");
        when(metadataDAO.getTaskDef("Foo")).thenReturn(taskDef);

        //when
        TaskDef dynamicTaskDefinition = dynamicTaskMapper.getDynamicTaskDefinition(workflowTask);

        assertEquals(dynamicTaskDefinition, taskDef);
    }

    @Test
    public void getDynamicTaskDefinitionNull() {

        //Given
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("Foo");

        when(metadataDAO.getTaskDef("Foo")).thenReturn(null);

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("Invalid task specified.  Cannot find task by name %s in the task definitions",
                workflowTask.getName()));

        dynamicTaskMapper.getDynamicTaskDefinition(workflowTask);

    }

}