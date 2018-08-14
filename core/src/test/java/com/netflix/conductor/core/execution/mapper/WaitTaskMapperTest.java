package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.tasks.Wait;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class WaitTaskMapperTest {


    @Test
    public void getMappedTasks() throws Exception {

        //Given
        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setName("Wait_task");
        taskToSchedule.setType(WorkflowTask.Type.WAIT.name());
        String taskId = IDGenerator.generate();

        ParametersUtils parametersUtils = new ParametersUtils();

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(new WorkflowDef())
                .withWorkflowInstance(new Workflow())
                .withTaskDefinition(new TaskDef())
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(new HashMap<>())
                .withRetryCount(0)
                .withTaskId(taskId)
                .build();

        WaitTaskMapper waitTaskMapper = new WaitTaskMapper(parametersUtils);
        //When
        List<Task> mappedTasks = waitTaskMapper.getMappedTasks(taskMapperContext);

        //Then
        assertEquals(1, mappedTasks.size());
        assertEquals(Wait.NAME, mappedTasks.get(0).getTaskType());




    }

}