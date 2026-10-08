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
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.ParametersUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy.FAIL;
import static com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy.RETURN_EXISTING;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TargetWorkflowCollectorTest {
    private WebhookConfig webhookConfig;
    @Mock private Evaluator evaluator;
    private TargetWorkflowCollector workflowCollector;
    private ParametersUtils parametersUtils;
    private ObjectMapper objectMapper;

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
    public void targetWorkflowsAreExtractedFromExpressionWhenExpressionIsPresent() {

        String expression =
                "(function(){ \n return { workflowsToStart: {\"sample_workflow_1\": 1}, receiverWorkflowNamesToVersions: { \"sample_workflow_2\": 1, \"sample_workflow_3\": 1 } }  \n})();";
        webhookConfig.setExpression(expression);
        webhookConfig.setEvaluatorType("javascript");

        when(evaluator.evaluate(expression, webhookConfig)).thenReturn(targetWorkflows());

        workflowCollector.visit(webhookConfig);

        assertThat(workflowCollector.getWorkflowsToStart()).containsKey("sample_workflow_1");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_2");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_3");
    }

    @Test()
    public void targetWorkflowsAreExtractedFromDirectlyFromFieldsWhenExpressionIsAbsent() {

        webhookConfig.setExpression(null);
        webhookConfig.setEvaluatorType(null);

        webhookConfig.setWorkflowsToStart(Map.of("sample_workflow_1_from_webhook_config_field", 1));
        webhookConfig.setReceiverWorkflowNamesToVersions(
                Map.of(
                        "sample_workflow_2_from_webhook_config_field",
                        1,
                        "sample_workflow_3_from_webhook_config_field",
                        1));

        workflowCollector.visit(webhookConfig);

        assertThat(workflowCollector.getWorkflowsToStart())
                .containsKey("sample_workflow_1_from_webhook_config_field");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_2_from_webhook_config_field");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_3_from_webhook_config_field");
    }

    @Test()
    public void
            targetWorkflowsAreExtractedPreferentiallyFromExpressionWhenBothExpressionAndFieldsArePresent() {

        String expression =
                "(function(){ \n return { workflowsToStart: {\"sample_workflow_1\": 1}, receiverWorkflowNamesToVersions: { \"sample_workflow_2\": 1, \"sample_workflow_3\": 1 } }  \n})();";
        webhookConfig.setExpression(expression);
        webhookConfig.setEvaluatorType("javascript");

        when(evaluator.evaluate(expression, webhookConfig)).thenReturn(targetWorkflows());

        webhookConfig.setWorkflowsToStart(Map.of("sample_workflow_1_from_webhook_config_field", 1));
        webhookConfig.setReceiverWorkflowNamesToVersions(
                Map.of(
                        "sample_workflow_2_from_webhook_config_field",
                        1,
                        "sample_workflow_3_from_webhook_config_field",
                        1));

        workflowCollector.visit(webhookConfig);

        assertThat(workflowCollector.getWorkflowsToStart()).containsKey("sample_workflow_1");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_2");
        assertThat(workflowCollector.getWorkflowsToCompleteWebhooks())
                .containsKey("sample_workflow_3");
    }

    @Test()
    public void shouldPopIdempotencyKeyAndStrategy() {

        Map<String, Object> workflowsToStart =
                new HashMap<>(
                        Map.of(
                                "sample_workflow_1_from_webhook_config_field",
                                1,
                                "idempotencyKey",
                                "sample_idempotency_key",
                                "idempotencyStrategy",
                                "RETURN_EXISTING"));
        webhookConfig.setWorkflowsToStart(workflowsToStart);
        Map<String, Integer> workflowsToComplete =
                new HashMap<>(
                        Map.of(
                                "sample_workflow_2_from_webhook_config_field",
                                1,
                                "sample_workflow_3_from_webhook_config_field",
                                1));
        webhookConfig.setReceiverWorkflowNamesToVersions(workflowsToComplete);

        workflowCollector.visit(webhookConfig);

        Map<String, Object> requestBody = new HashMap<>();
        assertThat(workflowCollector.popIdempotencyKey(requestBody))
                .isEqualTo("sample_idempotency_key");
        assertThat(workflowCollector.popIdempotencyKey(requestBody))
                .isEqualTo(StringUtils.EMPTY); // default

        assertThat(workflowCollector.popIdempotencyStrategy()).isEqualTo(RETURN_EXISTING);
        assertThat(workflowCollector.popIdempotencyStrategy()).isEqualTo(FAIL); // default
    }

    private Object targetWorkflows() {
        return Map.of(
                "workflowsToStart", Map.of("sample_workflow_1", 1),
                "receiverWorkflowNamesToVersions",
                        Map.of("sample_workflow_2", 1, "sample_workflow_3", 1));
    }
}
