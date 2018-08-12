package com.netflix.conductor.dao.mysql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.IndexDAO;

@SuppressWarnings("Duplicates")
public class MySQLExecutionDAOTest extends MySQLBaseDAOTest {

	private MySQLMetadataDAO metadata;
	private MySQLExecutionDAO dao;

	@Before
	public void setup() throws Exception {
		metadata = new MySQLMetadataDAO(objectMapper, dataSource, testConfiguration);
		dao = new MySQLExecutionDAO(mock(IndexDAO.class), metadata, objectMapper, dataSource);
		resetAllData();
	}

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void testTaskExceedsLimit() throws Exception {
		TaskDef def = new TaskDef();
		def.setName("task1");
		def.setConcurrentExecLimit(1);
		metadata.createTaskDef(def);

		List<Task> tasks = new LinkedList<>();
		for(int i = 0; i < 15; i++) {
			Task task = new Task();
			task.setScheduledTime(1L);
			task.setSeq(1);
			task.setTaskId("t_" + i);
			task.setWorkflowInstanceId("workflow_" + i);
			task.setReferenceTaskName("task1");
			task.setTaskDefName("task1");
			tasks.add(task);
			task.setStatus(Status.SCHEDULED);
		}

		dao.createTasks(tasks);
		assertFalse(dao.exceedsInProgressLimit(tasks.get(0)));
		tasks.get(0).setStatus(Status.IN_PROGRESS);
		dao.updateTask(tasks.get(0));

		for(Task task : tasks) {
			assertTrue(dao.exceedsInProgressLimit(task));
		}
	}

	@Test
	public void testCreateTaskException() throws Exception {
		Task task = new Task();
		task.setScheduledTime(1L);
		task.setSeq(1);
		task.setTaskId("t1");
		task.setTaskDefName("task1");

		expected.expect(ApplicationException.class);
		expected.expectMessage("Workflow instance id cannot be null");
		dao.createTasks(Collections.singletonList(task));

		task.setWorkflowInstanceId("wfid");
		expected.expect(ApplicationException.class);
		expected.expectMessage("Task reference name cannot be null");
		dao.createTasks(Collections.singletonList(task));
	}

	@Test
	public void testCreateTaskException2() throws Exception {
		Task task = new Task();
		task.setScheduledTime(1L);
		task.setSeq(1);
		task.setTaskId("t1");
		task.setTaskDefName("task1");
		task.setWorkflowInstanceId("wfid");

		expected.expect(ApplicationException.class);
		expected.expectMessage("Task reference name cannot be null");
		dao.createTasks(Collections.singletonList(task));
	}

	@Test
	public void testPollData() throws Exception {
		dao.updateLastPoll("taskDef", null, "workerId1");
		PollData pd = dao.getPollData("taskDef", null);
		assertNotNull(pd);
		assertTrue(pd.getLastPollTime() > 0);
		assertEquals(pd.getQueueName(), "taskDef");
		assertEquals(pd.getDomain(), null);
		assertEquals(pd.getWorkerId(), "workerId1");

		dao.updateLastPoll("taskDef", "domain1", "workerId1");
		pd = dao.getPollData("taskDef", "domain1");
		assertNotNull(pd);
		assertTrue(pd.getLastPollTime() > 0);
		assertEquals(pd.getQueueName(), "taskDef");
		assertEquals(pd.getDomain(), "domain1");
		assertEquals(pd.getWorkerId(), "workerId1");

		List<PollData> pData = dao.getPollData("taskDef");
		assertEquals(pData.size(), 2);

		pd = dao.getPollData("taskDef", "domain2");
		assertTrue(pd == null);
	}
	
