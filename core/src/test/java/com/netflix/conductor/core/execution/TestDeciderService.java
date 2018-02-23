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
package com.netflix.conductor.core.execution;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.core.execution.DeciderService.DeciderOutcome;
import com.netflix.conductor.core.execution.mapper.DecisionTaskMapper;
import com.netflix.conductor.core.execution.mapper.DynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.EventTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinDynamicTaskMapper;
import com.netflix.conductor.core.execution.mapper.ForkJoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.JoinTaskMapper;
import com.netflix.conductor.core.execution.mapper.SimpleTaskMapper;
import com.netflix.conductor.core.execution.mapper.SubWorkflowTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.execution.mapper.WaitTaskMapper;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.Spectator;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * @author Viren
 *
 */
public class TestDeciderService {

	private Workflow workflow;
	
	private DeciderService deciderService;

	private ParametersUtils parametersUtils;
	
	private static Registry registry;
	
	private static ObjectMapper objectMapper = new ObjectMapper();
	
	static {
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setSerializationInclusion(Include.NON_EMPTY);
	}
	
	@BeforeClass
	public static void init() {
		registry = new DefaultRegistry();
		Spectator.globalRegistry().add(registry);
	}
	
	@Before
	public void setup(){
		MetadataDAO metadataDAO = mock(MetadataDAO.class);
		TaskDef taskDef = new TaskDef();
		WorkflowDef workflowDef = new WorkflowDef();
		when(metadataDAO.getTaskDef(any())).thenReturn(taskDef);
		when(metadataDAO.getLatest(any())).thenReturn(workflowDef);
		parametersUtils = new ParametersUtils();
		Map<String, TaskMapper> taskMappers = new HashMap<>();
		taskMappers.put("DECISION", new DecisionTaskMapper());
		taskMappers.put("DYNAMIC", new DynamicTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("FORK_JOIN", new ForkJoinTaskMapper());
		taskMappers.put("JOIN", new JoinTaskMapper());
		taskMappers.put("FORK_JOIN_DYNAMIC", new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper));
		taskMappers.put("USER_DEFINED", new UserDefinedTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("SIMPLE", new SimpleTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("SUB_WORKFLOW", new SubWorkflowTaskMapper(parametersUtils, metadataDAO));
		taskMappers.put("EVENT", new EventTaskMapper(parametersUtils));
		taskMappers.put("WAIT", new WaitTaskMapper(parametersUtils));

		deciderService = new DeciderService(metadataDAO, taskMappers);
		
		workflow = new Workflow();
		workflow.getInput().put("requestId", "request id 001");
		workflow.getInput().put("hasAwards", true);
		workflow.getInput().put("channelMapping", 5);
		Map<String, Object> name = new HashMap<>();
		name.put("name", "The Who");
		name.put("year", 1970);
		Map<String, Object> name2 = new HashMap<>();
		name2.put("name", "The Doors");
		name2.put("year", 1975);
		
		List<Object> names = new LinkedList<>();
		names.add(name);
		names.add(name2);
		
		workflow.getOutput().put("name", name);
		workflow.getOutput().put("names", names);
		workflow.getOutput().put("awards", 200);
		
		Task task = new Task();
		task.setReferenceTaskName("task2");
		task.getOutputData().put("location", "http://location");
		task.setStatus(Status.COMPLETED);
		
		Task task2 = new Task();
		task2.setReferenceTaskName("task3");
		task2.getOutputData().put("refId", "abcddef_1234_7890_aaffcc");
		task2.setStatus(Status.SCHEDULED);
		
		workflow.getTasks().add(task);
		workflow.getTasks().add(task2);
		
		
	}
	
	@Test
	public void testGetTaskInputV2() throws Exception {
		
		workflow.setSchemaVersion(2);
		Map<String, Object> ip = new HashMap<>();
		ip.put("workflowInputParam", "${workflow.input.requestId}");
		ip.put("taskOutputParam", "${task2.output.location}");
		ip.put("taskOutputParam2", "${task2.output.locationBad}");
		ip.put("taskOutputParam3", "${task3.output.location}");
		ip.put("constParam", "Some String value");
		ip.put("nullValue", null);
		ip.put("task2Status", "${task2.status}");
		ip.put("channelMap", "${workflow.input.channelMapping}");
		Map<String, Object> taskInput = parametersUtils.getTaskInput(ip , workflow, null, null);
		
		assertNotNull(taskInput);
		assertTrue(taskInput.containsKey("workflowInputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam2"));
		assertTrue(taskInput.containsKey("taskOutputParam3"));
		assertNull(taskInput.get("taskOutputParam2"));
		
		assertNotNull(taskInput.get("channelMap"));
		assertEquals(5, taskInput.get("channelMap"));
		
		assertEquals("request id 001", taskInput.get("workflowInputParam"));
		assertEquals("http://location", taskInput.get("taskOutputParam"));
		assertNull(taskInput.get("taskOutputParam3"));
		assertNull(taskInput.get("nullValue"));
		assertEquals(workflow.getTasks().get(0).getStatus().name(), taskInput.get("task2Status"));	//task2 and task3 are the tasks respectively
		System.out.println(taskInput);
		workflow.setSchemaVersion(1);
	}
	
	@Test
	public void testGetTaskInputV2Partial() throws Exception {
		System.setProperty("EC2_INSTANCE", "i-123abcdef990");
		Map<String, Object> wfi = new HashMap<>();
		Map<String, Object> wfmap = new HashMap<>();
		wfmap.put("input", workflow.getInput());
		wfmap.put("output", workflow.getOutput());
		wfi.put("workflow", wfmap);
		
		workflow.getTasks().stream().map(task -> task.getReferenceTaskName()).forEach(ref -> {
			Map<String, Object> taskInput = workflow.getTaskByRefName(ref).getInputData();
			Map<String, Object> taskOutput = workflow.getTaskByRefName(ref).getOutputData();
			Map<String, Object> io = new HashMap<>();
			io.put("input", taskInput);
			io.put("output", taskOutput);
			wfi.put(ref, io);
		});
		
		workflow.setSchemaVersion(2);
		
		Map<String, Object> ip = new HashMap<>();
		ip.put("workflowInputParam", "${workflow.input.requestId}");
		ip.put("workfowOutputParam", "${workflow.output.name}");
		ip.put("taskOutputParam", "${task2.output.location}");
		ip.put("taskOutputParam2", "${task2.output.locationBad}");
		ip.put("taskOutputParam3", "${task3.output.location}");
		ip.put("constParam", "Some String value   &");
		ip.put("partial", "${task2.output.location}/something?host=${EC2_INSTANCE}");
		ip.put("jsonPathExtracted", "${workflow.output.names[*].year}");
		ip.put("secondName", "${workflow.output.names[1].name}");
		ip.put("concatenatedName", "The Band is: ${workflow.output.names[1].name}-\t${EC2_INSTANCE}");
		
		TaskDef taskDef = new TaskDef();
		taskDef.getInputTemplate().put("opname", "${workflow.output.name}");
		List<Object> listParams = new LinkedList<>();
		List<Object> listParams2 = new LinkedList<>();
		listParams2.add("${workflow.input.requestId}-10-${EC2_INSTANCE}");
		listParams.add(listParams2);
		Map<String, Object> map = new HashMap<>();
		map.put("name", "${workflow.output.names[0].name}");
		map.put("hasAwards", "${workflow.input.hasAwards}");
		listParams.add(map);
		taskDef.getInputTemplate().put("listValues", listParams);
		
		
		Map<String, Object> taskInput = parametersUtils.getTaskInput(ip , workflow, taskDef, null);
		
		assertNotNull(taskInput);
		assertTrue(taskInput.containsKey("workflowInputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam2"));
		assertTrue(taskInput.containsKey("taskOutputParam3"));
		assertNull(taskInput.get("taskOutputParam2"));
		assertNotNull(taskInput.get("jsonPathExtracted"));
		assertTrue(taskInput.get("jsonPathExtracted") instanceof List);
		assertNotNull(taskInput.get("secondName"));
		assertTrue(taskInput.get("secondName") instanceof String);
		assertEquals("The Doors", taskInput.get("secondName"));
		assertEquals("The Band is: The Doors-\ti-123abcdef990" , taskInput.get("concatenatedName"));
		
		System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(taskInput));
		
		assertEquals("request id 001", taskInput.get("workflowInputParam"));
		assertEquals("http://location", taskInput.get("taskOutputParam"));
		assertNull(taskInput.get("taskOutputParam3"));
		assertNotNull(taskInput.get("partial"));
		assertEquals("http://location/something?host=i-123abcdef990", taskInput.get("partial"));
		workflow.setSchemaVersion(1);
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testGetTaskInput() throws Exception {
		Map<String, Object> ip = new HashMap<>();
		ip.put("workflowInputParam", "${workflow.input.requestId}");
		ip.put("taskOutputParam", "${task2.output.location}");
		List<Map<String, Object>> json = new LinkedList<>();
		Map<String, Object> m1 = new HashMap<>();
		m1.put("name", "person name");
		m1.put("city", "New York");
		m1.put("phone", 2120001234);
		m1.put("status", "${task2.output.isPersonActive}");
		
		Map<String, Object> m2 = new HashMap<>();
		m2.put("employer", "City Of New York");
		m2.put("color", "purple");
		m2.put("requestId", "${workflow.input.requestId}");
		
		json.add(m1);
		json.add(m2);
		ip.put("complexJson", json);
		
		Workflow workflow = new Workflow();
		workflow.getInput().put("requestId", "request id 001");
		Task task = new Task();
		task.setReferenceTaskName("task2");
		task.getOutputData().put("location", "http://location");
		task.getOutputData().put("isPersonActive", true);
		workflow.getTasks().add(task);
		workflow.setSchemaVersion(2);
		Map<String, Object> taskInput = parametersUtils.getTaskInput(ip , workflow, null, null);
		System.out.println(taskInput.get("complexJson"));
		assertNotNull(taskInput);
		assertTrue(taskInput.containsKey("workflowInputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam"));
		assertEquals("request id 001", taskInput.get("workflowInputParam"));
		assertEquals("http://location", taskInput.get("taskOutputParam"));
		assertNotNull(taskInput.get("complexJson"));
		assertTrue(taskInput.get("complexJson") instanceof List);
		
		List<Map<String, Object>> resolvedInput = (List<Map<String, Object>>) taskInput.get("complexJson");
		assertEquals(2, resolvedInput.size());
	}
	
	@Test
	public void testGetTaskInputV1() throws Exception {
		Map<String, Object> ip = new HashMap<>();
		ip.put("workflowInputParam", "workflow.input.requestId");
		ip.put("taskOutputParam", "task2.output.location");
		
		Workflow workflow = new Workflow();
		workflow.getInput().put("requestId", "request id 001");
		Task task = new Task();
		task.setReferenceTaskName("task2");
		task.getOutputData().put("location", "http://location");
		task.getOutputData().put("isPersonActive", true);
		workflow.getTasks().add(task);
		workflow.setSchemaVersion(1);
		Map<String, Object> taskInput = parametersUtils.getTaskInput(ip , workflow, null, null);
		
		assertNotNull(taskInput);
		assertTrue(taskInput.containsKey("workflowInputParam"));
		assertTrue(taskInput.containsKey("taskOutputParam"));
		assertEquals("request id 001", taskInput.get("workflowInputParam"));
		assertEquals("http://location", taskInput.get("taskOutputParam"));
	}
	
	@Test
	public void testGetNextTask(){
		
		WorkflowDef def = createNestedWorkflow();
		WorkflowTask firstTask = def.getTasks().get(0);
		assertNotNull(firstTask);
		assertEquals("fork1", firstTask.getTaskReferenceName());
		WorkflowTask nextAfterFirst = def.getNextTask(firstTask.getTaskReferenceName());
		assertNotNull(nextAfterFirst);
		assertEquals("join1", nextAfterFirst.getTaskReferenceName());
		
		WorkflowTask fork2 = def.getTaskByRefName("fork2");
		assertNotNull(fork2);
		assertEquals("fork2", fork2.getTaskReferenceName());
		
		WorkflowTask taskAfterFork2 = def.getNextTask("fork2");
		assertNotNull(taskAfterFork2);
		assertEquals("join2", taskAfterFork2.getTaskReferenceName());
		
		WorkflowTask t2 = def.getTaskByRefName("t2");
		assertNotNull(t2);
		assertEquals("t2", t2.getTaskReferenceName());
		
		WorkflowTask taskAfterT2 = def.getNextTask("t2");
		assertNotNull(taskAfterT2);
		assertEquals("t4", taskAfterT2.getTaskReferenceName());
		
		WorkflowTask taskAfterT3 = def.getNextTask("t3");
		assertNotNull(taskAfterT3);
		assertEquals(Type.DECISION.name(), taskAfterT3.getType());
		assertEquals("d1", taskAfterT3.getTaskReferenceName());
		
		WorkflowTask taskAfterT4 = def.getNextTask("t4");
		assertNotNull(taskAfterT4);
		assertEquals("join2", taskAfterT4.getTaskReferenceName());
		
		WorkflowTask taskAfterT6 = def.getNextTask("t6");
		assertNotNull(taskAfterT6);
		assertEquals("t9", taskAfterT6.getTaskReferenceName());
		
		WorkflowTask taskAfterJoin2 = def.getNextTask("join2");
		assertNotNull(taskAfterJoin2);
		assertEquals("join1", taskAfterJoin2.getTaskReferenceName());
		
		WorkflowTask taskAfterJoin1 = def.getNextTask("join1");
		assertNotNull(taskAfterJoin1);
		assertEquals("t5", taskAfterJoin1.getTaskReferenceName());
		
		WorkflowTask taskAfterSubWF = def.getNextTask("sw1");
		assertNotNull(taskAfterSubWF);
		assertEquals("join1", taskAfterSubWF.getTaskReferenceName());
		
		WorkflowTask taskAfterT9 = def.getNextTask("t9");
		assertNotNull(taskAfterT9);
		assertEquals("join1", taskAfterT9.getTaskReferenceName());
	}
	
	private WorkflowDef createNestedWorkflow(){
		
		WorkflowDef sub = createSubWorkflow();
		WorkflowDef def = new WorkflowDef();
		def.setName("Nested Workflow");
		def.setDescription(def.getName());
		def.setVersion(1);
		def.setInputParameters(Arrays.asList("param1", "param2"));
		
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		
		List<WorkflowTask> tasks = new ArrayList<>(10);
		
		for(int i = 0; i < 10; i++){
			WorkflowTask wft = new WorkflowTask();
			wft.setName("junit_task_" + i);
			wft.setInputParameters(ip1);
			wft.setTaskReferenceName("t" + i);
			tasks.add(wft);
		}
		
		WorkflowTask d1 = new WorkflowTask();
		d1.setType(Type.DECISION.name());
		d1.setName("Decision");
		d1.setTaskReferenceName("d1");
		d1.setDefaultCase(Arrays.asList(tasks.get(8)));
		d1.setCaseValueParam("case");
		Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
		decisionCases.put("a", Arrays.asList(tasks.get(6), tasks.get(9)));
		decisionCases.put("b", Arrays.asList(tasks.get(7)));
		d1.setDecisionCases(decisionCases);
		
		WorkflowTask subWorkflow = new WorkflowTask();
		subWorkflow.setType(Type.SUB_WORKFLOW.name());
		SubWorkflowParams sw = new SubWorkflowParams();
		sw.setName(sub.getName());
		subWorkflow.setSubWorkflowParam(sw);
		subWorkflow.setTaskReferenceName("sw1");

		WorkflowTask fork2 = new WorkflowTask();
		fork2.setType(Type.FORK_JOIN.name());
		fork2.setName("second fork");
		fork2.setTaskReferenceName("fork2");		
		fork2.getForkTasks().add(Arrays.asList(tasks.get(2), tasks.get(4)));
		fork2.getForkTasks().add(Arrays.asList(tasks.get(3), d1));
		
		WorkflowTask join2 = new WorkflowTask();
		join2.setType(Type.JOIN.name());
		join2.setTaskReferenceName("join2");
		join2.setJoinOn(Arrays.asList("t4","d1"));
		
		WorkflowTask fork1 = new WorkflowTask();
		fork1.setType(Type.FORK_JOIN.name());
		fork1.setTaskReferenceName("fork1");
		fork1.getForkTasks().add(Arrays.asList(tasks.get(1)));
		fork1.getForkTasks().add(Arrays.asList(fork2, join2));
		fork1.getForkTasks().add(Arrays.asList(subWorkflow));
		
		
		WorkflowTask join1 = new WorkflowTask();
		join1.setType(Type.JOIN.name());
		join1.setTaskReferenceName("join1");
		join1.setJoinOn(Arrays.asList("t1","fork2"));
		
		def.getTasks().add(fork1);
		def.getTasks().add(join1);
		def.getTasks().add(tasks.get(5));
		
		return def;

	}
	
	private WorkflowDef createSubWorkflow() {
		
		WorkflowTask wft1 = new WorkflowTask();
		wft1.setName("junit_task_s1");
		Map<String, Object> ip1 = new HashMap<>();
		ip1.put("p1", "workflow.input.param1");
		ip1.put("p2", "workflow.input.param2");
		wft1.setInputParameters(ip1);
		wft1.setTaskReferenceName("s1");
		
		WorkflowTask wft2 = new WorkflowTask();
		wft2.setName("junit_task_s2");
		wft2.setInputParameters(ip1);
		wft2.setTaskReferenceName("s2");
				
		WorkflowDef main = new WorkflowDef();
		main.setSchemaVersion(2);
		main.setInputParameters(Arrays.asList("param1", "param2"));
		main.setName("Linear Sub Workflow");
		main.getTasks().addAll(Arrays.asList(wft1, wft2));
		
		return main;
	
	}
	
	@Test
	public void testCaseStatement() throws Exception {

		WorkflowDef def = createConditionalWF();
		
		Workflow wf = new Workflow();
		wf.setCreateTime(new Long(0));
		wf.setWorkflowId("a");
		wf.setCorrelationId("b");
		wf.setWorkflowType(def.getName());
		wf.setVersion(def.getVersion());
		wf.setStatus(WorkflowStatus.RUNNING);
		
		DeciderOutcome outcome = deciderService.decide(wf, def);
		List<Task> scheduledTasks = outcome.tasksToBeScheduled;
		assertNotNull(scheduledTasks);
		assertEquals(2, scheduledTasks.size());
		assertEquals(Status.IN_PROGRESS, scheduledTasks.get(0).getStatus());
		assertEquals(Status.SCHEDULED, scheduledTasks.get(1).getStatus());
		
	}
	
	private WorkflowDef createConditionalWF() throws Exception {
		
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
		def2.setName("Conditional Workflow");
		def2.setDescription("Conditional Workflow");
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
		return def2;
		
	}
	
	@Test
	public void testGetTaskByRef(){
		Workflow workflow = new Workflow();
		Task t1 = new Task();
		t1.setReferenceTaskName("ref");
		t1.setSeq(0);
		t1.setStatus(Status.TIMED_OUT);
		
		Task t2 = new Task();
		t2.setReferenceTaskName("ref");
		t2.setSeq(1);
		t2.setStatus(Status.FAILED);
		
		Task t3 = new Task();
		t3.setReferenceTaskName("ref");
		t3.setSeq(2);
		t3.setStatus(Status.COMPLETED);
		
		workflow.getTasks().add(t1);
		workflow.getTasks().add(t2);
		workflow.getTasks().add(t3);
		
		Task task = workflow.getTaskByRefName("ref");
		assertNotNull(task);
		assertEquals(Status.COMPLETED, task.getStatus());
		assertEquals(t3.getSeq(), task.getSeq());
		
	}
	
	@Test
	public void testTaskTimeout() {
		
		Counter counter = registry.counter("task_timeout", "class", "WorkflowMonitor", "taskType", "test");
		assertEquals(0, counter.count());
		
		TaskDef taskType = new TaskDef();
		taskType.setName("test");
		taskType.setTimeoutPolicy(TimeoutPolicy.RETRY);
		taskType.setTimeoutSeconds(1);
		
		Task task = new Task();
		task.setTaskType(taskType.getName());
		task.setStartTime(System.currentTimeMillis() - 2_000);		//2 seconds ago!
		task.setStatus(Status.IN_PROGRESS);
		deciderService.checkForTimeout(taskType, task);
		
		//Task should be marked as timed out
		assertEquals(Status.TIMED_OUT, task.getStatus());
		assertTrue(task.getReasonForIncompletion() != null);
		assertEquals(1, counter.count());
		
		taskType.setTimeoutPolicy(TimeoutPolicy.ALERT_ONLY);
		task.setStatus(Status.IN_PROGRESS);
		task.setReasonForIncompletion(null);
		deciderService.checkForTimeout(taskType, task);
		
		//Nothing will happen
		assertEquals(Status.IN_PROGRESS, task.getStatus());
		assertNull(task.getReasonForIncompletion());
		assertEquals(2, counter.count());
		
		boolean exception = false;
		taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
		task.setStatus(Status.IN_PROGRESS);
		task.setReasonForIncompletion(null);
		
		try {
			deciderService.checkForTimeout(taskType, task);
		}catch(TerminateWorkflowException tw) {
			exception = true;
		}
		assertTrue(exception);
		assertEquals(Status.TIMED_OUT, task.getStatus());
		assertNotNull(task.getReasonForIncompletion());
		assertEquals(3, counter.count());
		
		taskType.setTimeoutPolicy(TimeoutPolicy.TIME_OUT_WF);
		task.setStatus(Status.IN_PROGRESS);
		task.setReasonForIncompletion(null);
		deciderService.checkForTimeout(null, task);	//this will be a no-op
		
		assertEquals(Status.IN_PROGRESS, task.getStatus());
		assertNull(task.getReasonForIncompletion());
		assertEquals(3, counter.count());
		
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testConcurrentTaskInputCalc() throws InterruptedException {

		TaskDef def = new TaskDef();
		ParametersUtils pu = new ParametersUtils();
		
		
		Map<String, Object> inputMap = new HashMap<>();
		inputMap.put("path", "${workflow.input.inputLocation}");
		inputMap.put("type", "${workflow.input.sourceType}");
		inputMap.put("channelMapping", "${workflow.input.channelMapping}");
		
		List<Map<String, Object>> input = new LinkedList<>();
		input.add(inputMap);
		
		Map<String, Object> body = new HashMap<>();
		body.put("input", input);
		
		def.getInputTemplate().putAll(body);
		
		ExecutorService es = Executors.newFixedThreadPool(10);
		final int[] result = new int[10];
		CountDownLatch latch = new CountDownLatch(10);
		
		for(int i = 0; i < 10; i++) {
			final int x = i;
			es.submit(() -> {
				
				try {
					
					Map<String, Object> workflowInput = new HashMap<>();
					workflowInput.put("outputLocation", "baggins://outputlocation/" + x);
					workflowInput.put("inputLocation", "baggins://inputlocation/" + x);
					workflowInput.put("sourceType", "MuxedSource");
					workflowInput.put("channelMapping", x);
					Workflow workflow = new Workflow();
					workflow.setInput(workflowInput);
	
					Map<String, Object> taskInput = pu.getTaskInputV2(new HashMap<>(), workflow, null, def);
					
					Object reqInputObj = taskInput.get("input");
					assertNotNull(reqInputObj);
					assertTrue(reqInputObj instanceof List);
					List<Map<String, Object>> reqInput = (List<Map<String, Object>>) reqInputObj;
					
					Object cmObj = reqInput.get(0).get("channelMapping");
					assertNotNull(cmObj);
					if(! (cmObj instanceof Number) ) {
						System.out.println("Not a number @ " + x + ", found: " + cmObj.getClass());
						result[x] = -1;
					} else {
						Number channelMapping = (Number)cmObj;
						result[x] = channelMapping.intValue();	
					}

					latch.countDown();
					
				}catch(Exception e) {
					e.printStackTrace();
				}
				
			});
		}
		latch.await(1, TimeUnit.MINUTES);
		if(latch.getCount() > 0) {
			fail("Executions did not complete in a minute.  Something wrong with the build server?");
		}
		es.shutdownNow();
		for(int i = 0; i < result.length; i++) {
			assertEquals(i, result[i]);
		}
		System.out.println("Done");
	
	}
	
	
	@SuppressWarnings("unchecked")
	@Test
	public void testTaskRetry() throws Exception {
		
		workflow.setSchemaVersion(2);
		
		Map<String, Object> ip = new HashMap<>();
		ip.put("workflowInputParam", "${workflow.input.requestId}");
		ip.put("taskOutputParam", "${task2.output.location}");
		ip.put("constParam", "Some String value");
		ip.put("nullValue", null);
		ip.put("task2Status", "${task2.status}");
		ip.put("null", null);
		ip.put("task_id", "${CPEWF_TASK_ID}");
		
		Map<String, Object> env = new HashMap<>();
		env.put("env_task_id", "${CPEWF_TASK_ID}");
		ip.put("env", env);
		
		Map<String, Object> taskInput = parametersUtils.getTaskInput(ip , workflow, null, "t1");
		Task task = new Task();
		task.getInputData().putAll(taskInput);
		task.setStatus(Status.FAILED);
		task.setTaskId("t1");
		
		TaskDef taskDef = new TaskDef();
		WorkflowTask workflowTask = new WorkflowTask();
		workflowTask.getInputParameters().put("task_id", "${CPEWF_TASK_ID}");
		workflowTask.getInputParameters().put("env", env);
		
		Task task2 = deciderService.retry(taskDef, workflowTask, task, workflow);
		System.out.println(task.getTaskId() + ":\n" + task.getInputData());
		System.out.println(task2.getTaskId() + ":\n" + task2.getInputData());
		
		assertEquals("t1", task.getInputData().get("task_id"));
		assertEquals("t1", ((Map<String, Object>)task.getInputData().get("env")).get("env_task_id"));
		
		assertNotSame(task.getTaskId(), task2.getTaskId());
		assertEquals(task2.getTaskId(), task2.getInputData().get("task_id"));
		assertEquals(task2.getTaskId(), ((Map<String, Object>)task2.getInputData().get("env")).get("env_task_id"));
		
	}

	@Test
	public void testFork() throws Exception {
		InputStream stream = TestDeciderOutcomes.class.getResourceAsStream("/test.json");
		Workflow workflow = objectMapper.readValue(stream, Workflow.class);
		
		InputStream defs = TestDeciderOutcomes.class.getResourceAsStream("/def.json");
		WorkflowDef def = objectMapper.readValue(defs, WorkflowDef.class);

		DeciderOutcome outcome = deciderService.decide(workflow, def);
		assertFalse(outcome.isComplete);
		assertEquals(5, outcome.tasksToBeScheduled.size());
		assertEquals(1, outcome.tasksToBeUpdated.size());
		
	}
}
