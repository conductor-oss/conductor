package org.conductoross.conductor.service.webhook;

import com.netflix.conductor.common.metadata.workflow.IdempotencyStrategy;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.ParametersUtils;

import org.conductoross.conductor.webhook.model.WebhookConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TargetWorkflowCollector implements WebhookConfig.WebhookConfigVisitor {

    private final Map<String, Evaluator> evaluators;
    private final ParametersUtils parametersUtils;
    @Getter
    private Map<String, Integer> workflowsToCompleteWebhooks;
    @Getter
    private Map<String, Object> workflowsToStart;

    public TargetWorkflowCollector(Map<String, Evaluator> evaluators, ParametersUtils parametersUtils) {
        this.evaluators = evaluators;
        this.parametersUtils = parametersUtils;
    }

    @Override
    public void visit(WebhookConfig webhookConfig) {
        if (StringUtils.isNotEmpty(webhookConfig.getExpression())) {
            Map<String, Map<String, Object>> result = evaluateExpression(webhookConfig);
            Map<String, Integer> workflowsToComplete = result.get("receiverWorkflowNamesToVersions").entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Integer.valueOf(e.getValue().toString())));
            this.workflowsToCompleteWebhooks = workflowsToComplete;
            this.workflowsToStart = result.get("workflowsToStart");
        } else {
            this.workflowsToCompleteWebhooks = webhookConfig.getReceiverWorkflowNamesToVersions();
            this.workflowsToStart = webhookConfig.getWorkflowsToStart();
        }

    }

    private Map<String, Map<String, Object>> evaluateExpression(WebhookConfig webhookConfig) {
        String evaluatorType = webhookConfig.getEvaluatorType() != null ? webhookConfig.getEvaluatorType() : "javascript";
        String expression = webhookConfig.getExpression();
        Evaluator evaluator = evaluators.get(evaluatorType);
        return (Map<String, Map<String, Object>>) evaluator.evaluate(expression, webhookConfig);
    }

    /**
     * Method has side effect
     * It removes the key from the map after retrieving it.
     * <p>
     * This is an improvement over the previous implementation where a getter had side effects
     */
    public String popIdempotencyKey(Map<String, Object> requestBody) {
        String key = "idempotencyKey";
        if (workflowsToStart == null) {
            return StringUtils.EMPTY;
        }
        if (workflowsToStart.containsKey(key)) {
            String idempotencyKeyPattern = (String) workflowsToStart.get(key);
            workflowsToStart.remove(key);

            // Skip replacement if there are no variables to replace
            if (!idempotencyKeyPattern.contains("${")) {
                return idempotencyKeyPattern;
            }

            // Create a context map with the workflow input
            Map<String, Object> contextMap = new HashMap<>();
            Map<String, Object> workflowParams = new HashMap<>();
            workflowParams.put("input", requestBody);
            contextMap.put("workflow", workflowParams);

            // Create a single-entry map with the pattern
            Map<String, Object> inputMap = new HashMap<>();
            inputMap.put("idempotencyKey", idempotencyKeyPattern);

            // Use the replace method that takes a map and context
            Map<String, Object> replaced = parametersUtils.replace(inputMap, contextMap);

            // Return the replaced value, handling nulls gracefully
            Object replacedValue = replaced.get("idempotencyKey");
            return replacedValue != null ? replacedValue.toString() : StringUtils.EMPTY;
        }
        return StringUtils.EMPTY;
    }

    /**
     * Method has side effect
     * It removes the key from the map after retrieving it.
     * <p>
     * This is an improvement over the previous implementation where a getter had side effects
     */
    public IdempotencyStrategy popIdempotencyStrategy() {
        String key = "idempotencyStrategy";

        if (workflowsToStart == null) {
            return IdempotencyStrategy.FAIL;
        }
        if (workflowsToStart.containsKey(key)) {
            String idempotencyStrategy = (String) workflowsToStart.get(key);
            workflowsToStart.remove(key);
            try {
                return IdempotencyStrategy.valueOf(idempotencyStrategy);
            } catch (IllegalArgumentException illegalArgumentException) {
                return IdempotencyStrategy.FAIL;
            }
        }
        return IdempotencyStrategy.FAIL;
    }
}