	@Test
    public void testWith2THreads() throws InterruptedException, ExecutionException {
        testPollDataWithParallelThreads(2);
    }
    
    
    private void testPollDataWithParallelThreads(final int threadCount) throws InterruptedException, ExecutionException {

        Callable<PollData> task = new Callable<PollData>() {
            @Override
            public PollData call() {
                dao.updateLastPoll("taskDef", null, "workerId1");
                return dao.getPollData("taskDef", null);
            }
        };
        List<Callable<PollData>> tasks = Collections.nCopies(threadCount, task);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        List<Future<PollData>> futures = executorService.invokeAll(tasks);
        List<String> resultList = new ArrayList<String>(futures.size());
        // Check for exceptions
        for (Future<PollData> future : futures) {
            // Throws an exception if an exception was thrown by the task.
            PollData pollData = future.get();
            System.out.println(pollData);
            if(pollData !=null)
                resultList.add(future.get().getQueueName());
        }
        // Validate the IDs
        Assert.assertEquals(threadCount, futures.size());
        List<String> expectedList = new ArrayList<String>(threadCount);
        for (long i = 1; i <= threadCount; i++) {
            expectedList.add("taskDef");
        }
        Collections.sort(resultList);
        Assert.assertEquals(expectedList, resultList);
    }

	@Test
	public void testTaskCreateDups() throws Exception {
		List<Task> tasks = new LinkedList<>();
		String workflowId = UUID.randomUUID().toString();

		for(int i = 0; i < 3; i++) {
			Task task = new Task();
			task.setScheduledTime(1L);
			task.setSeq(1);
			task.setTaskId(workflowId + "_t" + i);
			task.setReferenceTaskName("t" + i);
			task.setRetryCount(0);
			task.setWorkflowInstanceId(workflowId);
			task.setTaskDefName("task" + i);
			task.setStatus(Status.IN_PROGRESS);
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
		task.setStatus(Status.IN_PROGRESS);
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
		task.setStatus(Status.IN_PROGRESS);
		tasks.add(task);

		List<Task> created = dao.createTasks(tasks);
		assertEquals(tasks.size()-1, created.size());	//1 less

		Set<String> srcIds = tasks.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());
		Set<String> createdIds = created.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());

		assertEquals(srcIds, createdIds);

