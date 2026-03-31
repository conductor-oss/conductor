/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.test.integration.http.functional;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

import com.netflix.conductor.ConductorTestApp;
import com.netflix.conductor.client.http.EventClient;
import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import static org.junit.Assert.assertFalse;

/**
 * Base class for functional (e2e-style) tests that run against a real Conductor server with Redis
 * persistence and Elasticsearch indexing, all managed via TestContainers.
 *
 * <p>Unlike the existing integration tests which use in-memory persistence, these tests exercise
 * the full stack with real infrastructure — matching what the e2e module tests against a Docker
 * Compose cluster.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = ConductorTestApp.class)
@TestPropertySource(locations = "classpath:application-functionaltest.properties")
public abstract class FunctionalTestBase {

    private static final Logger log = LoggerFactory.getLogger(FunctionalTestBase.class);

    protected static final String OWNER_EMAIL = "test@conductor.io";

    // Singleton containers — started once, reused across all functional test classes in the JVM.
    // TestContainers Ryuk cleans up on JVM exit.
    private static final ElasticsearchContainer esContainer =
            new ElasticsearchContainer(DockerImageName.parse("elasticsearch").withTag("7.17.11"))
                    .withExposedPorts(9200, 9300)
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("discovery.type", "single-node");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName.parse("redis:6.2-alpine"))
                    .withExposedPorts(6379);

    static {
        esContainer.start();
        redisContainer.start();
        log.info(
                "Functional test containers started — ES: {}, Redis port: {}",
                esContainer.getHttpHostAddress(),
                redisContainer.getFirstMappedPort());
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "conductor.elasticsearch.url", () -> "http://" + esContainer.getHttpHostAddress());
        registry.add(
                "conductor.redis.hosts",
                () -> "localhost:" + redisContainer.getFirstMappedPort() + ":us-east-1c");
        registry.add(
                "conductor.redis-lock.serverAddress",
                () -> String.format("redis://localhost:%s", redisContainer.getFirstMappedPort()));
    }

    @LocalServerPort protected int port;

    protected TaskClient taskClient;
    protected WorkflowClient workflowClient;
    protected MetadataClient metadataClient;
    protected EventClient eventClient;

    @Before
    public void initClients() {
        String apiRoot = String.format("http://localhost:%d/api/", port);

        taskClient = new TaskClient();
        taskClient.setRootURI(apiRoot);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(apiRoot);

        metadataClient = new MetadataClient();
        metadataClient.setRootURI(apiRoot);

        eventClient = new EventClient();
        eventClient.setRootURI(apiRoot);
    }

    // ---- Helper methods shared across functional tests ----

    protected TaskDef createTaskDef(String name) {
        TaskDef taskDef = new TaskDef(name, name + " description", OWNER_EMAIL, 3, 60, 60);
        taskDef.setTimeoutPolicy(TaskDef.TimeoutPolicy.RETRY);
        return taskDef;
    }

    protected WorkflowTask createSimpleWorkflowTask(String taskName, String refName) {
        WorkflowTask task = new WorkflowTask();
        task.setName(taskName);
        task.setTaskReferenceName(refName);
        task.setWorkflowTaskType(TaskType.SIMPLE);
        return task;
    }

    protected void pollAndCompleteTask(String taskType, Map<String, Object> output) {
        List<Task> tasks =
                taskClient.batchPollTasksByTaskType(taskType, "functional_test_worker", 1, 5000);
        assertFalse("Expected task " + taskType + " to be available for polling", tasks.isEmpty());
        Task task = tasks.get(0);
        task.setStatus(Task.Status.COMPLETED);
        if (output != null && !output.isEmpty()) {
            task.getOutputData().putAll(output);
        }
        taskClient.updateTask(new TaskResult(task));
    }
}
