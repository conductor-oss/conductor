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
package org.conductoross.conductor.webhook.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.service.webhook.TargetWorkflowCollector;
import org.conductoross.conductor.webhook.model.WebhookConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.ParametersUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IdempotencyKeyDynamicSubstitutionTest {

    private WebhookConfig webhookConfig;
    private TargetWorkflowCollector workflowCollector;
    private ParametersUtils parametersUtils;
    private ObjectMapper objectMapper;

    @Mock private Evaluator evaluator;

    @BeforeEach
    public void setUp() {
        webhookConfig = new WebhookConfig();
        objectMapper = new ObjectMapperProvider().getObjectMapper();
        parametersUtils = new ParametersUtils(objectMapper);

        Map<String, Evaluator> evaluators = new HashMap<>();
        evaluators.put("javascript", evaluator);

        workflowCollector = new TargetWorkflowCollector(evaluators, parametersUtils);
    }

    @Test
    public void testStaticIdempotencyKey() {
        // Setup
        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put("idempotencyKey", "static-key");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        webhookConfig.setWorkflowsToStart(workflowsToStart);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", "12345");

        // Verify
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo("static-key");
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }

    @Test
    public void testDynamicIdempotencyKeyWithSimpleVariable() {
        // Setup
        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put("idempotencyKey", "${workflow.input.orderId}");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        webhookConfig.setWorkflowsToStart(workflowsToStart);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", "12345");

        // Verify
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo("12345");
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }

    @Test
    public void testDynamicIdempotencyKeyWithNestedVariable() {
        // Setup
        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put("idempotencyKey", "${workflow.input.order.id}");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        webhookConfig.setWorkflowsToStart(workflowsToStart);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> orderMap = new HashMap<>();
        orderMap.put("id", "order-98765");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("order", orderMap);

        // Verify
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo("order-98765");
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }

    @Test
    public void testDynamicIdempotencyKeyWithTemplateAndMultipleVariables() {
        // Setup
        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put(
                "idempotencyKey",
                "order-${workflow.input.orderId}-customer-${workflow.input.customerId}");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        webhookConfig.setWorkflowsToStart(workflowsToStart);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", "12345");
        requestBody.put("customerId", "C789");

        // Verify
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo("order-12345-customer-C789");
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }

    @Test
    public void testDynamicIdempotencyKeyWithMissingVariable() {
        // Setup
        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put("idempotencyKey", "${workflow.input.missingField}");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        webhookConfig.setWorkflowsToStart(workflowsToStart);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", "12345");

        // Verify - now we handle missing variables gracefully
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo(StringUtils.EMPTY);
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }

    @Test
    public void testWithExpressionAndDynamicIdempotencyKey() {
        // Setup
        String expression =
                "(function(){ \n return { workflowsToStart: {\"sample_workflow\": 1, \"idempotencyKey\": \"${workflow.input.orderId}\", \"idempotencyStrategy\": \"RETURN_EXISTING\"}, receiverWorkflowNamesToVersions: { } }  \n})();";
        webhookConfig.setExpression(expression);
        webhookConfig.setEvaluatorType("javascript");

        Map<String, Object> workflowsToStart = new HashMap<>();
        workflowsToStart.put("sample_workflow", 1);
        workflowsToStart.put("idempotencyKey", "${workflow.input.orderId}");
        workflowsToStart.put("idempotencyStrategy", "RETURN_EXISTING");

        Map<String, Map<String, Object>> result = new HashMap<>();
        result.put("workflowsToStart", workflowsToStart);
        result.put("receiverWorkflowNamesToVersions", new HashMap<>());

        when(evaluator.evaluate(expression, webhookConfig)).thenReturn(result);

        // Execute
        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("orderId", "12345");

        // Verify
        String idempotencyKey = workflowCollector.popIdempotencyKey(requestBody);
        IdempotencyStrategy strategy = workflowCollector.popIdempotencyStrategy();

        assertThat(idempotencyKey).isEqualTo("12345");
        assertThat(strategy).isEqualTo(IdempotencyStrategy.RETURN_EXISTING);
    }
}
