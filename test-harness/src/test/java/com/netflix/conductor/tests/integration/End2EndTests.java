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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskDef.TimeoutPolicy;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask.Type;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.Workflow.WorkflowStatus;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.server.ConductorConfig;
import com.netflix.conductor.server.ConductorServer;

/**
 * @author Viren
 *
 */
public class End2EndTests {

	static {
		System.setProperty("EC2_REGION", "us-east-1");
		System.setProperty("EC2_AVAILABILITY_ZONE", "us-east-1c");
		System.setProperty("workflow.elasticsearch.url", "localhost:9300");
		System.setProperty("workflow.elasticsearch.index.name", "conductor");
		System.setProperty("db", "memory");
	}
	
	private static TaskClient tc;
	
	private static WorkflowClient wc;
	
	
	@BeforeClass
	public static void setup() throws Exception {
		ConductorServer server = new ConductorServer(new ConductorConfig());
		server.start(8080, false);
		
		tc = new TaskClient();
		tc.setRootURI("http://localhost:8080/api/");
		
		wc = new WorkflowClient();
		wc.setRootURI("http://localhost:8080/api/");
	}
	
	@Test
	public void testAll() throws Exception {
		assertNotNull(tc);
		List<TaskDef> defs = new LinkedList<>();
		for(int i = 0; i < 5; i++) {
			TaskDef def = new TaskDef("t" + i, "task " + i);
			def.setTimeoutPolicy(TimeoutPolicy.RETRY);
			defs.add(def);
		}
		tc.registerTaskDefs(defs);
		List<TaskDef> found = tc.getTaskDef();
		assertNotNull(found);
		assertEquals(defs.size(), found.size());
		
		WorkflowDef def = new WorkflowDef();
		def.setName("test");
		WorkflowTask t0 = new WorkflowTask();
		t0.setName("t0");
		t0.setType(Type.SIMPLE);
		t0.setTaskReferenceName("t0");
		
		WorkflowTask t1 = new WorkflowTask();
		t1.setName("t1");
		t1.setType(Type.SIMPLE);
		t1.setTaskReferenceName("t1");
		
		
		def.getTasks().add(t0);
		def.getTasks().add(t1);
		
		wc.registerWorkflow(def);
		WorkflowDef foundd = wc.getWorkflowDef(def.getName(), null);
		assertNotNull(foundd);
		assertEquals(def.getName(), foundd.getName());
		assertEquals(def.getVersion(), foundd.getVersion());
		
		String correlationId = "test_corr_id";
		String workflowId = wc.startWorkflow(def.getName(), null, correlationId, new HashMap<>());
		assertNotNull(workflowId);
		System.out.println(workflowId);
		
		Workflow wf = wc.getWorkflow(workflowId, false);
		assertEquals(0, wf.getTasks().size());
		assertEquals(workflowId, wf.getWorkflowId());
		
		wf = wc.getWorkflow(workflowId, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		assertEquals(1, wf.getTasks().size());
		assertEquals(t0.getTaskReferenceName(), wf.getTasks().get(0).getReferenceTaskName());
		assertEquals(workflowId, wf.getWorkflowId());
		
		List<String> runningIds = wc.getRunningWorkflow(def.getName(), def.getVersion());
		assertNotNull(runningIds);
		assertEquals(1, runningIds.size());
		assertEquals(workflowId, runningIds.get(0));
		
		List<Workflow> byCorrId = wc.getWorkflows(def.getName(), correlationId, true, true);
		assertNotNull(byCorrId);
		assertTrue(!byCorrId.isEmpty());
		assertEquals(workflowId, byCorrId.get(0).getWorkflowId());
		assertEquals(correlationId, byCorrId.get(0).getCorrelationId());
		
		List<Task> polled = tc.poll("non existing task", "test", 1, 100);
		assertNotNull(polled);
		assertEquals(0, polled.size());
		
		polled = tc.poll(t0.getName(), "test", 1, 100);
		assertNotNull(polled);
		assertEquals(1, polled.size());
		assertEquals(t0.getName(), polled.get(0).getTaskDefName());
		Task task = polled.get(0);
		
		Boolean acked = tc.ack(task.getTaskId(), "test");
		assertNotNull(acked);
		assertTrue(acked.booleanValue());
		
		task.getOutputData().put("key1", "value1");
		task.setStatus(Status.COMPLETED);
		tc.updateTask(task);
		
		polled = tc.poll(t0.getName(), "test", 1, 100);
		assertNotNull(polled);
		assertTrue(polled.isEmpty());
		
		wf = wc.getWorkflow(workflowId, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		assertEquals(2, wf.getTasks().size());
		assertEquals(t0.getTaskReferenceName(), wf.getTasks().get(0).getReferenceTaskName());
		assertEquals(t1.getTaskReferenceName(), wf.getTasks().get(1).getReferenceTaskName());
		assertEquals(Task.Status.COMPLETED, wf.getTasks().get(0).getStatus());
		assertEquals(Task.Status.SCHEDULED, wf.getTasks().get(1).getStatus());
		
		Task taskById = tc.get(task.getTaskId());
		assertNotNull(taskById);
		assertEquals(task.getTaskId(), taskById.getTaskId());
		
		
		List<Task> getTasks = tc.getTasks(t0.getName(), null, 1);
		assertNotNull(getTasks);
		assertEquals(0, getTasks.size());		//getTasks only gives pending tasks
		
		
		getTasks = tc.getTasks(t1.getName(), null, 1);
		assertNotNull(getTasks);
		assertEquals(1, getTasks.size());
		
		
		Task pending = tc.getPendingTaskForWorkflow(workflowId, t1.getTaskReferenceName());
		assertNotNull(pending);
		assertEquals(t1.getTaskReferenceName(), pending.getReferenceTaskName());
		assertEquals(workflowId, pending.getWorkflowInstanceId());
		
		Thread.sleep(1000);
		SearchResult<WorkflowSummary> searchResult = wc.search("workflowType='" + def.getName() + "'");
		assertNotNull(searchResult);
		assertEquals(1, searchResult.getTotalHits());
		
		wc.terminateWorkflow(workflowId, "terminate reason");
		wf = wc.getWorkflow(workflowId, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.TERMINATED, wf.getStatus());
		
		wc.restart(workflowId);
		wf = wc.getWorkflow(workflowId, true);
		assertNotNull(wf);
		assertEquals(WorkflowStatus.RUNNING, wf.getStatus());
		assertEquals(1, wf.getTasks().size());
		
		Thread.sleep(100000);
		
	}
	
}
