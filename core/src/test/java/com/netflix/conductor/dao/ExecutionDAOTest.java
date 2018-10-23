package com.netflix.conductor.dao;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.ApplicationException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public abstract class ExecutionDAOTest {

    abstract protected ExecutionDAO getExecutionDAO();

    @Rule
    public ExpectedException expected = ExpectedException.none();

    @Test
    public void testTaskExceedsLimit() {
        TaskDef taskDefinition = new TaskDef();
        taskDefinition.setName("task1");
        taskDefinition.setConcurrentExecLimit(1);

        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("task1");
        workflowTask.setTaskDefinition(taskDefinition);
        workflowTask.setTaskDefinition(taskDefinition);

        List<Task> tasks = new LinkedList<>();
        for (int i = 0; i < 15; i++) {
            Task task = new Task();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId("t_" + i);
            task.setWorkflowInstanceId("workflow_" + i);
            task.setReferenceTaskName("task1");
            task.setTaskDefName("task1");
            tasks.add(task);
            task.setStatus(Task.Status.SCHEDULED);
            task.setWorkflowTask(workflowTask);
        }

        getExecutionDAO().createTasks(tasks);
        assertFalse(getExecutionDAO().exceedsInProgressLimit(tasks.get(0)));
        tasks.get(0).setStatus(Task.Status.IN_PROGRESS);
        getExecutionDAO().updateTask(tasks.get(0));

        for (Task task : tasks) {
            assertTrue(getExecutionDAO().exceedsInProgressLimit(task));
        }
    }

    @Test
    public void testCreateTaskException() {
        Task task = new Task();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId("t1");
        task.setTaskDefName("task1");

        expected.expect(ApplicationException.class);
        expected.expectMessage("Workflow instance id cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));

        task.setWorkflowInstanceId("wfid");
        expected.expect(ApplicationException.class);
        expected.expectMessage("Task reference name cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));
    }

    @Test
    public void testCreateTaskException2() {
        Task task = new Task();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId("t1");
        task.setTaskDefName("task1");
        task.setWorkflowInstanceId("wfid");

        expected.expect(ApplicationException.class);
        expected.expectMessage("Task reference name cannot be null");
        getExecutionDAO().createTasks(Collections.singletonList(task));
    }

    @Test
    public void testPollData() {
        getExecutionDAO().updateLastPoll("taskDef", null, "workerId1");
        PollData pd = getExecutionDAO().getPollData("taskDef", null);
        assertNotNull(pd);
        assertTrue(pd.getLastPollTime() > 0);
        assertEquals(pd.getQueueName(), "taskDef");
        assertEquals(pd.getDomain(), null);
        assertEquals(pd.getWorkerId(), "workerId1");

        getExecutionDAO().updateLastPoll("taskDef", "domain1", "workerId1");
        pd = getExecutionDAO().getPollData("taskDef", "domain1");
        assertNotNull(pd);
        assertTrue(pd.getLastPollTime() > 0);
        assertEquals(pd.getQueueName(), "taskDef");
        assertEquals(pd.getDomain(), "domain1");
        assertEquals(pd.getWorkerId(), "workerId1");

        List<PollData> pData = getExecutionDAO().getPollData("taskDef");
        assertEquals(pData.size(), 2);

        pd = getExecutionDAO().getPollData("taskDef", "domain2");
        assertTrue(pd == null);
    }

    @Test
    public void testTaskCreateDups() {
        List<Task> tasks = new LinkedList<>();
        String workflowId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            Task task = new Task();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId(workflowId + "_t" + i);
            task.setReferenceTaskName("t" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId(workflowId);
            task.setTaskDefName("task" + i);
            task.setStatus(Task.Status.IN_PROGRESS);
            tasks.add(task);
        }

        //Let's insert a retried task
        Task task = new Task();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(workflowId + "_t" + 2);
        task.setReferenceTaskName("t" + 2);
        task.setRetryCount(1);
        task.setWorkflowInstanceId(workflowId);
        task.setTaskDefName("task" + 2);
        task.setStatus(Task.Status.IN_PROGRESS);
        tasks.add(task);

        //Duplicate task!
        task = new Task();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId(workflowId + "_t" + 1);
        task.setReferenceTaskName("t" + 1);
        task.setRetryCount(0);
        task.setWorkflowInstanceId(workflowId);
        task.setTaskDefName("task" + 1);
        task.setStatus(Task.Status.IN_PROGRESS);
        tasks.add(task);

        List<Task> created = getExecutionDAO().createTasks(tasks);
        assertEquals(tasks.size() - 1, created.size());    //1 less

        Set<String> srcIds = tasks.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());
        Set<String> createdIds = created.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());

        assertEquals(srcIds, createdIds);

        List<Task> pending = getExecutionDAO().getPendingTasksByWorkflow("task0", workflowId);
        assertNotNull(pending);
        assertEquals(1, pending.size());
        assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), pending.get(0)));

        List<Task> found = getExecutionDAO().getTasks(tasks.get(0).getTaskDefName(), null, 1);
        assertNotNull(found);
        assertEquals(1, found.size());
        assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), found.get(0)));
    }

    @Test
    public void testTaskOps() {
        List<Task> tasks = new LinkedList<>();
        String workflowId = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            Task task = new Task();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId(workflowId + "_t" + i);
            task.setReferenceTaskName("testTaskOps" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId(workflowId);
            task.setTaskDefName("testTaskOps" + i);
            task.setStatus(Task.Status.IN_PROGRESS);
            tasks.add(task);
        }

        for (int i = 0; i < 3; i++) {
            Task task = new Task();
            task.setScheduledTime(1L);
            task.setSeq(1);
            task.setTaskId("x" + workflowId + "_t" + i);
            task.setReferenceTaskName("testTaskOps" + i);
            task.setRetryCount(0);
            task.setWorkflowInstanceId("x" + workflowId);
            task.setTaskDefName("testTaskOps" + i);
            task.setStatus(Task.Status.IN_PROGRESS);
            getExecutionDAO().createTasks(Arrays.asList(task));
        }


        List<Task> created = getExecutionDAO().createTasks(tasks);
        assertEquals(tasks.size(), created.size());

        List<Task> pending = getExecutionDAO().getPendingTasksForTaskType(tasks.get(0).getTaskDefName());
        assertNotNull(pending);
        assertEquals(2, pending.size());
        //Pending list can come in any order.  finding the one we are looking for and then comparing
        Task matching = pending.stream().filter(task -> task.getTaskId().equals(tasks.get(0).getTaskId())).findAny().get();
        assertTrue(EqualsBuilder.reflectionEquals(matching, tasks.get(0)));

        List<Task> update = new LinkedList<>();
        for (int i = 0; i < 3; i++) {
            Task found = getExecutionDAO().getTask(workflowId + "_t" + i);
            assertNotNull(found);
            found.getOutputData().put("updated", true);
            found.setStatus(Task.Status.COMPLETED);
            update.add(found);
        }
        getExecutionDAO().updateTasks(update);

        List<String> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
        List<Task> found = getExecutionDAO().getTasks(taskIds);
        assertEquals(taskIds.size(), found.size());
        found.forEach(task -> {
            assertTrue(task.getOutputData().containsKey("updated"));
            assertEquals(true, task.getOutputData().get("updated"));
            getExecutionDAO().removeTask(task.getTaskId());
        });

        found = getExecutionDAO().getTasks(taskIds);
        assertTrue(found.isEmpty());
    }

    @Test
    public void testPending() {
        WorkflowDef def = new WorkflowDef();
        def.setName("pending_count_test");

        Workflow workflow = createTestWorkflow();
        workflow.setWorkflowDefinition(def);

        String idBase = workflow.getWorkflowId();
        generateWorkflows(workflow, idBase, 10);

        long count = getExecutionDAO().getPendingWorkflowCount(def.getName());
        assertEquals(10, count);

        for (int i = 0; i < 10; i++) {
            getExecutionDAO().removeFromPendingWorkflow(def.getName(), "x" + i + idBase);
        }
        count = getExecutionDAO().getPendingWorkflowCount(def.getName());
        assertEquals(0, count);
    }

    @Test
    public void complexExecutionTest() {
        Workflow workflow = createTestWorkflow();

        String workflowId = getExecutionDAO().createWorkflow(workflow);
        List<Task> created = getExecutionDAO().createTasks(workflow.getTasks());
        assertEquals(workflow.getTasks().size(), created.size());

        Workflow workflowWithTasks = getExecutionDAO().getWorkflow(workflow.getWorkflowId(), true);
        assertEquals(workflowWithTasks.getWorkflowId(), workflowId);
        assertTrue(!workflowWithTasks.getTasks().isEmpty());

        assertEquals(workflow.getWorkflowId(), workflowId);
        Workflow found = getExecutionDAO().getWorkflow(workflowId, false);
        assertTrue(found.getTasks().isEmpty());

        workflow.getTasks().clear();
        assertEquals(workflow, found);

        workflow.getInput().put("updated", true);
        getExecutionDAO().updateWorkflow(workflow);
        found = getExecutionDAO().getWorkflow(workflowId);
        assertNotNull(found);
        assertTrue(found.getInput().containsKey("updated"));
        assertEquals(true, found.getInput().get("updated"));

        List<String> running = getExecutionDAO().getRunningWorkflowIds(workflow.getWorkflowName());
        assertNotNull(running);
        assertTrue(running.isEmpty());

        workflow.setStatus(Workflow.WorkflowStatus.RUNNING);
        getExecutionDAO().updateWorkflow(workflow);

        running = getExecutionDAO().getRunningWorkflowIds(workflow.getWorkflowName());
        assertNotNull(running);
        assertEquals(1, running.size());
        assertEquals(workflow.getWorkflowId(), running.get(0));

        List<Workflow> pending = getExecutionDAO().getPendingWorkflowsByType(workflow.getWorkflowName());
        assertNotNull(pending);
        assertEquals(1, pending.size());
        assertEquals(3, pending.get(0).getTasks().size());
        pending.get(0).getTasks().clear();
        assertEquals(workflow, pending.get(0));

        workflow.setStatus(Workflow.WorkflowStatus.COMPLETED);
        getExecutionDAO().updateWorkflow(workflow);
        running = getExecutionDAO().getRunningWorkflowIds(workflow.getWorkflowName());
        assertNotNull(running);
        assertTrue(running.isEmpty());

        List<Workflow> bytime = getExecutionDAO().getWorkflowsByType(workflow.getWorkflowName(), System.currentTimeMillis(), System.currentTimeMillis() + 100);
        assertNotNull(bytime);
        assertTrue(bytime.isEmpty());

        bytime = getExecutionDAO().getWorkflowsByType(workflow.getWorkflowName(), workflow.getCreateTime() - 10, workflow.getCreateTime() + 10);
        assertNotNull(bytime);
        assertEquals(1, bytime.size());
    }

    protected Workflow createTestWorkflow() {
        WorkflowDef def = new WorkflowDef();
        def.setName("Junit Workflow");
        def.setVersion(3);
        def.setSchemaVersion(2);

        Workflow workflow = new Workflow();
        workflow.setWorkflowDefinition(def);
        workflow.setCorrelationId("correlationX");
        workflow.setCreatedBy("junit_tester");
        workflow.setEndTime(200L);

        Map<String, Object> input = new HashMap<>();
        input.put("param1", "param1 value");
        input.put("param2", 100);
        workflow.setInput(input);

        Map<String, Object> output = new HashMap<>();
        output.put("ouput1", "output 1 value");
        output.put("op2", 300);
        workflow.setOutput(output);

        workflow.setOwnerApp("workflow");
        workflow.setParentWorkflowId("parentWorkflowId");
        workflow.setParentWorkflowTaskId("parentWFTaskId");
        workflow.setReasonForIncompletion("missing recipe");
        workflow.setReRunFromWorkflowId("re-run from id1");
        workflow.setStartTime(90L);
        workflow.setStatus(Workflow.WorkflowStatus.FAILED);
        workflow.setWorkflowId("workflow0");

        List<Task> tasks = new LinkedList<>();

        Task task = new Task();
        task.setScheduledTime(1L);
        task.setSeq(1);
        task.setTaskId("t1");
        task.setReferenceTaskName("t1");
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setTaskDefName("task1");

        Task task2 = new Task();
        task2.setScheduledTime(2L);
        task2.setSeq(2);
        task2.setTaskId("t2");
        task2.setReferenceTaskName("t2");
        task2.setWorkflowInstanceId(workflow.getWorkflowId());
        task2.setTaskDefName("task2");

        Task task3 = new Task();
        task3.setScheduledTime(2L);
        task3.setSeq(3);
        task3.setTaskId("t3");
        task3.setReferenceTaskName("t3");
        task3.setWorkflowInstanceId(workflow.getWorkflowId());
        task3.setTaskDefName("task3");

        tasks.add(task);
        tasks.add(task2);
        tasks.add(task3);

        workflow.setTasks(tasks);

        workflow.setUpdatedBy("junit_tester");
        workflow.setUpdateTime(800L);

        return workflow;
    }

    protected void generateWorkflows(Workflow base, String baseId, int count) {
        for (int i = 0; i < count; i++) {
            base.setWorkflowId("x" + i + baseId);
            base.setCorrelationId("corr001");
            base.setStatus(Workflow.WorkflowStatus.RUNNING);
            getExecutionDAO().createWorkflow(base);
        }
    }
}
