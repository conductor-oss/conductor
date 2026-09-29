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
package org.conductoross.conductor.ai.agentspan.runtime.util;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.ai.model.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.AggregateTokenUsage;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;

/** Aggregates LLM token usage across an execution and its sub-workflow tree. */
@Component
public final class AgentExecutionTokenUsageAggregator {

    private static final Logger log =
            LoggerFactory.getLogger(AgentExecutionTokenUsageAggregator.class);

    private final WorkflowService workflowService;

    public AgentExecutionTokenUsageAggregator(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    public AggregateTokenUsage aggregate(Workflow root) {
        AggregateTokenUsage tokenUsage = new AggregateTokenUsage();
        aggregateWorkflow(root, tokenUsage, new HashSet<>());
        return tokenUsage;
    }

    private void aggregateWorkflow(
            Workflow workflow, AggregateTokenUsage tokenUsage, Set<String> visitedWorkflowIds) {
        String workflowId = workflow.getWorkflowId();
        List<Task> tasks = workflow.getTasks();
        if ((StringUtils.isNotBlank(workflowId) && !visitedWorkflowIds.add(workflowId))
                || CollectionUtils.isEmpty(tasks)) {
            return;
        }

        for (Task task : tasks) {
            addLlmTaskTokenUsage(tokenUsage, task);
            aggregateSubWorkflowTokenUsage(task, tokenUsage, visitedWorkflowIds);
        }
    }

    private static void addLlmTaskTokenUsage(AggregateTokenUsage aggregate, Task task) {
        Map<String, Object> output = task.getOutputData();
        if (!TaskType.LLM_CHAT_COMPLETE.name().equalsIgnoreCase(task.getTaskType())
                || output == null) {
            return;
        }

        long promptTokens = toLong(output.get(LLMResponse.PROMPT_TOKENS));
        long completionTokens = toLong(output.get(LLMResponse.COMPLETION_TOKENS));
        long totalTokens = toLong(output.get(LLMResponse.TOKEN_USED));
        aggregate.setPromptTokens(aggregate.getPromptTokens() + promptTokens);
        aggregate.setCompletionTokens(aggregate.getCompletionTokens() + completionTokens);
        aggregate.setTotalTokens(
                aggregate.getTotalTokens()
                        + (totalTokens > 0 ? totalTokens : promptTokens + completionTokens));
    }

    /** Recursively aggregates token usage from the task's sub-workflow. */
    private void aggregateSubWorkflowTokenUsage(
            Task task, AggregateTokenUsage tokenUsage, Set<String> visitedWorkflowIds) {
        String childWorkflowId = task.getSubWorkflowId();
        if (StringUtils.isBlank(childWorkflowId) || visitedWorkflowIds.contains(childWorkflowId)) {
            return;
        }

        Workflow childWorkflow = loadChild(childWorkflowId);
        if (childWorkflow != null) {
            aggregateWorkflow(childWorkflow, tokenUsage, visitedWorkflowIds);
        }
    }

    private Workflow loadChild(String childId) {
        try {
            return workflowService.getExecutionStatus(childId, true);
        } catch (RuntimeException e) {
            log.warn("Unable to include sub-workflow {} in token aggregation", childId, e);
            return null;
        }
    }

    private static long toLong(Object value) {
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}
