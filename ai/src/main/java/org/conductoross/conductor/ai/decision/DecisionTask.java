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
package org.conductoross.conductor.ai.decision;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.util.Timeout;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.Utils;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.sdk.workflow.executor.task.NonRetryableException;
import com.netflix.conductor.tasks.http.HttpTask;
import com.netflix.conductor.tasks.http.providers.RestTemplateProvider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Asks a decision provider structured questions and completes with its validated answers. The
 * request is sent through {@link HttpTask#httpCall}, so the task runs on the system task worker,
 * never in the decider, and failures are retried with the task definition's retry policy.
 *
 * <p>Route a workflow on the result with a regular {@code SWITCH}: either on {@code
 * answers.<question>.choice}, or on {@code selectedCase} when the {@code question} input names the
 * choice question to route on.
 */
@Component(DecisionTask.TASK_TYPE)
@Conditional(AIIntegrationEnabledCondition.class)
public class DecisionTask extends HttpTask {

    public static final String TASK_TYPE = "AI_DECISION";
    public static final String QUESTION = "question";
    public static final String SELECTED_CASE = "selectedCase";

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final DecisionConfiguration config;
    private final Map<String, DecisionApiAdapter> adapters;

    public DecisionTask(
            RestTemplateProvider restTemplateProvider,
            ObjectMapper objectMapper,
            DecisionConfiguration config,
            List<DecisionApiAdapter> adapters) {
        super(TASK_TYPE, decisionTransport(restTemplateProvider), objectMapper);
        this.config = config;
        this.adapters =
                adapters.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        DecisionApiAdapter::name, Function.identity()));
    }

    /**
     * Adapts the HTTP task's {@link RestTemplate} for decision calls.
     *
     * <ul>
     *   <li>{@link HttpTask#httpCall} turns an error status into an exception without the status
     *       code. Returning error responses instead lets this task tell a rate limit or server
     *       error, which is retried, from a rejected request, which is not.
     *   <li>Redirects are not followed: the HTTP client re-sends the {@code Authorization} header
     *       to the redirect target. The per-request config replaces the client default, so it
     *       carries the same timeouts {@code DefaultRestTemplateProvider} applies.
     * </ul>
     */
    private static RestTemplateProvider decisionTransport(RestTemplateProvider delegate) {
        return input -> {
            RestTemplate restTemplate = delegate.getRestTemplate(input);
            if (!(restTemplate.getRequestFactory()
                    instanceof HttpComponentsClientHttpRequestFactory factory)) {
                throw new NonRetryableException(
                        "Decision: the HTTP task transport must use Apache HttpClient");
            }
            RequestConfig noRedirects =
                    RequestConfig.custom()
                            .setRedirectsEnabled(false)
                            .setConnectTimeout(Timeout.ofMilliseconds(input.getConnectionTimeOut()))
                            .setResponseTimeout(Timeout.ofMilliseconds(input.getReadTimeOut()))
                            .build();
            factory.setHttpContextFactory(
                    (method, uri) -> {
                        HttpClientContext context = HttpClientContext.create();
                        context.setRequestConfig(noRedirects);
                        return context;
                    });
            restTemplate.setErrorHandler(
                    new DefaultResponseErrorHandler() {
                        @Override
                        public boolean hasError(ClientHttpResponse response) {
                            return false;
                        }
                    });
            return restTemplate;
        };
    }

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setWorkerId(Utils.getServerId());
        try {
            DecisionRequest request =
                    objectMapper.convertValue(task.getInputData(), DecisionRequest.class);
            DecisionValidation.request(request);
            String question = routingQuestion(task.getInputData().get(QUESTION), request);
            DecisionConfiguration.Route route = config.resolve(request.provider(), request.model());
            DecisionApiAdapter adapter = adapters.get(route.apiShape());
            DecisionValidation.requireResponse(
                    adapter != null, "Unknown decision API shape: " + route.apiShape());

            HttpTask.Input input = adapter.createRequest(request, route);
            int timeoutMs = (int) config.getTimeout().toMillis();
            input.setConnectionTimeOut(timeoutMs);
            input.setReadTimeOut(timeoutMs);

            long started = System.nanoTime();
            HttpResponse response = httpCall(input);
            if (response.statusCode < 200 || response.statusCode > 299) {
                // Provider error bodies may echo credentials, so only the status is reported.
                task.setReasonForIncompletion("Decision HTTP status " + response.statusCode);
                task.setStatus(
                        response.statusCode == 429 || response.statusCode >= 500
                                ? TaskModel.Status.FAILED
                                : TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
                return;
            }
            JsonNode data = objectMapper.valueToTree(response.body);
            DecisionResult result =
                    adapter.decode(
                            data,
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                            route.provider());
            DecisionValidation.result(request, result);

            task.getOutputData().putAll(objectMapper.convertValue(result, MAP));
            if (question != null) {
                task.addOutput(SELECTED_CASE, result.answers().get(question).choice());
            }
            task.setStatus(TaskModel.Status.COMPLETED);
        } catch (IllegalArgumentException | NonRetryableException e) {
            task.setReasonForIncompletion(e.getMessage());
            task.setStatus(TaskModel.Status.FAILED_WITH_TERMINAL_ERROR);
        } catch (Exception e) {
            // Transport errors may carry provider details; keep the reason generic.
            task.setReasonForIncompletion("Decision transport failed");
            task.setStatus(TaskModel.Status.FAILED);
        }
    }

    /** The choice question named by {@code question}, or null when the task does not route. */
    private static String routingQuestion(Object name, DecisionRequest request) {
        if (name == null || (name instanceof String s && StringUtils.isBlank(s))) {
            return null;
        }
        DecisionQuestion selected = request.questions().get(String.valueOf(name));
        if (selected == null || selected.type() != DecisionQuestion.Type.CHOICE) {
            throw new IllegalArgumentException(
                    "Decision question '" + name + "' must be a choice question");
        }
        return String.valueOf(name);
    }
}
