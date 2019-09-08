package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.mock;

public class TerminateTaskMapperTest {

    private ParametersUtils parametersUtils;

    @Before
    public void setUp() {
        parametersUtils = mock(ParametersUtils.class);
    }

    @Test
    public void getMappedTasks() throws Exception {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(TaskType.TASK_TYPE_TERMINATE);

        String taskId = IDGenerator.generate();

        WorkflowDef workflowDef = new WorkflowDef();
        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(workflowDef)
                .withWorkflowInstance(workflow)
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();

        List<Task> mappedTasks = new TerminateTaskMapper(parametersUtils).getMappedTasks(taskMapperContext);

        Assert.assertNotNull(mappedTasks);
        Assert.assertEquals(1, mappedTasks.size());
        Assert.assertEquals(TaskType.TASK_TYPE_TERMINATE, mappedTasks.get(0).getTaskType());
    }


}
