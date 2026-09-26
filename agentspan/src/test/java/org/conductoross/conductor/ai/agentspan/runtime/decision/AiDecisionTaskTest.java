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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.evaluators.ValueParamEvaluator;
import com.netflix.conductor.core.execution.mapper.SwitchTaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.core.execution.mapper.UserDefinedTaskMapper;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.assertj.core.api.Assertions.assertThat;

class AiDecisionTaskTest {
    @Test
    void exposesStructuredDecisionToExistingSwitch() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(
                    new MockResponse()
                            .setBody(
                                    """
                {"model":"jev-1.13","answers":{"route":{"type":"choice","choice":"approve"}},
                 "usage":{"input_tokens":12,"output_tokens":2},"id":"request-1"}
                """));
            AiDecisionTask runtime = runtime(server);
            ParametersUtils parameters = new ParametersUtils(new ObjectMapper());
            WorkflowTask decision = new WorkflowTask();
            decision.setName("decision");
            decision.setTaskReferenceName("decision");
            decision.setType(runtime.getTaskType());
            decision.setInputParameters(task().getInputData());
            WorkflowTask routing = new WorkflowTask();
            routing.setName("routing");
            routing.setTaskReferenceName("routing");
            routing.setType("SWITCH");
            routing.setEvaluatorType(ValueParamEvaluator.NAME);
            routing.setExpression("selectedCase");
            routing.setInputParameters(Map.of("selectedCase", "${decision.output.selectedCase}"));
            routing.setDecisionCases(Map.of("approve", List.of(), "reject", List.of()));
            WorkflowDef definition = new WorkflowDef();
            definition.setName("decision_workflow");
            definition.setTasks(List.of(decision, routing));
            WorkflowModel workflow = new WorkflowModel();
            workflow.setWorkflowDefinition(definition);
            TaskModel task =
                    new UserDefinedTaskMapper(parameters, null)
                            .getMappedTasks(
                                    TaskMapperContext.newBuilder()
                                            .withWorkflowModel(workflow)
                                            .withWorkflowTask(decision)
                                            .withTaskDefinition(new TaskDef("decision"))
                                            .withTaskId("decision-id")
                                            .build())
                            .get(0);
            workflow.getTasks().add(task);
            runtime.start(workflow, task, null);
            assertThat(task.getStatus()).isEqualTo(TaskModel.Status.COMPLETED);
            assertThat(task.getOutputData())
                    .containsEntry("selectedCase", "approve")
                    .containsEntry("model", "jev-1.13")
                    .containsEntry("requestId", "request-1")
                    .containsKeys("usage", "latencyMs");
            assertThat((Map<?, ?>) task.getOutputData().get("answers"))
                    .isEqualTo(Map.of("route", Map.of("type", "choice", "choice", "approve")));
            assertThat(runtime.execute(new WorkflowModel(), task, null)).isFalse();
            assertThat(server.getRequestCount()).isEqualTo(1);
            assertThat(definition.getNextTask("decision")).isSameAs(routing);
            TaskModel switchTask =
                    new SwitchTaskMapper(
                                    Map.of(ValueParamEvaluator.NAME, new ValueParamEvaluator()))
                            .getMappedTasks(
                                    TaskMapperContext.newBuilder()
                                            .withWorkflowModel(workflow)
                                            .withWorkflowTask(routing)
                                            .withTaskId("switch-id")
                                            .withTaskInput(
                                                    parameters.getTaskInputV2(
                                                            routing.getInputParameters(),
                                                            workflow,
                                                            "switch-id",
                                                            null))
                                            .build())
                            .get(0);
            assertThat(switchTask.getOutputData()).containsEntry("selectedCase", "approve");
        }
    }

    @Test
    void rejectsNonChoiceBeforeInference() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            TaskModel task = task();
            task.getInputData()
                    .put(
                            "questions",
                            Map.of("ready", Map.of("type", "boolean", "instructions", "Ready?")));
            runtime(server).execute(new WorkflowModel(), task, null);
            assertThat(task.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            assertThat(server.getRequestCount()).isZero();
        }
    }

    @Test
    void rejectsInvalidAnswerAndKeepsTransientFailuresRetryable() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            AiDecisionTask runtime = runtime(server);
            server.enqueue(
                    new MockResponse()
                            .setBody(
                                    """
                {"model":"jev-1.13","answers":{"route":{"type":"choice","choice":"unknown"}}}
                """));
            TaskModel invalid = task();
            runtime.execute(new WorkflowModel(), invalid, null);
            assertThat(invalid.getStatus()).isEqualTo(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
            assertThat(invalid.getOutputData()).doesNotContainKey("selectedCase");
            server.enqueue(new MockResponse().setResponseCode(503));
            TaskModel unavailable = task();
            runtime.execute(new WorkflowModel(), unavailable, null);
            assertThat(unavailable.getStatus()).isEqualTo(TaskModel.Status.FAILED);
        }
    }

    private AiDecisionTask runtime(MockWebServer server) {
        DecisionConfiguration config = new DecisionConfiguration();
        config.setApiKey("test-key");
        config.setEndpoint(server.url("/v1/systemone").toString());
        ObjectMapper mapper = new ObjectMapper();
        return new AiDecisionTask(
                new HttpDecisionClient(
                        config,
                        mapper,
                        new OkHttpClient(),
                        java.util.List.of(new SystemOneDecisionApiAdapter())),
                mapper);
    }

    private TaskModel task() {
        TaskModel task = new TaskModel();
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.getInputData()
                .putAll(
                        Map.of(
                                "model",
                                "jev-1.13",
                                "state",
                                "Review this request",
                                "questions",
                                Map.of(
                                        "route",
                                        Map.of(
                                                "type",
                                                "choice",
                                                "instructions",
                                                "Choose a route",
                                                "choices",
                                                Map.of(
                                                        "approve",
                                                        "Accept the request",
                                                        "reject",
                                                        "Decline the request")))));
        return task;
    }
}
