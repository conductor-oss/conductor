/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.client.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

import com.netflix.conductor.client.http.MetadataClient;
import com.netflix.conductor.client.http.WorkflowClient;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowTestRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractWorkflowTests {

    protected static ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    protected static TypeReference<Map<String, List<WorkflowTestRequest.TaskMock>>> mockType =
            new TypeReference<Map<String, List<WorkflowTestRequest.TaskMock>>>() {};

    protected MetadataClient metadataClient;

    protected WorkflowClient workflowClient;

    @BeforeAll
    public void setup() {

        String baseURL = "http://localhost:8080/api/";
        metadataClient = new MetadataClient();
        metadataClient.setRootURI(baseURL);

        workflowClient = new WorkflowClient();
        workflowClient.setRootURI(baseURL);
    }

    protected WorkflowTestRequest getWorkflowTestRequest(WorkflowDef def) throws IOException {
        WorkflowTestRequest testRequest = new WorkflowTestRequest();
        testRequest.setInput(new HashMap<>());
        testRequest.setName(def.getName());
        testRequest.setVersion(def.getVersion());
        testRequest.setWorkflowDef(def);

        Map<String, List<WorkflowTestRequest.TaskMock>> taskRefToMockOutput = new HashMap<>();
        for (WorkflowTask task : def.collectTasks()) {
            List<WorkflowTestRequest.TaskMock> taskRuns = new LinkedList<>();
            WorkflowTestRequest.TaskMock mock = new WorkflowTestRequest.TaskMock();
            mock.setStatus(TaskResult.Status.COMPLETED);
            Map<String, Object> output = new HashMap<>();

            output.put("response", Map.of());
            mock.setOutput(output);
            taskRuns.add(mock);
            taskRefToMockOutput.put(task.getTaskReferenceName(), taskRuns);

            if (task.getType().equals(TaskType.SUB_WORKFLOW.name())) {
                Object inlineSubWorkflowDefObj = task.getSubWorkflowParam().getWorkflowDefinition();
                if (inlineSubWorkflowDefObj != null) {
                    // If not null, it represents WorkflowDef object
                    WorkflowDef inlineSubWorkflowDef = (WorkflowDef) inlineSubWorkflowDefObj;
                    WorkflowTestRequest subWorkflowTestRequest =
                            getWorkflowTestRequest(inlineSubWorkflowDef);
                    testRequest
                            .getSubWorkflowTestRequest()
                            .put(task.getTaskReferenceName(), subWorkflowTestRequest);
                } else {
                    // Inline definition is null
                    String subWorkflowName = task.getSubWorkflowParam().getName();
                    // Load up the sub workflow from the JSON
                    WorkflowDef subWorkflowDef =
                            getWorkflowDef("/workflows/" + subWorkflowName + ".json");
                    assertNotNull(subWorkflowDef);
                    WorkflowTestRequest subWorkflowTestRequest =
                            getWorkflowTestRequest(subWorkflowDef);
                    testRequest
                            .getSubWorkflowTestRequest()
                            .put(task.getTaskReferenceName(), subWorkflowTestRequest);
                }
            }
        }
        testRequest.setTaskRefToMockOutput(taskRefToMockOutput);
        return testRequest;
    }

    protected WorkflowDef getWorkflowDef(String path) throws IOException {
        InputStream inputStream = AbstractWorkflowTests.class.getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("No file found at " + path);
        }
        return objectMapper.readValue(new InputStreamReader(inputStream), WorkflowDef.class);
    }

    protected Workflow getWorkflow(String path) throws IOException {
        InputStream inputStream = AbstractWorkflowTests.class.getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("No file found at " + path);
        }
        return objectMapper.readValue(new InputStreamReader(inputStream), Workflow.class);
    }

    protected Map<String, List<WorkflowTestRequest.TaskMock>> getTestInputs(String path)
            throws IOException {
        InputStream inputStream = AbstractWorkflowTests.class.getResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("No file found at " + path);
        }
        return objectMapper.readValue(new InputStreamReader(inputStream), mockType);
    }
}
