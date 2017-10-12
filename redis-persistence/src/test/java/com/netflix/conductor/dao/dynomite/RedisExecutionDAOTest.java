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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.ListenableActionFuture;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
import com.netflix.conductor.dao.index.ElasticSearchDAO;
import com.netflix.conductor.dao.redis.JedisMock;

import redis.clients.jedis.JedisCommands;

/**
 * @author Viren
 *
 */
public class RedisExecutionDAOTest {

	private RedisMetadataDAO mdao;

	private RedisExecutionDAO dao;

	private static ObjectMapper om = new ObjectMapper();

	static {
		om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
		om.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
		om.setSerializationInclusion(Include.NON_NULL);
		om.setSerializationInclusion(Include.NON_EMPTY);
	}

	@SuppressWarnings("unchecked")
	@Before
	public void init() throws Exception {
		Configuration config = new TestConfiguration();
		JedisCommands jedisMock = new JedisMock();
		DynoProxy dynoClient = new DynoProxy(jedisMock);

		Client client = mock(Client.class);
		BulkItemResponse[] responses = new BulkItemResponse[0];
		BulkResponse response = new BulkResponse(responses, 1);
		BulkRequestBuilder brb = mock(BulkRequestBuilder.class);

		when(brb.add(any(IndexRequest.class))).thenReturn(brb);
		ListenableActionFuture<BulkResponse> laf = mock(ListenableActionFuture.class);
		when(laf.actionGet()).thenReturn(response);
		when(brb.execute()).thenReturn(laf);
		when(client.prepareBulk()).thenReturn(brb);
		final UpdateResponse ur = new UpdateResponse();
		when(client.update(any())).thenReturn(new ActionFuture<UpdateResponse>() {

			@Override
			public boolean isDone() {
				return true;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}

			@Override
			public UpdateResponse get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return ur;
			}

			@Override
			public UpdateResponse get() throws InterruptedException, ExecutionException {
				return ur;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}

			@Override
			public UpdateResponse actionGet(long timeout, TimeUnit unit) {
				return ur;
			}

			@Override
			public UpdateResponse actionGet(TimeValue timeout) {
				return ur;
			}

			@Override
			public UpdateResponse actionGet(long timeoutMillis) {
				return ur;
			}

			@Override
			public UpdateResponse actionGet(String timeout) {
				return ur;
			}

			@Override
			public UpdateResponse actionGet() {
				return ur;
			}
		});
		BulkRequestBuilder bulk = client.prepareBulk();
		bulk = bulk.add(any(IndexRequest.class));
		bulk.execute().actionGet();

		when(client.prepareBulk().add(any(IndexRequest.class)).execute().actionGet()).thenReturn(response);

		ElasticSearchDAO indexer = spy(new ElasticSearchDAO(client, config, om));
		mdao = new RedisMetadataDAO(dynoClient, om, config);
		dao = new RedisExecutionDAO(dynoClient, om, indexer, mdao, config);

	}

	@Rule
	public ExpectedException expected = ExpectedException.none();

	@Test
	public void testTaskExceedsLimit() throws Exception {

		TaskDef def = new TaskDef();
		def.setName("task1");
		def.setConcurrentExecLimit(1);
		mdao.createTaskDef(def);

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
		expected.expect(NullPointerException.class);
		expected.expectMessage("Workflow instance id cannot be null");
		dao.createTasks(Arrays.asList(task));

		task.setWorkflowInstanceId("wfid");
		expected.expect(NullPointerException.class);
		expected.expectMessage("Task reference name cannot be nullss");
		dao.createTasks(Arrays.asList(task));

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
		dao.createTasks(Arrays.asList(task));
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

		List<Task> created = dao.createTasks(tasks);
		assertEquals(tasks.size()-1, created.size());		//1 less

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
			found.setStatus(Task.Status.COMPLETED);
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

		/*
		List<Workflow> bycorrelationId = dao.getWorkflowsByCorrelationId("corr001");
		assertNotNull(bycorrelationId);
		assertEquals(10, bycorrelationId.size());
		 */
		long count = dao.getPendingWorkflowCount(workflowName);
		assertEquals(10, count);

		for(int i = 0; i < 10; i++) {
			dao.removeFromPendingWorkflow(workflowName, "x" + i + idBase);
		}
		count = dao.getPendingWorkflowCount(workflowName);
		assertEquals(0, count);

	}

}
