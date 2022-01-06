/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.test.integration;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@TestPropertySource(
        properties = {"conductor.indexing.enabled=true", "conductor.elasticsearch.version=6"})
public abstract class AbstractEndToEndTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractEndToEndTest.class);

    private static final String TASK_DEFINITION_PREFIX = "task_";
    private static final String DEFAULT_DESCRIPTION = "description";
    // Represents null value deserialized from the redis in memory db
    private static final String DEFAULT_NULL_VALUE = "null";
    protected static final String DEFAULT_EMAIL_ADDRESS = "test@harness.com";

    private static final ElasticsearchContainer container =
            new ElasticsearchContainer(
                    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch-oss")
                            .withTag("6.8.12")); // this should match the client version

    private static RestClient restClient;

    // Initialization happens in a static block so the container is initialized
    // only once for all the sub-class tests in a CI environment
    // container is stopped when JVM exits
    // https://www.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers
    static {
        container.start();
        String httpHostAddress = container.getHttpHostAddress();
        System.setProperty("conductor.elasticsearch.url", "http://" + httpHostAddress);
        log.info("Initialized Elasticsearch {}", container.getContainerId());
    }

    @BeforeClass
    public static void initializeEs() {
        String httpHostAddress = container.getHttpHostAddress();
        String host = httpHostAddress.split(":")[0];
        int port = Integer.parseInt(httpHostAddress.split(":")[1]);

        RestClientBuilder restClientBuilder = RestClient.builder(new HttpHost(host, port, "http"));
        restClient = restClientBuilder.build();
    }

    @AfterClass
    public static void cleanupEs() throws Exception {
        // deletes all indices
        Response beforeResponse = restClient.performRequest(new Request("GET", "/_cat/indices"));
        Reader streamReader = new InputStreamReader(beforeResponse.getEntity().getContent());
        BufferedReader bufferedReader = new BufferedReader(streamReader);

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            String[] fields = line.split("\\s");
            String endpoint = String.format("/%s", fields[2]);

            restClient.performRequest(new Request("DELETE", endpoint));
        }

        if (restClient != null) {
            restClient.close();
        }
    }

    @Test
    public void testEphemeralWorkflowsWithStoredTasks() {
        String workflowExecutionName = "testEphemeralWorkflow";

        createAndRegisterTaskDefinitions("storedTaskDef", 5);
        WorkflowDef workflowDefinition = createWorkflowDefinition(workflowExecutionName);
        WorkflowTask workflowTask1 = createWorkflowTask("storedTaskDef1");
        WorkflowTask workflowTask2 = createWorkflowTask("storedTaskDef2");
        workflowDefinition.getTasks().addAll(Arrays.asList(workflowTask1, workflowTask2));

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);
        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);
    }

    @Test
    public void testEphemeralWorkflowsWithEphemeralTasks() {
        String workflowExecutionName = "ephemeralWorkflowWithEphemeralTasks";

        WorkflowDef workflowDefinition = createWorkflowDefinition(workflowExecutionName);
        WorkflowTask workflowTask1 = createWorkflowTask("ephemeralTask1");
        TaskDef taskDefinition1 = createTaskDefinition("ephemeralTaskDef1");
        workflowTask1.setTaskDefinition(taskDefinition1);
        WorkflowTask workflowTask2 = createWorkflowTask("ephemeralTask2");
        TaskDef taskDefinition2 = createTaskDefinition("ephemeralTaskDef2");
        workflowTask2.setTaskDefinition(taskDefinition2);
        workflowDefinition.getTasks().addAll(Arrays.asList(workflowTask1, workflowTask2));

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);

        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);

        List<WorkflowTask> ephemeralTasks = ephemeralWorkflow.getTasks();
        assertEquals(2, ephemeralTasks.size());
        for (WorkflowTask ephemeralTask : ephemeralTasks) {
            assertNotNull(ephemeralTask.getTaskDefinition());
        }
    }

    @Test
    public void testEphemeralWorkflowsWithEphemeralAndStoredTasks() {
        createAndRegisterTaskDefinitions("storedTask", 1);

        WorkflowDef workflowDefinition =
                createWorkflowDefinition("testEphemeralWorkflowsWithEphemeralAndStoredTasks");

        WorkflowTask workflowTask1 = createWorkflowTask("ephemeralTask1");
        TaskDef taskDefinition1 = createTaskDefinition("ephemeralTaskDef1");
        workflowTask1.setTaskDefinition(taskDefinition1);

        WorkflowTask workflowTask2 = createWorkflowTask("storedTask0");

        workflowDefinition.getTasks().add(workflowTask1);
        workflowDefinition.getTasks().add(workflowTask2);

        String workflowExecutionName = "ephemeralWorkflowWithEphemeralAndStoredTasks";

        String workflowId = startWorkflow(workflowExecutionName, workflowDefinition);
        assertNotNull(workflowId);

        Workflow workflow = getWorkflow(workflowId, true);
        WorkflowDef ephemeralWorkflow = workflow.getWorkflowDefinition();
        assertNotNull(ephemeralWorkflow);
        assertEquals(workflowDefinition, ephemeralWorkflow);

        TaskDef storedTaskDefinition = getTaskDefinition("storedTask0");
        List<WorkflowTask> tasks = ephemeralWorkflow.getTasks();
        assertEquals(2, tasks.size());
        assertEquals(workflowTask1, tasks.get(0));
        TaskDef currentStoredTaskDefinition = tasks.get(1).getTaskDefinition();
        assertNotNull(currentStoredTaskDefinition);
        assertEquals(storedTaskDefinition, currentStoredTaskDefinition);
    }

    @Test
    public void testEventHandler() {
        String eventName = "conductor:test_workflow:complete_task_with_event";
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName("test_complete_task_event");
        EventHandler.Action completeTaskAction = new EventHandler.Action();
        completeTaskAction.setAction(EventHandler.Action.Type.complete_task);
        completeTaskAction.setComplete_task(new EventHandler.TaskDetails());
        completeTaskAction.getComplete_task().setTaskRefName("test_task");
        completeTaskAction.getComplete_task().setWorkflowId("test_id");
        completeTaskAction.getComplete_task().setOutput(new HashMap<>());
        eventHandler.getActions().add(completeTaskAction);
        eventHandler.setEvent(eventName);
        eventHandler.setActive(true);
        registerEventHandler(eventHandler);

        Iterator<EventHandler> it = getEventHandlers(eventName, true);
        EventHandler result = it.next();
        assertFalse(it.hasNext());
        assertEquals(eventHandler.getName(), result.getName());
    }

    protected WorkflowTask createWorkflowTask(String name) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(name);
        workflowTask.setWorkflowTaskType(TaskType.SIMPLE);
        workflowTask.setTaskReferenceName(name);
        workflowTask.setDescription(getDefaultDescription(name));
        workflowTask.setDynamicTaskNameParam(DEFAULT_NULL_VALUE);
        workflowTask.setCaseValueParam(DEFAULT_NULL_VALUE);
        workflowTask.setCaseExpression(DEFAULT_NULL_VALUE);
        workflowTask.setDynamicForkTasksParam(DEFAULT_NULL_VALUE);
        workflowTask.setDynamicForkTasksInputParamName(DEFAULT_NULL_VALUE);
        workflowTask.setSink(DEFAULT_NULL_VALUE);
        workflowTask.setEvaluatorType(DEFAULT_NULL_VALUE);
        workflowTask.setExpression(DEFAULT_NULL_VALUE);
        return workflowTask;
    }

    protected TaskDef createTaskDefinition(String name) {
        TaskDef taskDefinition = new TaskDef();
        taskDefinition.setName(name);
        return taskDefinition;
    }

    protected WorkflowDef createWorkflowDefinition(String workflowName) {
        WorkflowDef workflowDefinition = new WorkflowDef();
        workflowDefinition.setName(workflowName);
        workflowDefinition.setDescription(getDefaultDescription(workflowName));
        workflowDefinition.setFailureWorkflow(DEFAULT_NULL_VALUE);
        workflowDefinition.setOwnerEmail(DEFAULT_EMAIL_ADDRESS);
        return workflowDefinition;
    }

    protected List<TaskDef> createAndRegisterTaskDefinitions(
            String prefixTaskDefinition, int numberOfTaskDefinitions) {
        String prefix = Optional.ofNullable(prefixTaskDefinition).orElse(TASK_DEFINITION_PREFIX);
        List<TaskDef> definitions = new LinkedList<>();
        for (int i = 0; i < numberOfTaskDefinitions; i++) {
            TaskDef def =
                    new TaskDef(
                            prefix + i,
                            "task " + i + DEFAULT_DESCRIPTION,
                            DEFAULT_EMAIL_ADDRESS,
                            3,
                            60,
                            60);
            def.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
            definitions.add(def);
        }
        this.registerTaskDefinitions(definitions);
        return definitions;
    }

    private String getDefaultDescription(String nameResource) {
        return nameResource + " " + DEFAULT_DESCRIPTION;
    }

    protected abstract String startWorkflow(
            String workflowExecutionName, WorkflowDef workflowDefinition);

    protected abstract Workflow getWorkflow(String workflowId, boolean includeTasks);

    protected abstract TaskDef getTaskDefinition(String taskName);

    protected abstract void registerTaskDefinitions(List<TaskDef> taskDefinitionList);

    protected abstract void registerWorkflowDefinition(WorkflowDef workflowDefinition);

    protected abstract void registerEventHandler(EventHandler eventHandler);

    protected abstract Iterator<EventHandler> getEventHandlers(String event, boolean activeOnly);
}