		List<Task> pending = dao.getPendingTasksByWorkflow("task0", workflowId);
		assertNotNull(pending);
		assertEquals(1, pending.size());
		assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), pending.get(0)));

		List<Task> found = dao.getTasks(tasks.get(0).getTaskDefName(), null, 1);
		assertNotNull(found);
		assertEquals(1, found.size());
		assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), found.get(0)));
	}

	@Test
	public void testTaskOps() throws Exception {
		List<Task> tasks = new LinkedList<>();
		String workflowId = UUID.randomUUID().toString();

		for(int i = 0; i < 3; i++) {
			Task task = new Task();
			task.setScheduledTime(1L);
			task.setSeq(1);
			task.setTaskId(workflowId + "_t" + i);
			task.setReferenceTaskName("testTaskOps" + i);
			task.setRetryCount(0);
			task.setWorkflowInstanceId(workflowId);
			task.setTaskDefName("testTaskOps" + i);
			task.setStatus(Status.IN_PROGRESS);
			tasks.add(task);
		}

		for(int i = 0; i < 3; i++) {
			Task task = new Task();
			task.setScheduledTime(1L);
			task.setSeq(1);
			task.setTaskId("x" + workflowId + "_t" + i);
			task.setReferenceTaskName("testTaskOps" + i);
			task.setRetryCount(0);
			task.setWorkflowInstanceId("x" + workflowId);
			task.setTaskDefName("testTaskOps" + i);
			task.setStatus(Status.IN_PROGRESS);
			dao.createTasks(Arrays.asList(task));
		}


		List<Task> created = dao.createTasks(tasks);
		assertEquals(tasks.size(), created.size());

		List<Task> pending = dao.getPendingTasksForTaskType(tasks.get(0).getTaskDefName());
		assertNotNull(pending);
		assertEquals(2, pending.size());
		//Pending list can come in any order.  finding the one we are looking for and then comparing
		Task matching = pending.stream().filter(task -> task.getTaskId().equals(tasks.get(0).getTaskId())).findAny().get();
		assertTrue(EqualsBuilder.reflectionEquals(matching, tasks.get(0)));

		List<Task> update = new LinkedList<>();
		for(int i = 0; i < 3; i++) {
			Task found = dao.getTask(workflowId + "_t" + i);
			assertNotNull(found);
			found.getOutputData().put("updated", true);
			found.setStatus(Status.COMPLETED);
			update.add(found);
		}
		dao.updateTasks(update);

		List<String> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
		List<Task> found = dao.getTasks(taskIds);
		assertEquals(taskIds.size(), found.size());
		found.forEach(task -> {
			assertTrue(task.getOutputData().containsKey("updated"));
			assertEquals(true, task.getOutputData().get("updated"));
			dao.removeTask(task.getTaskId());
		});

		found = dao.getTasks(taskIds);
		assertTrue(found.isEmpty());
	}

	@Test
	public void test() throws Exception {
		Workflow workflow = new Workflow();
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
		workflow.setSchemaVersion(2);
		workflow.setStartTime(90L);
		workflow.setStatus(WorkflowStatus.FAILED);
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
		workflow.setVersion(3);
		//workflow.setWorkflowId("wf0001");
		workflow.setWorkflowType("Junit Workflow");

		String workflowId = dao.createWorkflow(workflow);
		List<Task> created = dao.createTasks(tasks);
		assertEquals(tasks.size(), created.size());

		Workflow workflowWithTasks = dao.getWorkflow(workflow.getWorkflowId(), true);
		assertEquals(workflowWithTasks.getWorkflowId(), workflowId);
		assertTrue(!workflowWithTasks.getTasks().isEmpty());

		assertEquals(workflow.getWorkflowId(), workflowId);
		Workflow found = dao.getWorkflow(workflowId, false);
		assertTrue(found.getTasks().isEmpty());

		workflow.getTasks().clear();
		assertTrue(EqualsBuilder.reflectionEquals(workflow, found));

		workflow.getInput().put("updated", true);
		dao.updateWorkflow(workflow);
		found = dao.getWorkflow(workflowId);
		assertNotNull(found);
		assertTrue(found.getInput().containsKey("updated"));
		assertEquals(true, found.getInput().get("updated"));

		List<String> running = dao.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertTrue(running.isEmpty());

		workflow.setStatus(WorkflowStatus.RUNNING);
		dao.updateWorkflow(workflow);

		running = dao.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertEquals(1, running.size());
		assertEquals(workflow.getWorkflowId(), running.get(0));

		List<Workflow> pending = dao.getPendingWorkflowsByType(workflow.getWorkflowType());
		assertNotNull(pending);
		assertEquals(1, pending.size());
		assertEquals(3, pending.get(0).getTasks().size());
		pending.get(0).getTasks().clear();
		assertTrue(EqualsBuilder.reflectionEquals(workflow, pending.get(0)));

		workflow.setStatus(WorkflowStatus.COMPLETED);
		dao.updateWorkflow(workflow);
		running = dao.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertTrue(running.isEmpty());

		List<Workflow> bytime = dao.getWorkflowsByType(workflow.getWorkflowType(), System.currentTimeMillis(), System.currentTimeMillis()+100);
		assertNotNull(bytime);
		assertTrue(bytime.isEmpty());

		bytime = dao.getWorkflowsByType(workflow.getWorkflowType(), workflow.getCreateTime() - 10, workflow.getCreateTime() + 10);
		assertNotNull(bytime);
		assertEquals(1, bytime.size());

		String workflowName = "pending_count_test";
		String idBase = workflow.getWorkflowId();
		for(int i = 0; i < 10; i++) {
			workflow.setWorkflowId("x" + i + idBase);
			workflow.setCorrelationId("corr001");
			workflow.setStatus(WorkflowStatus.RUNNING);
			workflow.setWorkflowType(workflowName);
			dao.createWorkflow(workflow);
		}

		List<Workflow> bycorrelationId = dao.getWorkflowsByCorrelationId("corr001", true);
		assertNotNull(bycorrelationId);
		assertEquals(10, bycorrelationId.size());

		long count = dao.getPendingWorkflowCount(workflowName);
		assertEquals(10, count);

		for(int i = 0; i < 10; i++) {
			dao.removeFromPendingWorkflow(workflowName, "x" + i + idBase);
		}
		count = dao.getPendingWorkflowCount(workflowName);
		assertEquals(0, count);
	}
}
