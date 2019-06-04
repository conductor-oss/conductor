package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DoWhileTaskMapperTest {

    private Task task1;
    private Task task2;
    private DeciderService deciderService;
    private Workflow workflow;
    private WorkflowTask workflowTask1;
    private WorkflowTask workflowTask2;
    private TaskMapperContext taskMapperContext;
    private WorkflowTask taskToSchedule;

    @Before
    public void setup() {
        taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(TaskType.DO_WHILE.name());
        taskToSchedule.setTaskReferenceName("Test");
        task1 = new Task();
        task1.setReferenceTaskName("task1");
        task2 = new Task();
        task2.setReferenceTaskName("task2");
        workflowTask1 = new WorkflowTask();
        workflowTask1.setTaskReferenceName("task1");
        workflowTask2= new WorkflowTask();
        workflowTask2.setTaskReferenceName("task2");
        task1.setWorkflowTask(workflowTask1);
        task2.setWorkflowTask(workflowTask2);
        taskToSchedule.setLoopOver(Arrays.asList(task1.getWorkflowTask(), task2.getWorkflowTask()));
        taskToSchedule.setLoopCondition("if ($.second_task + $.first_task > 10) { false; } else { true; }");

        String taskId = IDGenerator.generate();

        WorkflowDef  workflowDef = new WorkflowDef();
        workflow = new Workflow();
        workflow.setWorkflowDefinition(workflowDef);

        deciderService = Mockito.mock(DeciderService.class);

        taskMapperContext = TaskMapperContext.newBuilder()
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

        Mockito.doReturn(Arrays.asList(task1)).when(deciderService).getTasksToBeScheduled(workflow, workflowTask1, 0);
        Mockito.doReturn(Arrays.asList(task2)).when(deciderService).getTasksToBeScheduled(workflow, workflowTask2, 0);

        List<Task> mappedTasks = new DoWhileTaskMapper().getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(mappedTasks.size(), 3);
        assertEquals(task1, mappedTasks.get(0));
        assertEquals(task2, mappedTasks.get(1));
        assertEquals(SystemTaskType.DO_WHILE.name(), mappedTasks.get(2).getTaskType());
    }

    @Test
    public void shouldNotScheduleCompletedTask() {

        taskToSchedule = new WorkflowTask();
        taskToSchedule.setType(TaskType.DO_WHILE.name());
        Task task = new Task();
        task.setReferenceTaskName("Test");
        task.setStatus(Task.Status.COMPLETED);
        workflow.setTasks(Arrays.asList(task));

        List<Task> mappedTasks = new DoWhileTaskMapper().getMappedTasks(taskMapperContext);

        assertNotNull(mappedTasks);
        assertEquals(mappedTasks.size(), 0);
    }

}
