package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ForkJoinTaskMapperTest {

    private DeciderService deciderService;

    private ForkJoinTaskMapper  forkJoinTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        deciderService = Mockito.mock(DeciderService.class);
        forkJoinTaskMapper  = new ForkJoinTaskMapper();
    }

    @Test
    public void getMappedTasks() throws Exception {

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
        forkTask.getForkTasks().add(Arrays.asList(wft2));

        def.getTasks().add(forkTask);

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("forktask_join");
        join.setJoinOn(Arrays.asList("t3","t2"));

        def.getTasks().add(join);
        def.getTasks().add(wft4);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);

        Task task1 = new Task();
        task1.setReferenceTaskName(wft1.getTaskReferenceName());

        Task task3 = new Task();
        task3.setReferenceTaskName(wft3.getTaskReferenceName());

        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft1,0)).thenReturn(Arrays.asList(task1));
        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft2,0)).thenReturn(Arrays.asList(task3));

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(def)
                .withWorkflowInstance(workflow)
                .withTaskToSchedule(forkTask)
                .withRetryCount(0)
                .withTaskId(taskId)
                .withDeciderService(deciderService)
                .build();

        List<Task> mappedTasks = forkJoinTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(3, mappedTasks.size());
        assertEquals(SystemTaskType.FORK.name(),mappedTasks.get(0).getTaskType());

    }


    @Test
    public void getMappedTasksException() throws Exception {

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
        forkTask.getForkTasks().add(Arrays.asList(wft2));

        def.getTasks().add(forkTask);

        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("forktask_join");
        join.setJoinOn(Arrays.asList("t3","t2"));

        def.getTasks().add(wft4);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);

        Task task1 = new Task();
        task1.setReferenceTaskName(wft1.getTaskReferenceName());

        Task task3 = new Task();
        task3.setReferenceTaskName(wft3.getTaskReferenceName());

        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft1,0)).thenReturn(Arrays.asList(task1));
        Mockito.when(deciderService.getTasksToBeScheduled(workflow, wft2,0)).thenReturn(Arrays.asList(task3));

        String taskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(def)
                .withWorkflowInstance(workflow)
                .withTaskToSchedule(forkTask)
                .withRetryCount(0)
                .withTaskId(taskId)
                .withDeciderService(deciderService)
                .build();

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage("Fork task definition is not followed by a join task.  Check the blueprint");
        forkJoinTaskMapper.getMappedTasks(taskMapperContext);

    }

}
