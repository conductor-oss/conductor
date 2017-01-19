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
package com.netflix.conductor.tests.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.RetryLogic;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.WorkflowContext;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.WorkflowSweeper;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.tests.utils.TestRunner;

/**
 * @author Viren
 *
 */
@RunWith(TestRunner.class)
public class WorkflowServiceTest {
	
	private static final String COND_TASK_WF = "ConditionalTaskWF";
	
	private static final String FORK_JOIN_NESTED_WF = "FanInOutNestedTest";
	
	private static final String FORK_JOIN_WF = "FanInOutTest";
	
	private static final String DYNAMIC_FORK_JOIN_WF = "DynamicFanInOutTest";
	
	private static final String DYNAMIC_FORK_JOIN_WF_LEGACY = "DynamicFanInOutTestLegacy";

	private static final int RETRY_COUNT = 1;

	@Inject
	private ExecutionService ess;
	
	@Inject
	private MetadataService ms;
	
	@Inject
	private DeciderService ds;
	
	@Inject
	private WorkflowSweeper sweeper;
	
	@Inject 
	private QueueDAO queue;
	
	@Inject
	private WorkflowExecutor provider;

	private static boolean registered;
	
	private static List<TaskDef> taskDefs;

	private static final String LINEAR_WORKFLOW_T1_T2 = "junit_test_wf";
	
	private static final String LONG_RUNNING = "longRunningWf";
	
	private static final String TEST_WORKFLOW_NAME_3 = "junit_test_wf3";
	
