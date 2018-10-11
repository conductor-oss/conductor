package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class UserDefinedTaskMapperTest {

    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;
    private UserDefinedTaskMapper userDefinedTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);
        userDefinedTaskMapper = new UserDefinedTaskMapper(parametersUtils, metadataDAO);
    }

    @Test
    public void getMappedTasks() {
        //Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("user_task");
        taskToSchedule.setType(TaskType.USER_DEFINED.name());
        taskToSchedule.setTaskDefinition(new TaskDef("user_task"));
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        Workflow workflow = new Workflow();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(workflowDef)
                .withWorkflowInstance(workflow)
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(new HashMap<>())
                .withRetryCount(0)
                .withRetryTaskId(retriedTaskId)
                .withTaskId(taskId)
                .build();

        //when
        List<Task> mappedTasks = userDefinedTaskMapper.getMappedTasks(taskMapperContext);

        //Then
        assertEquals(1, mappedTasks.size());
        assertEquals(TaskType.USER_DEFINED.name(), mappedTasks.get(0).getTaskType());
    }

    @Test
    public void getMappedTasksException() {
        //Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("user_task");
        taskToSchedule.setType(TaskType.USER_DEFINED.name());
        String taskId = IDGenerator.generate();
        String retriedTaskId = IDGenerator.generate();

        Workflow workflow = new Workflow();
        WorkflowDef workflowDef = new WorkflowDef();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(workflowDef)
                .withWorkflowInstance(workflow)
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(new HashMap<>())
                .withRetryCount(0)
                .withRetryTaskId(retriedTaskId)
                .withTaskId(taskId)
                .build();

        //then
        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("Invalid task specified. Cannot find task by name %s in the task definitions", taskToSchedule.getName()));
        //when
        userDefinedTaskMapper.getMappedTasks(taskMapperContext);

    }
}
