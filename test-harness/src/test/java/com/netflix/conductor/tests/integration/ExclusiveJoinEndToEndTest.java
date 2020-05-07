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

package com.netflix.conductor.tests.integration;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.netflix.conductor.bootstrap.BootstrapModule;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.elasticsearch.ElasticSearchConfiguration;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;
import com.netflix.conductor.jetty.server.JettyServer;
import com.netflix.conductor.tests.integration.model.TaskWrapper;
import com.netflix.conductor.tests.utils.JsonUtils;
import com.netflix.conductor.tests.utils.TestEnvironment;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@Ignore("This is taken care in the spock test, to de deleted once verified")
public class ExclusiveJoinEndToEndTest {

	private static TaskClient taskClient;

	private static WorkflowClient workflowClient;

	private static MetadataClient metadataClient;

	private static EmbeddedElasticSearch search;

	private static final int SERVER_PORT = 8093;

	private static String CONDUCTOR_WORKFLOW_DEF_NAME = "ExclusiveJoinTestWorkflow";

	private static Map<String, Object> workflowInput = new HashMap<>();

	private static Map<String, Object> taskOutput = new HashMap<>();

	@BeforeClass
	public static void setUp() throws Exception {
		TestEnvironment.setup();
        System.setProperty(ElasticSearchConfiguration.EMBEDDED_PORT_PROPERTY_NAME, "9205");
        System.setProperty(ElasticSearchConfiguration.ELASTIC_SEARCH_URL_PROPERTY_NAME, "localhost:9305");
		System.setProperty(Configuration.EXECUTION_LOCK_ENABLED_PROPERTY_NAME, "false");

		Injector bootInjector = Guice.createInjector(new BootstrapModule());
		Injector serverInjector = Guice.createInjector(bootInjector.getInstance(ModulesProvider.class).get());

		search = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get().get();
		search.start();

		JettyServer server = new JettyServer(SERVER_PORT, false);
		server.start();
		String apiRoot = String.format("http://localhost:%d/api/", SERVER_PORT);
		taskClient = new TaskClient();
		taskClient.setRootURI(apiRoot);
		workflowClient = new WorkflowClient();
		workflowClient.setRootURI(apiRoot);
		metadataClient = new MetadataClient();
		metadataClient.setRootURI(apiRoot);
	}

	@Before
	public void registerWorkflows() throws Exception {
		registerWorkflowDefinitions();
	}

	@Test
	public void testDecision1Default() {
		workflowInput.put("decision_1", "null");

		StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest().withName(CONDUCTOR_WORKFLOW_DEF_NAME)
				.withCorrelationId("").withInput(workflowInput).withVersion(1);
		String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest);

		String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId();
		taskOutput.put("taskReferenceName", "task1");
		TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true);
		String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin").getOutputData().get("taskReferenceName")
				.toString();

		assertEquals("task1", taskReferenceName);
		assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
	}

	@Test
	public void testDecision1TrueAndDecision2Default() {
		workflowInput.put("decision_1", "true");
		workflowInput.put("decision_2", "null");

		StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest().withName(CONDUCTOR_WORKFLOW_DEF_NAME)
				.withCorrelationId("").withInput(workflowInput).withVersion(1);
		String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest);

		String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId();
		taskOutput.put("taskReferenceName", "task1");
		TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task2").getTaskId();
		taskOutput.put("taskReferenceName", "task2");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true);
		String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin").getOutputData().get("taskReferenceName")
				.toString();

		assertEquals("task2", taskReferenceName);
		assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
	}

	@Test
	public void testDecision1TrueAndDecision2True() {
		workflowInput.put("decision_1", "true");
		workflowInput.put("decision_2", "true");

		StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest().withName(CONDUCTOR_WORKFLOW_DEF_NAME)
				.withCorrelationId("").withInput(workflowInput).withVersion(1);
		String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest);

		String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId();
		taskOutput.put("taskReferenceName", "task1");
		TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task2").getTaskId();
		taskOutput.put("taskReferenceName", "task2");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task3").getTaskId();
		taskOutput.put("taskReferenceName", "task3");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true);
		String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin").getOutputData().get("taskReferenceName")
				.toString();

		assertEquals("task3", taskReferenceName);
		assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
	}

	@Test
	public void testDecision1FalseAndDecision3Default() {
		workflowInput.put("decision_1", "false");
		workflowInput.put("decision_3", "null");

		StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest().withName(CONDUCTOR_WORKFLOW_DEF_NAME)
				.withCorrelationId("").withInput(workflowInput).withVersion(1);
		String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest);

		String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId();
		taskOutput.put("taskReferenceName", "task1");
		TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task4").getTaskId();
		taskOutput.put("taskReferenceName", "task4");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true);
		String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin").getOutputData().get("taskReferenceName")
				.toString();

		assertEquals("task4", taskReferenceName);
		assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
	}

	@Test
	public void testDecision1FalseAndDecision3True() {
		workflowInput.put("decision_1", "false");
		workflowInput.put("decision_3", "true");

		StartWorkflowRequest startWorkflowRequest = new StartWorkflowRequest().withName(CONDUCTOR_WORKFLOW_DEF_NAME)
				.withCorrelationId("").withInput(workflowInput).withVersion(1);
		String wfInstanceId = workflowClient.startWorkflow(startWorkflowRequest);

		String taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task1").getTaskId();
		taskOutput.put("taskReferenceName", "task1");
		TaskResult taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task4").getTaskId();
		taskOutput.put("taskReferenceName", "task4");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		taskId = taskClient.getPendingTaskForWorkflow(wfInstanceId, "task5").getTaskId();
		taskOutput.put("taskReferenceName", "task5");
		taskResult = setTaskResult(wfInstanceId, taskId, TaskResult.Status.COMPLETED, taskOutput);
		taskClient.updateTask(taskResult);

		Workflow workflow = workflowClient.getWorkflow(wfInstanceId, true);
		String taskReferenceName = workflow.getTaskByRefName("exclusiveJoin").getOutputData().get("taskReferenceName")
				.toString();

		assertEquals("task5", taskReferenceName);
		assertEquals(Workflow.WorkflowStatus.COMPLETED, workflow.getStatus());
	}

	private TaskResult setTaskResult(String workflowInstanceId, String taskId, TaskResult.Status status,
			Map<String, Object> output) {
		TaskResult taskResult = new TaskResult();
		taskResult.setTaskId(taskId);
		taskResult.setWorkflowInstanceId(workflowInstanceId);
		taskResult.setStatus(status);
		taskResult.setOutputData(output);
		return taskResult;
	}

	private void registerWorkflowDefinitions() throws Exception {
		TaskWrapper taskWrapper = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinTaskDef.json", TaskWrapper.class);
		metadataClient.registerTaskDefs(taskWrapper.getTaskDefs());

		WorkflowDef conductorWorkflowDef = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinWorkflowDef.json",
				WorkflowDef.class);
		metadataClient.registerWorkflowDef(conductorWorkflowDef);
	}

	private void unRegisterWorkflowDefinitions() throws Exception {
		WorkflowDef conductorWorkflowDef = JsonUtils.fromJson("integration/scenarios/legacy/ExclusiveJoinWorkflowDef.json",
				WorkflowDef.class);
		metadataClient.unregisterWorkflowDef(conductorWorkflowDef.getName(), conductorWorkflowDef.getVersion());
	}

	@After
	public void unRegisterWorkflows() throws Exception {
		unRegisterWorkflowDefinitions();
	}

	@AfterClass
	public static void teardown() throws Exception {
		TestEnvironment.teardown();
		search.stop();
	}
}