	@Before
	public void init() throws Exception {
		System.setProperty("EC2_REGION", "us-east-1");
		System.setProperty("EC2_AVAILABILITY_ZONE", "us-east-1c");		
		if(registered){
			return;
		}
		
		
		WorkflowContext.set(new WorkflowContext("junit_app"));
		for(int i = 0; i < 21; i++){
			
			String name = "junit_task_" + i;
			if(ms.getTaskDef(name) != null){
				continue;
			}
			
			TaskDef task = new TaskDef();
			task.setName(name);
			task.setTimeoutSeconds(120);
			task.setRetryCount(RETRY_COUNT);
			ms.registerTaskDef(Arrays.asList(task));
		}

		TaskDef task = new TaskDef();
		task.setName("short_time_out");
		task.setTimeoutSeconds(5);
		task.setRetryCount(RETRY_COUNT);
		ms.registerTaskDef(Arrays.asList(task));
		
		WorkflowDef def = new WorkflowDef();
		def.setName(LINEAR_WORKFLOW_T1_T2);
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));
		Map<String, Object> outputParameters = new HashMap<>();
		outputParameters.put("o1", "${workflow.input.param1}");
		outputParameters.put("o2", "${t2.output.uuid}");
		outputParameters.put("o3", "${t1.output.op}");
		def.setOutputParameters(outputParameters);
		def.setFailureWorkflow("$workflow.input.failureWfName");
		def.setSchemaVersion(2);
		LinkedList<WorkflowTask> wftasks = new LinkedList<>();
		
		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_1");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "${workflow.input.param1}");
		ip1.put("p2", "${workflow.input.param2}");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("t1");
		
		WorkflowTask wft2 = new WorkflowTask();
		wft2.setName("junit_task_2");
		Map<String, Object> ip2 = new HashMap<>();
		ip2.put("tp1", "${workflow.input.param1}");
		ip2.put("tp2", "${t1.output.op}");
		wft2.setInputParameters(ip2);
		wft2.setTaskReferenceName("t2");
		
		wftasks.add(wft1);
		wftasks.add(wft2);
		def.setTasks(wftasks);
		
		WorkflowTask wft3 = new WorkflowTask();
		wft3.setName("junit_task_3");
		Map<String, Object> ip3 = new HashMap<>();
		ip3.put("tp1", "${workflow.input.param1}");
		ip3.put("tp2", "${t1.output.op}");
		wft3.setInputParameters(ip3);
		wft3.setTaskReferenceName("t3");
		
		WorkflowDef def2 = new WorkflowDef();
		def2.setName(TEST_WORKFLOW_NAME_3);
		def2.setDescription(def2.getName());
		def2.setVersion(1);
		def2.setInputParameters(Arrays.asList("param1", "param2"));
		LinkedList<WorkflowTask> wftasks2 = new LinkedList<>();

		wftasks2.add(wft1);
		wftasks2.add(wft2);
		wftasks2.add(wft3);
		def2.setSchemaVersion(2);
		def2.setTasks(wftasks2);
		
		try {
			
			WorkflowDef[] wdsf = new WorkflowDef[] { def, def2 };
			for (WorkflowDef wd : wdsf) {
				ms.updateWorkflowDef(wd);
			}
			createForkJoinWorkflow();
			def.setName(LONG_RUNNING);
			ms.updateWorkflowDef(def);
		} catch (Exception e) {}
		
		taskDefs = ms.getTaskDefs();
		
		registered = true;
	}
	
	@Test
	public void testWorkflowWithNoTasks() throws Exception {

		WorkflowDef empty = new WorkflowDef();
		empty.setName("empty_workflow");
		empty.setSchemaVersion(2);
		ms.registerWorkflowDef(empty);
		
		String id = provider.startWorkflow(empty.getName(), 1, "testWorkflowWithNoTasks", new HashMap<>());
		assertNotNull(id);
		Workflow workflow = ess.getExecutionStatus(id, true);
		assertNotNull(workflow);
		assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
		assertEquals(0, workflow.getTasks().size());
	}
	
	@Test
	public void testTaskDefTemplate() throws Exception {
		
		System.setProperty("STACK2", "test_stack");
		TaskDef templatedTask = new TaskDef();
		templatedTask.setName("templated_task");
		Map<String, Object> httpRequest = new HashMap<>();
		httpRequest.put("method", "GET");
		httpRequest.put("vipStack", "${STACK2}");
		httpRequest.put("uri", "/get/something");
		Map<String, Object> body = new HashMap<>();
		body.put("inputPaths", Arrays.asList("${workflow.input.path1}", "${workflow.input.path2}"));
		body.put("requestDetails", "${workflow.input.requestDetails}");
		body.put("outputPath", "${workflow.input.outputPath}");
		httpRequest.put("body", body);
		templatedTask.getInputTemplate().put("http_request", httpRequest);
		ms.registerTaskDef(Arrays.asList(templatedTask));
		
		WorkflowDef templateWf = new WorkflowDef();
		templateWf.setName("template_workflow");
		WorkflowTask wft = new WorkflowTask();
		wft.setName(templatedTask.getName());
		wft.setType(Type.SIMPLE);
		wft.setTaskReferenceName("t0");
		templateWf.getTasks().add(wft);
		templateWf.setSchemaVersion(2);
		ms.registerWorkflowDef(templateWf);
		
		Map<String, Object> requestDetails = new HashMap<>();
		requestDetails.put("key1", "value1");
		requestDetails.put("key2", 42);
		
		Map<String, Object> input = new HashMap<>();
		input.put("path1", "file://path1");
		input.put("path2", "file://path2");
		input.put("outputPath", "s3://bucket/outputPath");
		input.put("requestDetails", requestDetails);
		
		String id = provider.startWorkflow(templateWf.getName(), 1, "testTaskDefTemplate", input);
		assertNotNull(id);
		Workflow workflow = ess.getExecutionStatus(id, true);
		assertNotNull(workflow);
		assertTrue(workflow.getReasonForIncompletion(), !workflow.getStatus().isTerminal());
		assertEquals(1, workflow.getTasks().size());
		Task task = workflow.getTasks().get(0);
		Map<String, Object> taskInput = task.getInputData();
		assertNotNull(taskInput);
		assertTrue(taskInput.containsKey("http_request"));
		assertTrue(taskInput.get("http_request") instanceof Map);
		
		ObjectMapper om = new ObjectMapper();
	
		//Use the commented sysout to get the string value
		//System.out.println(om.writeValueAsString(om.writeValueAsString(taskInput)));
		String expected = "{\"http_request\":{\"method\":\"GET\",\"vipStack\":\"test_stack\",\"body\":{\"requestDetails\":{\"key1\":\"value1\",\"key2\":42},\"outputPath\":\"s3://bucket/outputPath\",\"inputPaths\":[\"file://path1\",\"file://path2\"]},\"uri\":\"/get/something\"}}";
		assertEquals(expected, om.writeValueAsString(taskInput));
	}
	
	
	@Test
	public void testWorkflowSchemaVersion() throws Exception {
		WorkflowDef ver2 = new WorkflowDef();
		ver2.setSchemaVersion(2);
		ver2.setName("Test_schema_version2");
		ver2.setVersion(1);
		
		WorkflowDef ver1 = new WorkflowDef();
		ver1.setName("Test_schema_version1");
		ver1.setVersion(1);
		
		ms.updateWorkflowDef(ver1);
		ms.updateWorkflowDef(ver2);
		
		WorkflowDef found = ms.getWorkflowDef(ver2.getName(), 1);
		assertNotNull(found);
		assertEquals(2, found.getSchemaVersion());
		
		WorkflowDef found1 = ms.getWorkflowDef(ver1.getName(), 1);
		assertNotNull(found1);
		assertEquals(1, found1.getSchemaVersion());
		
	}
	
	@Test
	public void testForkJoin() throws Exception {
		try{
			createForkJoinWorkflow();
		}catch(Exception e){}
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(0);
		taskDef.setTimeoutSeconds(0);
		ms.updateTaskDef(taskDef);
		
		Map<String, Object> input = new HashMap<String, Object>();
		String wfid = provider.startWorkflow(FORK_JOIN_WF, 1, "fanouttest", input );
		System.out.println("testForkJoin.wfid=" + wfid);
		
		Task t1 = ess.poll("junit_task_1", "test");
		assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));
		
		Task t2 = ess.poll("junit_task_2", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));
		
		Task t3 = ess.poll("junit_task_3", "test");
		assertNull(t3);
		
		assertNotNull(t1);
		assertNotNull(t2);
		
		t1.setStatus(Status.COMPLETED);
		ess.updateTask(t1);

		Workflow wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.RUNNING, wf.getStatus());
		
		t3 = ess.poll("junit_task_3", "test");
		assertNotNull(t3);
		
		t2.setStatus(Status.COMPLETED);
		t3.setStatus(Status.COMPLETED);

		ExecutorService es = Executors.newFixedThreadPool(2);
		Future<?> future1 = es.submit(()->{
			try {
				ess.updateTask(t2);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
				
		});
		final Task _t3 = t3;
		Future<?> future2 = es.submit(()->{
			try {
				ess.updateTask(_t3);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
				
		});
		future1.get();
		future2.get();
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.RUNNING, wf.getStatus());
		if(!wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t3"))){
			ds.decide(wfid, provider);
			wf = ess.getExecutionStatus(wfid, true);
			assertNotNull(wf);
		}else {
			ds.decide(wfid, provider);
		}
		assertTrue("Found " + wf.getTasks().stream().map(t -> t.getTaskType()).collect(Collectors.toList()), wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t3")));
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.RUNNING, wf.getStatus());
		assertTrue("Found  " + wf.getTasks().stream().map(t -> t.getReferenceTaskName() + "." + t.getStatus()).collect(Collectors.toList()), wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t4")));		
		assertEquals("Found " + wf.getTasks().stream().map(t -> t.getTaskType()).collect(Collectors.toList()), 6, wf.getTasks().size());
		
		ds.decide(wfid, provider);
		ds.decide(wfid, provider);
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.RUNNING, wf.getStatus());
		//fanout, t1, t2, t3, t4, join
		assertEquals("Found " + wf.getTasks().stream().map(t -> t.getTaskType()).collect(Collectors.toList()), 6, wf.getTasks().size());
		
		Task t4 = ess.poll("junit_task_4", "test");
		assertNotNull(t4);
		t4.setStatus(Status.COMPLETED);
		ess.updateTask(t4);
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.COMPLETED, wf.getStatus());
	}
	
	@Test
	public void testForkJoinNested() throws Exception {
		
		createForkJoinNestedWorkflow();

		Map<String, Object> input = new HashMap<String, Object>();
		input.put("case", "a");		//This should execute t16 and t19
		String wfid = provider.startWorkflow(FORK_JOIN_NESTED_WF, 1, "fork_join_nested_test", input );
		System.out.println("testForkJoinNested.wfid=" + wfid);
		
		Workflow wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t11")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t12")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t13")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("sw1")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork1")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("fork2")));
		
		assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
		assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t1")));
		assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t2")));

		
		Task t1 = ess.poll("junit_task_11", "test");
		assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));

		Task t2 = ess.poll("junit_task_12", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));
		
		Task t3 = ess.poll("junit_task_13", "test");
		assertTrue(ess.ackTaskRecieved(t3.getTaskId(), "test"));
		
		assertNotNull(t1);
		assertNotNull(t2);
		assertNotNull(t3);
		
		t1.setStatus(Status.COMPLETED);
		t2.setStatus(Status.COMPLETED);
		t3.setStatus(Status.COMPLETED);
		
		ess.updateTask(t1);
		ess.updateTask(t2);
		ess.updateTask(t3);

		wf = ess.getExecutionStatus(wfid, true);
		
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t16")));
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t14")));
		
		String[] tasks = new String[]{"junit_task_1","junit_task_2","junit_task_14","junit_task_16"};
		for(String tt : tasks){
			Task polled = ess.poll(tt, "test");
			assertNotNull(polled);
			polled.setStatus(Status.COMPLETED);
			ess.updateTask(polled);
		}
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		
		assertTrue(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t19")));
		assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));		//Not there yet
		assertFalse(wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t20")));		//Not there yet
		
		Task task19 = ess.poll("junit_task_19", "test");
		assertNotNull(task19);
		task19.setStatus(Status.COMPLETED);
		ess.updateTask(task19);
		
		Task task20 = ess.poll("junit_task_20", "test");
		assertNotNull(task20);
		task20.setStatus(Status.COMPLETED);
		ess.updateTask(task20);
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		
		Set<String> pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
		assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("join1")));
		
		pendingTasks = wf.getTasks().stream().filter(t -> !t.getStatus().isTerminal()).map(t -> t.getReferenceTaskName()).collect(Collectors.toSet());
		assertTrue("Found only this: " + pendingTasks, wf.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equals("t15")));
		Task task15 = ess.poll("junit_task_15", "test");
		assertNotNull(task15);
		task15.setStatus(Status.COMPLETED);
		ess.updateTask(task15);
		
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
	}
	
	@Test
	public void testForkJoinFailure() throws Exception {
		
		try{
			createForkJoinWorkflow();
		}catch(Exception e){}
		
		String taskName = "junit_task_2";
		TaskDef taskDef = ms.getTaskDef(taskName);
		int retryCount = taskDef.getRetryCount();
		taskDef.setRetryCount(0);
		ms.updateTaskDef(taskDef);
		
		
		Map<String, Object> input = new HashMap<String, Object>();
		String wfid = provider.startWorkflow(FORK_JOIN_WF, 1, "fanouttest", input );
		System.out.println("testForkJoinFailure.wfid=" + wfid);
		
		Task t1 = ess.poll("junit_task_2", "test");
		assertNotNull(t1);
		assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));

		Task t2 = ess.poll("junit_task_1", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));
		
		Task t3 = ess.poll("junit_task_3", "test");
		assertNull(t3);
		
		assertNotNull(t1);
		assertNotNull(t2);
		t1.setStatus(Status.FAILED);
		t2.setStatus(Status.COMPLETED);
		
		ess.updateTask(t2);
		Workflow wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.RUNNING, wf.getStatus());
		
		t3 = ess.poll("junit_task_3", "test");
		assertNotNull(t3);
		
		
		ess.updateTask(t1);
		wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals("Found " + wf.getTasks(), WorkflowStatus.FAILED, wf.getStatus());
		
		
		taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(retryCount);
		ms.updateTaskDef(taskDef);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testDynamicForkJoinLegacy() throws Exception { 

		try{
			createDynamicForkJoinWorkflowDefsLegacy();
		}catch(Exception e){}
		
		Map<String, Object> input = new HashMap<String, Object>();
		String wfid = provider.startWorkflow(DYNAMIC_FORK_JOIN_WF_LEGACY, 1, "dynfanouttest1", input );
		System.out.println("testDynamicForkJoinLegacy.wfid=" + wfid);
		
		Task t1 = ess.poll("junit_task_1", "test");
		//assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));

		DynamicForkJoinTaskList dtasks = new DynamicForkJoinTaskList();
		
		input = new HashMap<String, Object>();
		input.put("k1", "v1");
		dtasks.add("junit_task_2", null, "xdt1", input);

		HashMap<String, Object> input2 = new HashMap<String, Object>();
		input2.put("k2", "v2");
		dtasks.add("junit_task_3", null, "xdt2", input2);
		
		t1.getOutputData().put("dynamicTasks", dtasks);
		t1.setStatus(Status.COMPLETED);
		
		ess.updateTask(t1);
		
		Task t2 = ess.poll("junit_task_2", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));		
		assertEquals("xdt1", t2.getReferenceTaskName());
		assertTrue(t2.getInputData().containsKey("k1"));
		assertEquals("v1", t2.getInputData().get("k1"));
		Map<String, Object> output = new HashMap<String, Object>();
		output.put("ok1", "ov1");
		t2.setOutputData(output);
		t2.setStatus(Status.COMPLETED);
		ess.updateTask(t2);

		Task t3 = ess.poll("junit_task_3", "test");
		assertTrue(ess.ackTaskRecieved(t3.getTaskId(), "test"));	
		assertEquals("xdt2", t3.getReferenceTaskName());
		assertTrue(t3.getInputData().containsKey("k2"));
		assertEquals("v2", t3.getInputData().get("k2"));
		
		output = new HashMap<String, Object>();
		output.put("ok1", "ov1");
		t3.setOutputData(output);
		t3.setStatus(Status.COMPLETED);
		ess.updateTask(t3);

		Workflow wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
		
		// Check the output
		Task joinTask = wf.getTaskByRefName("dynamicfanouttask_join");
		assertEquals("Found:" + joinTask.getOutputData(), 2, joinTask.getOutputData().keySet().size());
		Set<String> joinTaskOutput = joinTask.getOutputData().keySet();
		System.out.println("joinTaskOutput=" + joinTaskOutput);
		for(String key: joinTask.getOutputData().keySet()){
			assertTrue(key.equals("xdt1") || key.equals("xdt2"));
			assertEquals("ov1", ((Map<String, Object>)joinTask.getOutputData().get(key)).get("ok1"));
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDynamicForkJoin() throws Exception {
		
		createDynamicForkJoinWorkflowDefs();
		
		String taskName = "junit_task_2";
		TaskDef taskDef = ms.getTaskDef(taskName);
		int retryCount = taskDef.getRetryCount();
		taskDef.setRetryCount(2);
		taskDef.setRetryDelaySeconds(0);
		taskDef.setRetryLogic(RetryLogic.FIXED);
		ms.updateTaskDef(taskDef);
		
		Map<String, Object> input = new HashMap<String, Object>();
		String wfid = provider.startWorkflow(DYNAMIC_FORK_JOIN_WF, 1, "dynfanouttest1", input );
		System.out.println("testDynamicForkJoin.wfid=" + wfid);
		Workflow es = provider.getWorkflow(wfid, true);
		assertNotNull(es);
		assertEquals(es.getReasonForIncompletion(), WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(1, es.getTasks().size());
		Task t1 = ess.poll("junit_task_1", "test");
		assertNotNull(t1);
		assertTrue(ess.ackTaskRecieved(t1.getTaskId(), "test"));
		assertEquals("dt1", t1.getReferenceTaskName());
		
		
		Map<String, Object> input1 = new HashMap<String, Object>();
		input1.put("k1", "v1");
		WorkflowTask wt2 = new WorkflowTask();
		wt2.setName("junit_task_2");
		wt2.setTaskReferenceName("xdt1");

		Map<String, Object> input2 = new HashMap<String, Object>();
		input2.put("k2", "v2");
		
		WorkflowTask wt3 = new WorkflowTask();
		wt3.setName("junit_task_3");
		wt3.setTaskReferenceName("xdt2");
		
		HashMap<String, Object> dynamicTasksInput = new HashMap<>();
		dynamicTasksInput.put("xdt1", input1);
		dynamicTasksInput.put("xdt2", input2);
		t1.getOutputData().put("dynamicTasks", Arrays.asList(wt2, wt3));
		t1.getOutputData().put("dynamicTasksInput", dynamicTasksInput);
		t1.setStatus(Status.COMPLETED);
		
		ess.updateTask(t1);
		
		Task t2 = ess.poll("junit_task_2", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));
		assertEquals("xdt1", t2.getReferenceTaskName());
		assertTrue(t2.getInputData().containsKey("k1"));
		assertEquals("v1", t2.getInputData().get("k1"));
		Map<String, Object> output = new HashMap<String, Object>();
		output.put("ok1", "ov1");
		t2.setOutputData(output);
		t2.setStatus(Status.FAILED);
		ess.updateTask(t2);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(2, es.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_2")).count());
		assertTrue(es.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_2")).allMatch(t -> t.getDynamicWorkflowTask() != null));
		
		t2 = ess.poll("junit_task_2", "test");
		assertTrue(ess.ackTaskRecieved(t2.getTaskId(), "test"));
		assertEquals("xdt1", t2.getReferenceTaskName());
		assertTrue(t2.getInputData().containsKey("k1"));
		assertEquals("v1", t2.getInputData().get("k1"));
		t2.setOutputData(output);
		t2.setStatus(Status.COMPLETED);
		ess.updateTask(t2);
		

		Task t3 = ess.poll("junit_task_3", "test");
		assertTrue(ess.ackTaskRecieved(t3.getTaskId(), "test"));
		assertEquals("xdt2", t3.getReferenceTaskName());
		assertTrue(t3.getInputData().containsKey("k2"));
		assertEquals("v2", t3.getInputData().get("k2"));
		output = new HashMap<String, Object>();
		output.put("ok1", "ov1");
		t3.setOutputData(output);
		t3.setStatus(Status.COMPLETED);
		ess.updateTask(t3);
		
		Workflow wf = ess.getExecutionStatus(wfid, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
		
		// Check the output
		Task joinTask = wf.getTaskByRefName("dynamicfanouttask_join");
		assertEquals("Found:" + joinTask.getOutputData(), 2, joinTask.getOutputData().keySet().size());
		Set<String> joinTaskOutput = joinTask.getOutputData().keySet();
		System.out.println("joinTaskOutput=" + joinTaskOutput);
		for(String key: joinTask.getOutputData().keySet()){
			assertTrue(key.equals("xdt1") || key.equals("xdt2"));
			assertEquals("ov1", ((Map<String, Object>)joinTask.getOutputData().get(key)).get("ok1"));
		}
		
		taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(retryCount);
		taskDef.setRetryDelaySeconds(1);
		ms.updateTaskDef(taskDef);
	}

	private void createForkJoinWorkflow() throws Exception {
		
		WorkflowDef def = new WorkflowDef();
		def.setName(FORK_JOIN_WF);
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));
		
		WorkflowTask fanout = new WorkflowTask();
		fanout.setType(Type.FORK_JOIN.name());
		fanout.setTaskReferenceName("fanouttask");
		
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
		
		fanout.getForkTasks().add(Arrays.asList(wft1, wft3));
		fanout.getForkTasks().add(Arrays.asList(wft2));
		
		def.getTasks().add(fanout);
		
		WorkflowTask join = new WorkflowTask();
		join.setType(Type.JOIN.name());
		join.setTaskReferenceName("fanouttask_join");
		join.setJoinOn(Arrays.asList("t3","t2"));
		
		def.getTasks().add(join);
		def.getTasks().add(wft4);
		ms.updateWorkflowDef(def);

	}
	
	private void createForkJoinNestedWorkflow() throws Exception {
		
		WorkflowDef def = new WorkflowDef();
		def.setName(FORK_JOIN_NESTED_WF);
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));
		
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		ip1.put("case", "workflow.input.case");
		
		WorkflowTask[] tasks = new WorkflowTask[21];
		
		for(int i = 10; i < 21; i++){
			WorkflowTask wft = new WorkflowTask();
			wft.setName("junit_task_" + i);
			wft.setInputParameters(ip1);
			wft.setTaskReferenceName("t" + i);
			tasks[i] = wft;
		}
		
		WorkflowTask d1 = new WorkflowTask();
		d1.setType(Type.DECISION.name());
		d1.setName("Decision");
		d1.setTaskReferenceName("d1");
		d1.setInputParameters(ip1);
		d1.setDefaultCase(Arrays.asList(tasks[18], tasks[20]));
		d1.setCaseValueParam("case");
		Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
		decisionCases.put("a", Arrays.asList(tasks[16], tasks[19], tasks[20]));
		decisionCases.put("b", Arrays.asList(tasks[17], tasks[20]));
		d1.setDecisionCases(decisionCases);
		
		WorkflowTask subWorkflow = new WorkflowTask();
		subWorkflow.setType(Type.SUB_WORKFLOW.name());
		SubWorkflowParams sw = new SubWorkflowParams();
		sw.setName(LINEAR_WORKFLOW_T1_T2);
		subWorkflow.setSubWorkflowParam(sw);
		subWorkflow.setTaskReferenceName("sw1");

		WorkflowTask fork2 = new WorkflowTask();
		fork2.setType(Type.FORK_JOIN.name());
		fork2.setName("fork2");
		fork2.setTaskReferenceName("fork2");		
		fork2.getForkTasks().add(Arrays.asList(tasks[12], tasks[14]));
		fork2.getForkTasks().add(Arrays.asList(tasks[13], d1));
		
		WorkflowTask join2 = new WorkflowTask();
		join2.setType(Type.JOIN.name());
		join2.setTaskReferenceName("join2");
		join2.setJoinOn(Arrays.asList("t14","t20"));
		
		WorkflowTask fork1 = new WorkflowTask();
		fork1.setType(Type.FORK_JOIN.name());
		fork1.setTaskReferenceName("fork1");
		fork1.getForkTasks().add(Arrays.asList(tasks[11]));
		fork1.getForkTasks().add(Arrays.asList(fork2, join2));
		fork1.getForkTasks().add(Arrays.asList(subWorkflow));
		
		
		WorkflowTask join1 = new WorkflowTask();
		join1.setType(Type.JOIN.name());
		join1.setTaskReferenceName("join1");
		join1.setJoinOn(Arrays.asList("t11","join2","sw1"));
		
		def.getTasks().add(fork1);
		def.getTasks().add(join1);
		def.getTasks().add(tasks[15]);
		
		ms.updateWorkflowDef(def);

	
	}
	
	private void createDynamicForkJoinWorkflowDefs() throws Exception {
		
		WorkflowDef def = new WorkflowDef();
		def.setName(DYNAMIC_FORK_JOIN_WF);
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));

		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_1");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("dt1");

		WorkflowTask fanout = new WorkflowTask();
		fanout.setType(Type.FORK_JOIN_DYNAMIC.name());
		fanout.setTaskReferenceName("dynamicfanouttask");
		fanout.setDynamicForkTasksParam("dynamicTasks");
		fanout.setDynamicForkTasksInputParamName("dynamicTasksInput");
		fanout.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
		fanout.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");
		
		WorkflowTask join = new WorkflowTask();
		join.setType(Type.JOIN.name());
		join.setTaskReferenceName("dynamicfanouttask_join");
		
		def.getTasks().add(wft1);
		def.getTasks().add(fanout);
		def.getTasks().add(join);		
		
		ms.updateWorkflowDef(def);

	}
	
	@SuppressWarnings("deprecation")
	private void createDynamicForkJoinWorkflowDefsLegacy() throws Exception {
		
		WorkflowDef def = new WorkflowDef();
		def.setName(DYNAMIC_FORK_JOIN_WF_LEGACY);
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));

		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_1");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("dt1");

		WorkflowTask fanout = new WorkflowTask();
		fanout.setType(Type.FORK_JOIN_DYNAMIC.name());
		fanout.setTaskReferenceName("dynamicfanouttask");
		fanout.setDynamicForkJoinTasksParam("dynamicTasks");		
		fanout.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
		fanout.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");
		
		WorkflowTask join = new WorkflowTask();
		join.setType(Type.JOIN.name());
		join.setTaskReferenceName("dynamicfanouttask_join");
		
		def.getTasks().add(wft1);
		def.getTasks().add(fanout);
		def.getTasks().add(join);		
		
		ms.updateWorkflowDef(def);

	}
	
	private void createConditionalWF() throws Exception {
		
		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_1");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("t1");
		
		WorkflowTask wft2 = new WorkflowTask();
		wft2.setName("junit_task_2");
		Map<String, Object> ip2 = new HashMap<>();
		ip2.put("tp1", "workflow.input.param1");
		wft2.setInputParameters(ip2);
		wft2.setTaskReferenceName("t2");
		
		WorkflowTask wft3 = new WorkflowTask();
		wft3.setName("junit_task_3");
		Map<String, Object> ip3 = new HashMap<>();
		ip2.put("tp3", "workflow.input.param2");
		wft3.setInputParameters(ip3);
		wft3.setTaskReferenceName("t3");
				
		WorkflowDef def2 = new WorkflowDef();
		def2.setName(COND_TASK_WF);
		def2.setDescription(COND_TASK_WF);
		def2.setInputParameters(Arrays.asList("param1", "param2"));
		
		WorkflowTask c2 = new WorkflowTask();
		c2.setType(Type.DECISION.name());
		c2.setCaseValueParam("case");
		c2.setName("conditional2");
		c2.setTaskReferenceName("conditional2");
		Map<String, List<WorkflowTask>> dc = new HashMap<>();
		dc.put("one", Arrays.asList(wft1, wft3));
		dc.put("two", Arrays.asList(wft2));
		c2.setDecisionCases(dc);
		c2.getInputParameters().put("case", "workflow.input.param2");
		
		
		WorkflowTask condition = new WorkflowTask();
		condition.setType(Type.DECISION.name());
		condition.setCaseValueParam("case");
		condition.setName("conditional");
		condition.setTaskReferenceName("conditional");
		Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
		decisionCases.put("nested", Arrays.asList(c2));
		decisionCases.put("three", Arrays.asList(wft3));
		condition.setDecisionCases(decisionCases);
		condition.getInputParameters().put("case", "workflow.input.param1");
		condition.getDefaultCase().add(wft2);
		def2.getTasks().add(condition);
		
		WorkflowTask notifyTask = new WorkflowTask();
		notifyTask.setName("junit_task_4");
		notifyTask.setTaskReferenceName("junit_task_4");
		
		WorkflowTask finalTask = new WorkflowTask();
		finalTask.setName("finalcondition");
		finalTask.setTaskReferenceName("tf");
		finalTask.setType(Type.DECISION.name());
		finalTask.setCaseValueParam("finalCase");
		Map<String, Object> fi = new HashMap<>();
		fi.put("finalCase", "workflow.input.finalCase");
		finalTask.setInputParameters(fi);
		finalTask.getDecisionCases().put("notify", Arrays.asList(notifyTask));
		
		def2.getTasks().add(finalTask );
		System.out.println(new ObjectMapper().writeValueAsString(def2));
		ms.updateWorkflowDef(def2);
		
	}
	
	
	@Test
	public void testDefDAO() throws Exception {
		List<TaskDef> taskDefs = ms.getTaskDefs();
		assertNotNull(taskDefs);
		assertTrue(!taskDefs.isEmpty());
	}
	
	
	
	@Test
	public void testSimpleWorkflow() throws Exception {
		
		clearWorkflows();
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1";
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		System.out.println("testSimpleWorkflow.wfid=" + wfid);
		assertNotNull(wfid);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(es.getReasonForIncompletion(), WorkflowStatus.RUNNING, es.getStatus());
		
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(1, es.getTasks().size());		//The very first task is the one that should be scheduled.
		
		
		boolean failed = false;
		try{
			provider.rewind(wfid);
		}catch(ApplicationException ae){
			failed = true;
		}
		assertTrue(failed);
				
		// Polling for the first task should return the same task as before
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertEquals("junit_task_1", task.getTaskType());
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		assertEquals(wfid, task.getWorkflowInstanceId());
		
		String task1Op = "task1.Done";
		List<Task> tasks = ess.getTasks(task.getTaskType(), null, 1);
		assertNotNull(tasks);
		assertEquals(1, tasks.size());
		task = tasks.get(0);
		
		Workflow workflow = ess.getExecutionStatus(task.getWorkflowInstanceId(), false);
		System.out.println("task workflow = " + workflow.getWorkflowType() + "," + workflow.getInput());
		assertEquals(wfid, task.getWorkflowInstanceId());
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, false);
		assertNotNull(es);
		assertNotNull(es.getOutput());
		assertTrue("Found "  +es.getOutput().toString(), es.getOutput().containsKey("o3"));
		assertEquals("task1.Done", es.getOutput().get("o3"));
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertEquals("junit_task_2", task.getTaskType());
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull("Found=" + task.getInputData(), task2Input);
		assertEquals(task1Op, task2Input);
		
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		task.setReasonForIncompletion("unit test failure");
		ess.updateTask(task);
		
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		tasks = es.getTasks();
		assertNotNull(tasks);
		assertEquals(2, tasks.size());
		
		assertTrue("Found "  +es.getOutput().toString(), es.getOutput().containsKey("o3"));
		assertEquals("task1.Done", es.getOutput().get("o3"));

	}

	
	private void clearWorkflows() throws Exception {
		List<String> workflows = ms.getWorkflowDefs().stream().map(def -> def.getName()).collect(Collectors.toList());
		for(String wfName : workflows){
			List<String> running = ess.getRunningWorkflows(wfName);
			for(String wfid : running){
				provider.terminateWorkflow(wfid, "cleanup");
			}
		}
		queue.queuesDetail().keySet().stream().forEach(queueName -> {
			queue.flush(queueName);
		});
	}
	
	@Test	
	public void testLongRunning() throws Exception {
		
		clearWorkflows();
		
		WorkflowDef found = ms.getWorkflowDef(LONG_RUNNING, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1";
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		System.out.println("testLongRunning.wfid=" + wfid);
		assertNotNull(wfid);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		// Check the queue
		assertEquals(Integer.valueOf(1), ess.getTaskQueueSizes(Arrays.asList("junit_task_1")).get("junit_task_1"));
		///
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));

		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		
		String task1Op = "task1.In.Progress";
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.IN_PROGRESS);
		task.setCallbackAfterSeconds(5);
		ess.updateTask(task);
		String taskId = task.getTaskId();

		// Check the queue
		assertEquals(Integer.valueOf(1), ess.getTaskQueueSizes(Arrays.asList("junit_task_1")).get("junit_task_1"));
		///
		

		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());

		// Polling for next task should not return anything
		Task task2 = ess.poll("junit_task_2", "task2.junit.worker");
		assertNull(task2);
		
		task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNull(task);
		
		Uninterruptibles.sleepUninterruptibly(5, TimeUnit.SECONDS);
		// Polling for the first task should return the same task as before
		task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		assertEquals(task.getTaskId(), taskId);
		
		task1Op = "task1.Done";
		List<Task> tasks = ess.getTasks(task.getTaskType(), null, 1);
		assertNotNull(tasks);
		assertEquals(1, tasks.size());
		assertEquals(wfid, task.getWorkflowInstanceId());
		task = tasks.get(0);
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);

		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		task.setReasonForIncompletion("unit test failure");
		ess.updateTask(task);
		
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		tasks = es.getTasks();
		assertNotNull(tasks);
		assertEquals(2, tasks.size());
		

	}
	
	@Test
	public void testConcurrentWorkflowExecutions() throws Exception {

		int count = 3;

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_concurrrent";
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String[] wfids = new String[count];
		
		for(int i = 0; i < count; i++){
			String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId, input);
			System.out.println("testConcurrentWorkflowExecutions.wfid=" + wfid);
			assertNotNull(wfid);
			
			List<String> ids = ess.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
			assertNotNull(ids);
			assertTrue("found no ids: " + ids, ids.size() > 0);		//if there are concurrent tests running, this would be more than 1
			boolean foundId = false;
			for(String id : ids){
				if(id.equals(wfid)){
					foundId = true;
				}
			}
			assertTrue(foundId);
			wfids[i] = wfid;
		}
		
		
		String task1Op = "";
		for(int i = 0; i < count; i++){
			
			Task task = ess.poll("junit_task_1", "task1.junit.worker");
			assertNotNull(task);
			assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			
			String param1 = (String) task.getInputData().get("p1");
			String param2 = (String) task.getInputData().get("p2");
			
			assertNotNull(param1);
			assertNotNull(param2);
			assertEquals("p1 value", param1);
			assertEquals("p2 value", param2);
			
			task1Op = "task1.output->" + param1 + "." + param2;
			task.getOutputData().put("op", task1Op);
			task.setStatus(Status.COMPLETED);
			ess.updateTask(task);		
		}
		
		for(int i = 0; i < count; i++){
			Task task = ess.poll("junit_task_2", "task2.junit.worker");
			assertNotNull(task);
			assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			
			String task2Input = (String) task.getInputData().get("tp2");
			assertNotNull(task2Input);
			assertEquals(task1Op, task2Input);
			
			task2Input = (String) task.getInputData().get("tp1");
			assertNotNull(task2Input);
			assertEquals(inputParam1, task2Input);
			
			task.setStatus(Status.COMPLETED);
			ess.updateTask(task);
		}

		List<Workflow> wfs = ess.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
		wfs.forEach(wf -> { 
			assertEquals(WorkflowStatus.COMPLETED, wf.getStatus());
		});
	
		
	}
	
	@Test
	public void testCaseStatements() throws Exception {
		createConditionalWF();
		
		String correlationId = "testCaseStatements: " + System.currentTimeMillis();
		Map<String, Object> input = new HashMap<String, Object>();
		String wfid;
		String[] sequence;

		
		//default case
		input.put("param1", "xxx");
		input.put("param2", "two");
		wfid = provider.startWorkflow(COND_TASK_WF, 1, correlationId, input);
		System.out.println("testCaseStatements.wfid=" + wfid);
		assertNotNull(wfid);
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		Task task = ess.poll("junit_task_2", "junit");
		assertNotNull(task);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		assertEquals(3, es.getTasks().size());
		
		///
		
		
		//nested - one
		input.put("param1", "nested");
		input.put("param2", "one");
		wfid = provider.startWorkflow(COND_TASK_WF, 1, correlationId, input);
		System.out.println("testCaseStatements.wfid=" + wfid);
		assertNotNull(wfid);
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		sequence = new String[]{"junit_task_1","junit_task_3"};
		
		validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(),SystemTaskType.DECISION.name(), "junit_task_1","junit_task_3",SystemTaskType.DECISION.name()}, 5);
		//
		
		//nested - two
		input.put("param1", "nested");
		input.put("param2", "two");
		wfid = provider.startWorkflow(COND_TASK_WF, 1, correlationId, input);
		System.out.println("testCaseStatements.wfid=" + wfid);
		assertNotNull(wfid);
		sequence = new String[]{"junit_task_2"};
		validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(),SystemTaskType.DECISION.name(), "junit_task_2",SystemTaskType.DECISION.name()}, 4);
		//
		
		//three
		input.put("param1", "three");
		input.put("param2", "two");
		input.put("finalCase","notify");
		wfid = provider.startWorkflow(COND_TASK_WF, 1, correlationId, input);
		System.out.println("testCaseStatements.wfid=" + wfid);
		assertNotNull(wfid);
		sequence = new String[]{"junit_task_3","junit_task_4"};
		validate(wfid, sequence, new String[]{SystemTaskType.DECISION.name(),"junit_task_3",SystemTaskType.DECISION.name(), "junit_task_4"}, 3);
		//
		
	}

	private void validate(String wfid, String[] sequence, String[] executedTasks, int expectedTotalTasks) throws Exception {
		for(int i = 0; i < sequence.length; i++){
			String t = sequence[i];
			Task task = getTask(t);
			if(task == null){
				System.out.println("Missing task for " + t + ", below are the workflow tasks completed...");
				Workflow workflow = ess.getExecutionStatus(wfid, true);
				for(Task x : workflow.getTasks()){
					System.out.println(x.getTaskType() + "/" + x.getReferenceTaskName());
				}
			}
			assertNotNull("No task for " + t, task);
			assertEquals(wfid, task.getWorkflowInstanceId());
			task.setStatus(Status.COMPLETED);
			ess.updateTask(task);
			
			Workflow workflow = ess.getExecutionStatus(wfid, true);
			assertNotNull(workflow);
			assertTrue(!workflow.getTasks().isEmpty());
			if(i < sequence.length-1){
				assertEquals(WorkflowStatus.RUNNING, workflow.getStatus());
			}else{
				workflow = ess.getExecutionStatus(wfid, true);
				List<Task> workflowTasks = workflow.getTasks();
				assertEquals(workflowTasks.toString(), 	executedTasks.length, workflowTasks.size());
				for(int k = 0; k < executedTasks.length; k++){
					assertEquals(workflowTasks.toString(), executedTasks[k], workflowTasks.get(k).getTaskType());
				}
				
				assertEquals(WorkflowStatus.COMPLETED, workflow.getStatus());
			}
		}
	}

	private Task getTask(String tt) throws Exception {
		Task task = null;
		int count = 2;
		do{
			task = ess.poll(tt, "junit");
			if(task == null){
				count--;
			}
			if(count < 0){
				break;
			}
			
		}while(task == null);
		if(task != null){
			ess.ackTaskRecieved(task.getTaskId(), "junit");
		}
		return task;
	}
	
	@Test
	public void testRetries() throws Exception {

		String taskName = "junit_task_2";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(2);
		taskDef.setRetryDelaySeconds(1);
		ms.updateTaskDef(taskDef);

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1";
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		System.out.println("testRetries.wfid=" + wfid);
		assertNotNull(wfid);
		
		List<String> ids = ess.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
		assertNotNull(ids);
		assertTrue("found no ids: " + ids, ids.size() > 0);		//if there are concurrent tests running, this would be more than 1
		boolean foundId = false;
		for(String id : ids){
			if(id.equals(wfid)){
				foundId = true;
			}
		}
		assertTrue(foundId);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			

		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		//fail the task twice and then succeed
		verify(inputParam1, wfid, task1Op, true);
		Uninterruptibles.sleepUninterruptibly(2, TimeUnit.SECONDS);
		verify(inputParam1, wfid, task1Op, false);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		assertEquals(3, es.getTasks().size());		//task 1, and 2 of the task 2

		assertEquals("junit_task_1", es.getTasks().get(0).getTaskType());
		assertEquals("junit_task_2", es.getTasks().get(1).getTaskType());
		assertEquals("junit_task_2", es.getTasks().get(2).getTaskType());
		assertEquals(Status.COMPLETED, es.getTasks().get(0).getStatus());
		assertEquals(Status.FAILED, es.getTasks().get(1).getStatus());
		assertEquals(Status.COMPLETED, es.getTasks().get(2).getStatus());
		assertEquals(es.getTasks().get(1).getTaskId(), es.getTasks().get(2).getRetriedTaskId());
		

	
	}
	
	@Test
	public void testSuccess() throws Exception {

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		List<String> ids = ess.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
		assertNotNull(ids);
		assertTrue("found no ids: " + ids, ids.size() > 0);		//if there are concurrent tests running, this would be more than 1
		boolean foundId = false;
		for(String id : ids){
			if(id.equals(wfid)){
				foundId = true;
			}
		}
		assertTrue(foundId);
		
		List<Workflow> byCorrelationId = ess.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
		assertNotNull(byCorrelationId);
		assertTrue(!byCorrelationId.isEmpty());
		assertEquals(1, byCorrelationId.size());
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// The first task would be marked as scheduled
		assertEquals(1, es.getTasks().size());
		assertEquals(Task.Status.SCHEDULED, es.getTasks().get(0).getStatus());

		// decideNow should be idempotent if re-run on the same state!
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(1, es.getTasks().size());
		Task t = es.getTasks().get(0);
		assertEquals(Status.SCHEDULED, t.getStatus());
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		
		assertNotNull(task);
		assertEquals(t.getTaskId(), task.getTaskId());
		es = ess.getExecutionStatus(wfid, true);
		t = es.getTasks().get(0);
		assertEquals(Status.IN_PROGRESS, t.getStatus());
		String taskId = t.getTaskId();
		
		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getTaskId().equals(taskId)){
				assertEquals(Status.COMPLETED, wfTask.getStatus());
			} else {
				assertEquals(Status.SCHEDULED, wfTask.getStatus());
			}
		});
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));
		assertNotNull(task);
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		// Check the tasks, at this time there should be 2 task
		assertEquals(es.getTasks().size(), 2);
		es.getTasks().forEach(wfTask -> {
				assertEquals(wfTask.getStatus(), Status.COMPLETED);
		});
		
		System.out.println("Total tasks=" + es.getTasks().size());
		assertTrue(es.getTasks().size() < 10);
		

	}
	
	@Test
	public void testDeciderUpdate() throws Exception {

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		Workflow workflow = provider.getWorkflow(wfid, false);
		long updated1 = workflow.getUpdateTime();
		Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
		ds.decide(wfid, provider);
		workflow = provider.getWorkflow(wfid, false);
		long updated2 = workflow.getUpdateTime();
		assertTrue(updated2 > updated1);
		
		provider.terminateWorkflow(wfid, "done");
	}
	
	@Test
	@Ignore
	//Ignore for now, will improve this in the future
	public void testFailurePoints() throws Exception {

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// The first task would be marked as scheduled
		assertEquals(1, es.getTasks().size());
		assertEquals(Task.Status.SCHEDULED, es.getTasks().get(0).getStatus());

		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		String taskId = task.getTaskId();
		
		String task1Op = "task1.output";
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		try{
			ess.updateTask(task);
		}catch(Exception e){
			ess.updateTask(task);
		}
		
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getTaskId().equals(taskId)){
				assertEquals(Status.COMPLETED, wfTask.getStatus());
			} else {
				assertEquals(Status.SCHEDULED, wfTask.getStatus());
			}
		});
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));
		assertNotNull(task);
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		// Check the tasks, at this time there should be 2 task
		assertEquals(es.getTasks().size(), 2);
		es.getTasks().forEach(wfTask -> {
				assertEquals(wfTask.getStatus(), Status.COMPLETED);
		});
		
		System.out.println("Total tasks=" + es.getTasks().size());
		assertTrue(es.getTasks().size() < 10);
		

	}
	
	@Test
	public void testDeciderMix() throws Exception {

		ExecutorService executors = Executors.newFixedThreadPool(3);
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		List<String> ids = ess.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
		assertNotNull(ids);
		assertTrue("found no ids: " + ids, ids.size() > 0);		//if there are concurrent tests running, this would be more than 1
		boolean foundId = false;
		for(String id : ids){
			if(id.equals(wfid)){
				foundId = true;
			}
		}
		assertTrue(foundId);
		
		List<Workflow> byCorrelationId = ess.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
		assertNotNull(byCorrelationId);
		assertTrue(!byCorrelationId.isEmpty());
		assertEquals(1, byCorrelationId.size());
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// The first task would be marked as scheduled
		assertEquals(1, es.getTasks().size());
		assertEquals(Task.Status.SCHEDULED, es.getTasks().get(0).getStatus());
		
		List<Future<Void>> futures = new LinkedList<>();
		for(int i = 0; i < 10; i++){
			futures.add(executors.submit(()->{
				ds.decide(wfid, provider);
				return null;
			}));
		}
		for(Future<Void> future : futures){
			future.get();
		}
		futures.clear();
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// The first task would be marked as scheduled
		assertEquals(1, es.getTasks().size());
		assertEquals(Task.Status.SCHEDULED, es.getTasks().get(0).getStatus());
		

		// decideNow should be idempotent if re-run on the same state!
		ds.decide(wfid, provider);
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(1, es.getTasks().size());
		Task t = es.getTasks().get(0);
		assertEquals(Status.SCHEDULED, t.getStatus());
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		
		assertNotNull(task);
		assertEquals(t.getTaskId(), task.getTaskId());
		es = ess.getExecutionStatus(wfid, true);
		t = es.getTasks().get(0);
		assertEquals(Status.IN_PROGRESS, t.getStatus());
		String taskId = t.getTaskId();
		
		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getTaskId().equals(taskId)){
				assertEquals(Status.COMPLETED, wfTask.getStatus());
			} else {
				assertEquals(Status.SCHEDULED, wfTask.getStatus());
			}
		});
		
		//Run sweep 10 times!		
		for(int i = 0; i < 10; i++){
			futures.add(executors.submit(()->{
				long s = System.currentTimeMillis();
				ds.decide(wfid, provider);				
				System.out.println("Took " + (System.currentTimeMillis()-s) + " ms to run decider");
				return null;
			}));
		}
		for(Future<Void> future : futures){
			future.get();
		}
		futures.clear();
		
		es = ess.getExecutionStatus(wfid, true);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals(2, es.getTasks().size());
		
		System.out.println("Workflow tasks=" + es.getTasks());
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));
		assertNotNull(task);
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		// Check the tasks, at this time there should be 2 task
		assertEquals(es.getTasks().size(), 2);
		es.getTasks().forEach(wfTask -> {
				assertEquals(wfTask.getStatus(), Status.COMPLETED);
		});
		
		System.out.println("Total tasks=" + es.getTasks().size());
		assertTrue(es.getTasks().size() < 10);
	}
	
	@Test
	public void testFailures() throws Exception {
		WorkflowDef errorWorkflow = ms.getWorkflowDef(FORK_JOIN_WF, 1);
		assertNotNull("Error workflow is not defined", errorWorkflow);
		
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(0);
		ms.updateTaskDef(taskDef);
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		assertNotNull(found.getFailureWorkflow());
		assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		input.put("failureWfName", "FanInOutTest");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);

		Task task = getTask("junit_task_1");
		assertNotNull(task);
		task.setStatus(Status.FAILED);
		ess.updateTask(task);

		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.FAILED, es.getStatus());
		
		List<Workflow> failureInstances = ess.getWorkflowInstances(FORK_JOIN_WF, wfid, false, false);
		assertNotNull(failureInstances);
		assertEquals(1, failureInstances.size());
		assertEquals(wfid, failureInstances.get(0).getCorrelationId());
		taskDef.setRetryCount(RETRY_COUNT);
		ms.updateTaskDef(taskDef);
	
	}
	
	@Test
	public void testRetry() throws Exception {
		WorkflowDef errorWorkflow = ms.getWorkflowDef(FORK_JOIN_WF, 1);
		assertNotNull("Error workflow is not defined", errorWorkflow);
		
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		int retryCount = taskDef.getRetryCount();
		taskDef.setRetryCount(1);
		int retryDelay = taskDef.getRetryDelaySeconds();
		taskDef.setRetryDelaySeconds(0);
		ms.updateTaskDef(taskDef);
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		assertNotNull(found.getFailureWorkflow());
		assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);

		Task task = getTask("junit_task_1");
		assertNotNull(task);
		task.setStatus(Status.FAILED);
		ess.updateTask(task);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = getTask("junit_task_1");
		assertNotNull(task);
		task.setStatus(Status.FAILED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.FAILED, es.getStatus());
		
		provider.retry(wfid);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = getTask("junit_task_1");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = getTask("junit_task_2");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		
		assertEquals(3, es.getTasks().stream().filter(t -> t.getTaskType().equals("junit_task_1")).count());
		
		taskDef.setRetryCount(retryCount);
		taskDef.setRetryDelaySeconds(retryDelay);
		ms.updateTaskDef(taskDef);
	
	}
	
	@Test
	public void testRestart() throws Exception {
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(0);
		ms.updateTaskDef(taskDef);
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		assertNotNull(found.getFailureWorkflow());
		assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);

		Task task = getTask("junit_task_1");
		task.setStatus(Status.FAILED);
		ess.updateTask(task);

		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.FAILED, es.getStatus());
		
		provider.rewind(es.getWorkflowId());
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = getTask("junit_task_1");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = getTask("junit_task_2");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		
	
	}
	
	
	@Test
	public void testTimeout() throws Exception {
		
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(1);
		taskDef.setTimeoutSeconds(1);
		taskDef.setRetryDelaySeconds(0);
		taskDef.setTimeoutPolicy(TimeoutPolicy.RETRY);
		ms.updateTaskDef(taskDef);
		
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		assertNotNull(found.getFailureWorkflow());
		assertFalse(StringUtils.isBlank(found.getFailureWorkflow()));
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		input.put("failureWfName", "FanInOutTest");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		//Ensure that we have a workflow queued up for evaluation here...
		long size = queue.getSize(WorkflowExecutor.deciderQueue);
		assertEquals(1, size);
		
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		assertEquals("fond: " +es.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 1, es.getTasks().size());

		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			
		
		
		//Ensure that we have a workflow queued up for evaluation here...
		size = queue.getSize(WorkflowExecutor.deciderQueue);
		assertEquals(1, size);
				
				
		Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
		sweeper.sweep(Arrays.asList(wfid), provider);		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals("fond: " +es.getTasks().stream().map(Task::toString).collect(Collectors.toList()), 2, es.getTasks().size());
		
		Task task1 = es.getTasks().get(0);
		assertEquals(Status.TIMED_OUT, task1.getStatus());
		Task task2 = es.getTasks().get(1);
		assertEquals(Status.SCHEDULED, task2.getStatus());
		
		task = ess.poll(task2.getTaskDefName(), "task1.junit.worker");
		assertNotNull(task);
		assertEquals(wfid, task.getWorkflowInstanceId());
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));
		
		Uninterruptibles.sleepUninterruptibly(3, TimeUnit.SECONDS);
		ds.decide(wfid, provider);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(2, es.getTasks().size());
		
		assertEquals(Status.TIMED_OUT, es.getTasks().get(0).getStatus());
		assertEquals(Status.TIMED_OUT, es.getTasks().get(1).getStatus());
		assertEquals(WorkflowStatus.TIMED_OUT, es.getStatus());
		
		List<Workflow> failureInstances = ess.getWorkflowInstances(FORK_JOIN_WF, wfid, false, false);
		assertNotNull(failureInstances);
		assertEquals(failureInstances.stream().map(Workflow::getCorrelationId).collect(Collectors.toList()).toString(), 1, failureInstances.size());
		assertEquals(wfid, failureInstances.get(0).getCorrelationId());
		assertTrue(!failureInstances.get(0).getStatus().isTerminal());

		assertEquals(1, queue.getSize(WorkflowExecutor.deciderQueue));		
		
		taskDef.setTimeoutSeconds(0);
		taskDef.setRetryCount(RETRY_COUNT);
		ms.updateTaskDef(taskDef);
	
	}
	
	@Test
	public void testReruns() throws Exception {
	
		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
				
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// Check the tasks, at this time there should be 1 task
		assertEquals(es.getTasks().size(), 1);
		Task t = es.getTasks().get(0);
		assertEquals(Status.SCHEDULED, t.getStatus());
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			
		assertEquals(t.getTaskId(), task.getTaskId());
	
		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
			
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getTaskId().equals(t.getTaskId())){
				assertEquals(wfTask.getStatus(), Status.COMPLETED);
			} else {
				assertEquals(wfTask.getStatus(), Status.SCHEDULED);
			}
		});
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		
		// Now rerun lets rerun the workflow from the second task
		RerunWorkflowRequest request = new RerunWorkflowRequest();
		request.setReRunFromWorkflowId(wfid);
		request.setReRunFromTaskId(es.getTasks().get(1).getTaskId());
		
		String reRunwfid = provider.rerun(request);
		
		Workflow esRR = ess.getExecutionStatus(reRunwfid, true);
		assertNotNull(esRR);
		assertEquals(esRR.getReasonForIncompletion(), WorkflowStatus.RUNNING, esRR.getStatus());
		// Check the tasks, at this time there should be 2 tasks
		// first one is skipped and the second one is scheduled
		assertEquals(esRR.getTasks().toString(), 2, esRR.getTasks().size());
		assertEquals(Status.SKIPPED, esRR.getTasks().get(0).getStatus());
		Task tRR = esRR.getTasks().get(1);
		assertEquals(esRR.getTasks().toString(), Status.SCHEDULED, tRR.getStatus());
		assertEquals(tRR.getTaskType(), "junit_task_2");
	
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			
		task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(reRunwfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		
		//////////////////////
		// Now rerun the entire workflow 
		RerunWorkflowRequest request1 = new RerunWorkflowRequest();
		request1.setReRunFromWorkflowId(wfid);
		
		String reRunwfid1 = provider.rerun(request1);
		
		es = ess.getExecutionStatus(reRunwfid1, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// Check the tasks, at this time there should be 1 task
		assertEquals(es.getTasks().size(), 1);
		assertEquals(Status.SCHEDULED, es.getTasks().get(0).getStatus());
		
		task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			
	
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			

		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());

		

		
	}
	

	@Test
	public void testTaskSkipping() throws Exception {
	
		String taskName = "junit_task_1";
		TaskDef taskDef = ms.getTaskDef(taskName);
		taskDef.setRetryCount(0);
		taskDef.setTimeoutSeconds(0);
		ms.updateTaskDef(taskDef);
		
		
		WorkflowDef found = ms.getWorkflowDef(TEST_WORKFLOW_NAME_3, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + UUID.randomUUID().toString();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(TEST_WORKFLOW_NAME_3, 1, correlationId , input);
		assertNotNull(wfid);
		
		// Now Skip the second task
		provider.skipTaskFromWorkflow(wfid, "t2", null);
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		// Check the tasks, at this time there should be 3 task
		assertEquals(2, es.getTasks().size());
		assertEquals(Task.Status.SCHEDULED, es.getTasks().get(0).getStatus());
		assertEquals(Task.Status.SKIPPED, es.getTasks().get(1).getStatus());
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			

		assertEquals("t1", task.getReferenceTaskName());
	
		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
	
		// If we get the full workflow here then, last task should be completed and the next task should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getReferenceTaskName().equals("t1")){
				assertEquals(Status.COMPLETED, wfTask.getStatus());
			} else if(wfTask.getReferenceTaskName().equals("t2")){
				assertEquals(Status.SKIPPED, wfTask.getStatus());
			} else {
				assertEquals(Status.SCHEDULED, wfTask.getStatus());
			}
		});
		
		task = ess.poll("junit_task_3", "task3.junit.worker");
		assertNotNull(task);
		assertEquals(Status.IN_PROGRESS, task.getStatus());
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task3.junit.worker"));			

		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());	
		

	}

	@Test
	public void testPauseResume() throws Exception {

		WorkflowDef found = ms.getWorkflowDef(LINEAR_WORKFLOW_T1_T2, 1);
		assertNotNull(found);
		
		String correlationId = "unit_test_1" + System.nanoTime();
		Map<String, Object> input = new HashMap<String, Object>();
		String inputParam1 = "p1 value";
		input.put("param1", inputParam1);
		input.put("param2", "p2 value");
		String wfid = provider.startWorkflow(LINEAR_WORKFLOW_T1_T2, 1, correlationId , input);
		assertNotNull(wfid);
		
		List<String> ids = ess.getRunningWorkflows(LINEAR_WORKFLOW_T1_T2);
		assertNotNull(ids);
		assertTrue("found no ids: " + ids, ids.size() > 0);		//if there are concurrent tests running, this would be more than 1
		boolean foundId = false;
		for(String id : ids){
			if(id.equals(wfid)){
				foundId = true;
			}
		}
		assertTrue(foundId);
		
		List<Workflow> byCorrelationId = ess.getWorkflowInstances(LINEAR_WORKFLOW_T1_T2, correlationId, false, false);
		assertNotNull(byCorrelationId);
		assertTrue(!byCorrelationId.isEmpty());
		assertEquals(byCorrelationId.toString(), 1, byCorrelationId.size());
		
		Workflow es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		Task t = es.getTasks().get(0);
		assertEquals(Status.SCHEDULED, t.getStatus());

		// PAUSE
		provider.pauseWorkflow(wfid);
		
		// The workflow is paused but the scheduled task should be pollable
		
		Task task = ess.poll("junit_task_1", "task1.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task1.junit.worker"));			
		assertEquals(t.getTaskId(), task.getTaskId());

		String param1 = (String) task.getInputData().get("p1");
		String param2 = (String) task.getInputData().get("p2");
		
		assertNotNull(param1);
		assertNotNull(param2);
		assertEquals("p1 value", param1);
		assertEquals("p2 value", param2);
		
		String task1Op = "task1.output->" + param1 + "." + param2;
		task.getOutputData().put("op", task1Op);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);

		// This decide should not schedule the next task
		//ds.decideNow(wfid, task);
		
		// If we get the full workflow here then, last task should be completed and the rest (including PAUSE task) should be scheduled
		es = ess.getExecutionStatus(wfid, true);
		es.getTasks().forEach(wfTask -> {
			if(wfTask.getTaskId().equals(t.getTaskId())){
				assertEquals(wfTask.getStatus(), Status.COMPLETED);
			}
		});

		// This should return null as workflow is paused
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNull("Found: " + task, task);

		// Even if decide is run again the next task will not be scheduled as the workflow is still paused--
		ds.decide(wfid, provider);
		
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertTrue(task == null);
		
		// RESUME
		provider.resumeWorkflow(wfid);

		// Now polling should get the second task
		task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			

		
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		
		Task byRefName = ess.getPendingTaskForWorkflow("t2", wfid);
		assertNotNull(byRefName);
		assertEquals(task.getTaskId(), byRefName.getTaskId());
		
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfid, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());

	}
	
	private static final String WF_WITH_SUB_WF = "WorkflowWithSubWorkflow";
	
	@Test
	public void testSubWorkflow() throws Exception {

		createSubWorkflow();
		WorkflowDef found = ms.getWorkflowDef(WF_WITH_SUB_WF, 1);
		assertNotNull(found);
		Map<String, Object> input = new HashMap<>();
		input.put("param1", "param 1 value");
		input.put("param3", "param 2 value");
		input.put("wfName", LINEAR_WORKFLOW_T1_T2);
		String wfId = provider.startWorkflow(WF_WITH_SUB_WF, 1, "test", input);
		assertNotNull(wfId);
		
		Workflow es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		
		Task task = ess.poll("junit_task_5", "test");
		assertNotNull(task);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		task = es.getTasks().stream().filter(t -> t.getTaskType().equals(Type.SUB_WORKFLOW.name().toString())).findAny().get();
		assertNotNull(task);
		assertNotNull(task.getOutputData());
		assertNotNull("Input: " + task.getInputData().toString(), task.getInputData().get("subWorkflowId"));
		String subWorkflowId = task.getInputData().get("subWorkflowId").toString();
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		assertEquals(wfId, es.getParentWorkflowId());
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = ess.poll("junit_task_1", "test");
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		task = ess.poll("junit_task_2", "test");
		String uuid = UUID.randomUUID().toString();
		task.getOutputData().put("uuid", uuid);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		assertNotNull(es.getOutput());
		assertTrue(es.getOutput().containsKey("o1"));
		assertTrue(es.getOutput().containsKey("o2"));
		assertEquals("sub workflow input param1", es.getOutput().get("o1"));
		assertEquals(uuid, es.getOutput().get("o2"));
		
		es = ess.getExecutionStatus(wfId, true);
		assertEquals(WorkflowStatus.COMPLETED, es.getStatus());

	}
	
	@Test
	public void testSubWorkflowFailure() throws Exception {

		TaskDef taskDef = ms.getTaskDef("junit_task_1");
		assertNotNull(taskDef);
		taskDef.setRetryCount(0);
		taskDef.setTimeoutSeconds(2);
		ms.updateTaskDef(taskDef);
		
		
		createSubWorkflow();
		WorkflowDef found = ms.getWorkflowDef(WF_WITH_SUB_WF, 1);
		assertNotNull(found);
		
		Map<String, Object> input = new HashMap<>();
		input.put("param1", "param 1 value");
		input.put("param3", "param 2 value");
		input.put("wfName", LINEAR_WORKFLOW_T1_T2);
		String wfId = provider.startWorkflow(WF_WITH_SUB_WF, 1, "test", input);
		assertNotNull(wfId);
		
		Workflow es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		
		Task task = ess.poll("junit_task_5", "test");
		assertNotNull(task);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		task = es.getTasks().stream().filter(t -> t.getTaskType().equals(Type.SUB_WORKFLOW.name().toString())).findAny().get();
		assertNotNull(task);
		assertNotNull(task.getOutputData());
		assertNotNull(task.getOutputData().get("subWorkflowId"));
		String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		assertEquals(wfId, es.getParentWorkflowId());
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		task = ess.poll("junit_task_1", "test");
		assertNotNull(task);
		task.setStatus(Status.FAILED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertNotNull(es);
		assertEquals(WorkflowStatus.FAILED, es.getStatus());
		
		es = ess.getExecutionStatus(wfId, true);
		assertEquals(WorkflowStatus.FAILED, es.getStatus());
		
		taskDef.setTimeoutSeconds(0);
		taskDef.setRetryCount(RETRY_COUNT);
		ms.updateTaskDef(taskDef);

	}
	
	@Test
	public void testSubWorkflowFailureInverse() throws Exception {

		TaskDef taskDef = ms.getTaskDef("junit_task_1");
		assertNotNull(taskDef);
		taskDef.setRetryCount(0);
		taskDef.setTimeoutSeconds(2);
		ms.updateTaskDef(taskDef);
		
		
		createSubWorkflow();
		
		WorkflowDef found = ms.getWorkflowDef(WF_WITH_SUB_WF, 1);
		assertNotNull(found);
		Map<String, Object> input = new HashMap<>();
		input.put("param1", "param 1 value");
		input.put("param3", "param 2 value");
		input.put("wfName", LINEAR_WORKFLOW_T1_T2);
		String wfId = provider.startWorkflow(WF_WITH_SUB_WF, 1, "test", input);
		assertNotNull(wfId);
		
		Workflow es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		
		Task task = ess.poll("junit_task_5", "test");
		assertNotNull(task);
		task.setStatus(Status.COMPLETED);
		ess.updateTask(task);
		
		es = ess.getExecutionStatus(wfId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		task = es.getTasks().stream().filter(t -> t.getTaskType().equals(Type.SUB_WORKFLOW.name().toString())).findAny().get();
		assertNotNull(task);
		assertNotNull(task.getOutputData());
		assertNotNull(task.getOutputData().get("subWorkflowId"));
		String subWorkflowId = task.getOutputData().get("subWorkflowId").toString();
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertNotNull(es);
		assertNotNull(es.getTasks());
		assertEquals(wfId, es.getParentWorkflowId());
		assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		
		provider.terminateWorkflow(wfId, "fail");
		es = ess.getExecutionStatus(wfId, true);
		assertEquals(WorkflowStatus.TERMINATED, es.getStatus());
		
		es = ess.getExecutionStatus(subWorkflowId, true);
		assertEquals(WorkflowStatus.TERMINATED, es.getStatus());

	}
	
	private void createSubWorkflow() throws Exception {
		
		
		
		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_5");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "${workflow.input.param1}");
		ip1.put("p2", "${workflow.input.param2}");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("a1");
		
		WorkflowTask wft2 = new WorkflowTask();
		wft2.setName("subWorkflowTask");
		wft2.setType(Type.SUB_WORKFLOW.name());
		SubWorkflowParams swp = new SubWorkflowParams();
		swp.setName(LINEAR_WORKFLOW_T1_T2);
		wft2.setSubWorkflowParam(swp);
		Map<String, Object> ip2 = new HashMap<>();
		ip2.put("test", "test value");
		ip2.put("param1", "sub workflow input param1");
		wft2.setInputParameters(ip2);
		wft2.setTaskReferenceName("a2");
				
		WorkflowDef main = new WorkflowDef();
		main.setSchemaVersion(2);
		main.setInputParameters(Arrays.asList("param1", "param2"));
		main.setName(WF_WITH_SUB_WF);
		main.getTasks().addAll(Arrays.asList(wft1, wft2));
		
		ms.updateWorkflowDef(Arrays.asList(main));
	
	}

	private void verify(String inputParam1, String wfid, String task1Op, boolean fail) throws Exception {
		Task task = ess.poll("junit_task_2", "task2.junit.worker");
		assertNotNull(task);
		assertTrue(ess.ackTaskRecieved(task.getTaskId(), "task2.junit.worker"));			
		
		String task2Input = (String) task.getInputData().get("tp2");
		assertNotNull(task2Input);
		assertEquals(task1Op, task2Input);
		task2Input = (String) task.getInputData().get("tp1");
		assertNotNull(task2Input);
		assertEquals(inputParam1, task2Input);
		if(fail){
			task.setStatus(Status.FAILED);
			task.setReasonForIncompletion("failure...0");
		}else{
			task.setStatus(Status.COMPLETED);	
		}
		
		ess.updateTask(task);
		
		Workflow es = ess.getExecutionStatus(wfid, false);
		assertNotNull(es);
		if(fail){
			assertEquals(WorkflowStatus.RUNNING, es.getStatus());
		}else{
			assertEquals(WorkflowStatus.COMPLETED, es.getStatus());
		}
	}
	
	@Before
	public void flushAllTaskQueues(){
		queue.queuesDetail().keySet().stream().forEach(queueName -> {
			queue.flush(queueName);
		});
		
		if(taskDefs == null){
			return;
		}
		for(TaskDef td: taskDefs){
			queue.flush(td.getName());
		}
	}
}
