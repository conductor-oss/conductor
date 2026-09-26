/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.agentspan.runtime.decision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.compiler.AgentCompiler;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentStreamRegistry;
import org.conductoross.conductor.common.metadata.agent.AgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionAgentTaskTest {
    private MockWebServer server;
    private AgentStreamRegistry streamRegistry;
    private DecisionAgentTask runtime;
    private WorkflowModel workflow;
    private TaskModel task;

    @BeforeEach
    void setup() throws Exception {
        server = new MockWebServer();
        server.start();
        DecisionConfiguration properties = new DecisionConfiguration();
        properties.setApiKey("test-key");
        properties.setEndpoint(server.url("/v1/systemone").toString());
        ObjectMapper mapper = new ObjectMapper();
        streamRegistry = new AgentStreamRegistry();
        runtime =
                new DecisionAgentTask(
                        new DecisionTaskSupport(
                                new HttpDecisionClient(
                                        properties,
                                        mapper,
                                        new OkHttpClient(),
                                        java.util.List.of(new SystemOneDecisionApiAdapter())),
                                mapper),
                        streamRegistry);
        AgentConfig config =
                AgentConfig.builder()
                        .name("chooser")
                        .kind(AgentConfig.Kind.DECISION)
                        .model("jev-1.13")
                        .questions(
                                Map.of(
                                        "ready",
                                        Map.of("type", "boolean", "instructions", "Ready?")))
                        .build();
        workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new AgentCompiler().compile(config));
        task = new TaskModel();
        task.setWorkflowInstanceId("decision-agent");
        task.setTaskType("DECISION_AGENT");
        task.setReferenceTaskName("chooser_decision");
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setInputData(
                Map.of(
                        "model",
                        config.getModel(),
                        "state",
                        "Ready",
                        "questions",
                        config.getQuestions()));
    }

    @AfterEach
    void close() throws Exception {
        server.shutdown();
    }

    @Test
    void executesCompiledAgentAndPreservesStructuredOutput() {
        server.enqueue(
                new MockResponse()
                        .setBody(
                                """
                {"model":"jev-1.13","answers":{"ready":{"type":"noul","noul":0.9}},
                 "usage":{"input_tokens":12,"output_tokens":2},"id":"request-1"}
                """));
        runtime.start(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
        assertThat(task.getOutputData()).containsEntry("requestId", "request-1");
        assertThat(((Map<?, ?>) task.getOutputData().get("answers")).get("ready"))
                .isEqualTo(Map.of("type", "boolean", "probability", 0.9));
        try (var stream = streamRegistry.openStream("decision-agent", null)) {
            var event = stream.nextEvent();
            assertThat(event.getType()).isEqualTo("decision");
            assertThat(event.getResult()).isEqualTo(task.getOutputData());
        }
        assertThat(runtime.execute(workflow, task, null)).isFalse();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void rejectsStandaloneAndChatWorkflowUseBeforeInference() {
        WorkflowDef compiled = workflow.getWorkflowDefinition();
        Map<String, Object> metadata = compiled.getMetadata();
        for (Map<String, Object> invalid :
                List.<Map<String, Object>>of(
                        Map.of(),
                        Map.of("classifier", "agent", "agentDef", Map.of("kind", "chat")))) {
            compiled.setMetadata(invalid);
            task.setStatus(TaskModel.Status.SCHEDULED);
            runtime.start(workflow, task, null);
            assertThat(task.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        }
        compiled.setMetadata(metadata);
        // A task inserted into a larger workflow is not a compiled Decision agent either.
        compiled.setTasks(List.of(compiled.getTasks().get(0), compiled.getTasks().get(0)));
        task.setStatus(TaskModel.Status.SCHEDULED);
        runtime.start(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void invalidQuestionsFailBeforeInference() {
        var input = new LinkedHashMap<>(task.getInputData());
        input.put("questions", Map.of());
        task.setInputData(input);
        runtime.start(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void onlyTransientFailuresAreRetryable() {
        for (int code : List.of(429, 500, 401)) {
            server.enqueue(new MockResponse().setResponseCode(code));
            task.setStatus(TaskModel.Status.SCHEDULED);
            runtime.start(workflow, task, null);
            assertThat(task.getStatus())
                    .isEqualTo(
                            code == 401
                                    ? TaskModel.Status.FAILED_WITH_TERMINAL_ERROR
                                    : TaskModel.Status.FAILED);
        }
        server.enqueue(new MockResponse().setBody("{broken"));
        task.setStatus(TaskModel.Status.SCHEDULED);
        runtime.start(workflow, task, null);
        assertThat(task.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        assertThat(server.getRequestCount()).isEqualTo(4);
    }
}
