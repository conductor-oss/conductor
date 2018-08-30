/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.redis.JedisMock;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import redis.clients.jedis.JedisCommands;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * @author Viren
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class RedisExecutionDAOTest {

	private RedisMetadataDAO metadataDAO;

	private RedisExecutionDAO executionDAO;

	@Mock
	private IndexDAO indexDAO;

	private static ObjectMapper objectMapper = new ObjectMapper();

	static {
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
		objectMapper.setSerializationInclusion(Include.NON_NULL);
		objectMapper.setSerializationInclusion(Include.NON_EMPTY);
	}

	@SuppressWarnings("unchecked")
	@Before
	public void init() throws Exception {
		Configuration config = new TestConfiguration();
		JedisCommands jedisMock = new JedisMock();
		DynoProxy dynoClient = new DynoProxy(jedisMock);

		metadataDAO = new RedisMetadataDAO(dynoClient, objectMapper, config);
		executionDAO = new RedisExecutionDAO(dynoClient, objectMapper, indexDAO, metadataDAO, config);

		// Ignore indexing in Redis tests.
		doNothing().when(indexDAO).indexTask(any(Task.class));
	}

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void testTaskExceedsLimit() throws Exception {

		TaskDef def = new TaskDef();
		def.setName("task1");
		def.setConcurrentExecLimit(1);
		metadataDAO.createTaskDef(def);

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

		executionDAO.createTasks(tasks);
		assertFalse(executionDAO.exceedsInProgressLimit(tasks.get(0)));
		tasks.get(0).setStatus(Status.IN_PROGRESS);
		executionDAO.updateTask(tasks.get(0));

		for(Task task : tasks) {
			assertTrue(executionDAO.exceedsInProgressLimit(task));
		}

	}
	@Test
	public void testCreateTaskException() throws Exception {
		Task task = new Task();
		task.setScheduledTime(1L);
		task.setSeq(1);
		task.setTaskId("t1");
		task.setTaskDefName("task1");
		expected.expect(NullPointerException.class);
		expected.expectMessage("Workflow instance id cannot be null");
		executionDAO.createTasks(Arrays.asList(task));

		task.setWorkflowInstanceId("wfid");
		expected.expect(NullPointerException.class);
		expected.expectMessage("Task reference name cannot be nullss");
		executionDAO.createTasks(Arrays.asList(task));

	}

	@Test
	public void testCreateTaskException2() throws Exception {
		Task task = new Task();
		task.setScheduledTime(1L);
		task.setSeq(1);
		task.setTaskId("t1");
		task.setTaskDefName("task1");
		task.setWorkflowInstanceId("wfid");
		expected.expect(NullPointerException.class);
		expected.expectMessage("Task reference name cannot be null");
		executionDAO.createTasks(Arrays.asList(task));
	}

	@Test
	public void testPollData() throws Exception {
		executionDAO.updateLastPoll("taskDef", null, "workerId1");
		PollData pd = executionDAO.getPollData("taskDef", null);
		assertNotNull(pd);
		assertTrue(pd.getLastPollTime() > 0);
		assertEquals(pd.getQueueName(), "taskDef");
		assertEquals(pd.getDomain(), null);
		assertEquals(pd.getWorkerId(), "workerId1");

		executionDAO.updateLastPoll("taskDef", "domain1", "workerId1");
		pd = executionDAO.getPollData("taskDef", "domain1");
		assertNotNull(pd);
		assertTrue(pd.getLastPollTime() > 0);
		assertEquals(pd.getQueueName(), "taskDef");
		assertEquals(pd.getDomain(), "domain1");
		assertEquals(pd.getWorkerId(), "workerId1");

		List<PollData> pData = executionDAO.getPollData("taskDef");
		assertEquals(pData.size(), 2);

		pd = executionDAO.getPollData("taskDef", "domain2");
		assertTrue(pd == null);
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

		List<Task> created = executionDAO.createTasks(tasks);
		assertEquals(tasks.size()-1, created.size());		//1 less

		Set<String> srcIds = tasks.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());
		Set<String> createdIds = created.stream().map(t -> t.getReferenceTaskName() + "." + t.getRetryCount()).collect(Collectors.toSet());

		assertEquals(srcIds, createdIds);

		List<Task> pending = executionDAO.getPendingTasksByWorkflow("task0", workflowId);
		assertNotNull(pending);
		assertEquals(1, pending.size());
		assertTrue(EqualsBuilder.reflectionEquals(tasks.get(0), pending.get(0)));

		List<Task> found = executionDAO.getTasks(tasks.get(0).getTaskDefName(), null, 1);
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
			task.setStatus(Task.Status.IN_PROGRESS);
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
			task.setStatus(Task.Status.IN_PROGRESS);
			executionDAO.createTasks(Arrays.asList(task));
		}


		List<Task> created = executionDAO.createTasks(tasks);
		assertEquals(tasks.size(), created.size());

		List<Task> pending = executionDAO.getPendingTasksForTaskType(tasks.get(0).getTaskDefName());
		assertNotNull(pending);
		assertEquals(2, pending.size());
		//Pending list can come in any order.  finding the one we are looking for and then comparing
		Task matching = pending.stream().filter(task -> task.getTaskId().equals(tasks.get(0).getTaskId())).findAny().get();
		assertTrue(EqualsBuilder.reflectionEquals(matching, tasks.get(0)));

		List<Task> update = new LinkedList<>();
		for(int i = 0; i < 3; i++) {
			Task found = executionDAO.getTask(workflowId + "_t" + i);
			assertNotNull(found);
			found.getOutputData().put("updated", true);
			found.setStatus(Task.Status.COMPLETED);
			update.add(found);
		}
		executionDAO.updateTasks(update);

		List<String> taskIds = tasks.stream().map(Task::getTaskId).collect(Collectors.toList());
		List<Task> found = executionDAO.getTasks(taskIds);
		assertEquals(taskIds.size(), found.size());
		found.forEach(task -> {
			assertTrue(task.getOutputData().containsKey("updated"));
			assertEquals(true, task.getOutputData().get("updated"));
			executionDAO.removeTask(task.getTaskId());
		});

		found = executionDAO.getTasks(taskIds);
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

		String workflowId = executionDAO.createWorkflow(workflow);
		List<Task> created = executionDAO.createTasks(tasks);
		assertEquals(tasks.size(), created.size());

		Workflow workflowWithTasks = executionDAO.getWorkflow(workflow.getWorkflowId(), true);
		assertEquals(workflowWithTasks.getWorkflowId(), workflowId);
		assertTrue(!workflowWithTasks.getTasks().isEmpty());

		assertEquals(workflow.getWorkflowId(), workflowId);
		Workflow found = executionDAO.getWorkflow(workflowId, false);
		assertTrue(found.getTasks().isEmpty());

		workflow.getTasks().clear();
		assertTrue(EqualsBuilder.reflectionEquals(workflow, found));

		workflow.getInput().put("updated", true);
		executionDAO.updateWorkflow(workflow);
		found = executionDAO.getWorkflow(workflowId);
		assertNotNull(found);
		assertTrue(found.getInput().containsKey("updated"));
		assertEquals(true, found.getInput().get("updated"));

		List<String> running = executionDAO.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertTrue(running.isEmpty());

		workflow.setStatus(WorkflowStatus.RUNNING);
		executionDAO.updateWorkflow(workflow);

		running = executionDAO.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertEquals(1, running.size());
		assertEquals(workflow.getWorkflowId(), running.get(0));

		List<Workflow> pending = executionDAO.getPendingWorkflowsByType(workflow.getWorkflowType());
		assertNotNull(pending);
		assertEquals(1, pending.size());
		assertEquals(3, pending.get(0).getTasks().size());
		pending.get(0).getTasks().clear();
		assertTrue(EqualsBuilder.reflectionEquals(workflow, pending.get(0)));

		workflow.setStatus(WorkflowStatus.COMPLETED);
		executionDAO.updateWorkflow(workflow);
		running = executionDAO.getRunningWorkflowIds(workflow.getWorkflowType());
		assertNotNull(running);
		assertTrue(running.isEmpty());

		List<Workflow> bytime = executionDAO.getWorkflowsByType(workflow.getWorkflowType(), System.currentTimeMillis(), System.currentTimeMillis()+100);
		assertNotNull(bytime);
		assertTrue(bytime.isEmpty());

		bytime = executionDAO.getWorkflowsByType(workflow.getWorkflowType(), workflow.getCreateTime() - 10, workflow.getCreateTime() + 10);
		assertNotNull(bytime);
		assertEquals(1, bytime.size());

		String workflowName = "pending_count_test";
		String idBase = workflow.getWorkflowId();
		for(int i = 0; i < 10; i++) {
			workflow.setWorkflowId("x" + i + idBase);
			workflow.setCorrelationId("corr001");
			workflow.setStatus(WorkflowStatus.RUNNING);
			workflow.setWorkflowType(workflowName);
			executionDAO.createWorkflow(workflow);
		}

		/*
		List<Workflow> bycorrelationId = executionDAO.getWorkflowsByCorrelationId("corr001");
		assertNotNull(bycorrelationId);
		assertEquals(10, bycorrelationId.size());
		 */
		long count = executionDAO.getPendingWorkflowCount(workflowName);
		assertEquals(10, count);

		for(int i = 0; i < 10; i++) {
			executionDAO.removeFromPendingWorkflow(workflowName, "x" + i + idBase);
		}
		count = executionDAO.getPendingWorkflowCount(workflowName);
		assertEquals(0, count);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCorrelateTaskToWorkflowInDS() throws Exception {
		String workflowId = "workflowId";
		String taskId = "taskId1";
		String taskDefName = "task1";

		TaskDef def = new TaskDef();
		def.setName("task1");
		def.setConcurrentExecLimit(1);
		metadataDAO.createTaskDef(def);

		Task task = new Task();
		task.setTaskId(taskId);
		task.setWorkflowInstanceId(workflowId);
		task.setReferenceTaskName("ref_name");
		task.setTaskDefName(taskDefName);
		task.setTaskType(taskDefName);
		task.setStatus(Status.IN_PROGRESS);
		List<Task> tasks = executionDAO.createTasks(Collections.singletonList(task));
		assertNotNull(tasks);
		assertEquals(1, tasks.size());

		executionDAO.correlateTaskToWorkflowInDS(taskId, workflowId);
		tasks = executionDAO.getTasksForWorkflow(workflowId);
		assertNotNull(tasks);
		assertEquals(workflowId, tasks.get(0).getWorkflowInstanceId());
		assertEquals(taskId, tasks.get(0).getTaskId());
	}

	@Test
	public void testExceedsRateLimitWhenNoRateLimitSet() {
		Task task =new Task();
		assertFalse(executionDAO.exceedsRateLimitPerFrequency(task));
	}

	@Test
	public void testExceedsRateLimitWithinLimit() {
		Task task =new Task();
		task.setRateLimitFrequencyInSeconds(60);
		task.setRateLimitPerFrequency(20);

		assertFalse(executionDAO.exceedsRateLimitPerFrequency(task));

	}

	@Test
	public void testExceedsRateLimitOutOfLimit() {
		Task task =new Task();
		task.setRateLimitFrequencyInSeconds(60);
		task.setRateLimitPerFrequency(1);

		assertFalse(executionDAO.exceedsRateLimitPerFrequency(task));
		assertTrue(executionDAO.exceedsRateLimitPerFrequency(task));

	}

}
