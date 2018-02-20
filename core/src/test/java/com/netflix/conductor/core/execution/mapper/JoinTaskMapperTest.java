package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class JoinTaskMapperTest {


    @Test
    public void getMappedTasks() throws Exception {

        WorkflowTask taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(WorkflowTask.Type.JOIN.name());
        taskToSchedule.setJoinOn(Arrays.asList("task1, task2"));

        String taskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext = new TaskMapperContext(new WorkflowDef(), new Workflow(), taskToSchedule,
                null, 0, null, taskId, null);

        List<Task> mappedTasks = new JoinTaskMapper().getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(SystemTaskType.JOIN.name(), mappedTasks.get(0).getTaskType());
    }

}