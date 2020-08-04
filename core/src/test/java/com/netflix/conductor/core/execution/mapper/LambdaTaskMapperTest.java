package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

public class LambdaTaskMapperTest {

    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);
    }

    @Test
    public void getMappedTasks() throws Exception {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("lambda_task");
        taskToSchedule.setType(TaskType.LAMBDA.name());
        taskToSchedule.setTaskDefinition(new TaskDef("lambda_task"));
        taskToSchedule.setScriptExpression("if ($.input.a==1){return {testValue: true}} else{return {testValue: false} }");

        String taskId = IDGenerator.generate();

        WorkflowDef  wd = new WorkflowDef();
        Workflow w = new Workflow();
        w.setWorkflowDefinition(wd);

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(wd)
                .withWorkflowInstance(w)
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();


        List<Task> mappedTasks = new LambdaTaskMapper(parametersUtils, metadataDAO).getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertNotNull(mappedTasks);
        assertEquals(TaskType.LAMBDA.name(), mappedTasks.get(0).getTaskType());
    }

    @Test
    public void getMappedTasks_WithoutTaskDef() throws Exception {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(TaskType.LAMBDA.name());
        taskToSchedule.setScriptExpression("if ($.input.a==1){return {testValue: true}} else{return {testValue: false} }");

        String taskId = IDGenerator.generate();

        WorkflowDef  wd = new WorkflowDef();
        Workflow w = new Workflow();
        w.setWorkflowDefinition(wd);

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(wd)
                .withWorkflowInstance(w)
                .withTaskDefinition(null)
                .withTaskToSchedule(taskToSchedule)
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();


        List<Task> mappedTasks = new LambdaTaskMapper(parametersUtils, metadataDAO).getMappedTasks(taskMapperContext);

        assertEquals(1, mappedTasks.size());
        assertNotNull(mappedTasks);
        assertEquals(TaskType.LAMBDA.name(), mappedTasks.get(0).getTaskType());
    }

}
