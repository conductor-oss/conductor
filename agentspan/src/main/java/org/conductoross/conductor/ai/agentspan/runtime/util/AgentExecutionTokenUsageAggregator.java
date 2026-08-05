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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.AggregateTokenUsage;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;

/** Aggregates LLM token usage across an execution and its sub-workflow tree. */
public final class AgentExecutionTokenUsageAggregator {

    private static final Logger log =
            LoggerFactory.getLogger(AgentExecutionTokenUsageAggregator.class);

    private final WorkflowService workflowService;
    private final Map<String, Workflow> childWorkflows;
    private final String rootExecutionId;

    public AgentExecutionTokenUsageAggregator(
            WorkflowService workflowService, String rootExecutionId) {
        this.workflowService = workflowService;
        this.rootExecutionId = rootExecutionId;
        this.childWorkflows = null;
    }

    public AgentExecutionTokenUsageAggregator(Map<String, Workflow> childWorkflows) {
        this.workflowService = null;
        this.rootExecutionId = null;
        this.childWorkflows = childWorkflows;
    }

    public AggregateTokenUsage aggregate(Workflow root) {
        AggregateTokenUsage tokenUsage = new AggregateTokenUsage();
        Set<String> visited = new HashSet<>();
        Set<String> scheduled = new HashSet<>();
        List<Workflow> pendingWorkflows = new ArrayList<>();
        schedule(root, pendingWorkflows, scheduled);

        for (int workflowIndex = 0; workflowIndex < pendingWorkflows.size(); workflowIndex++) {
            Workflow workflow = pendingWorkflows.get(workflowIndex);
            if (alreadyVisited(workflow, visited)) {
                continue;
            }
            processTasks(workflow, tokenUsage, pendingWorkflows, scheduled);
        }

        return tokenUsage;
    }

    private static boolean alreadyVisited(Workflow workflow, Set<String> visited) {
        String workflowId = workflow.getWorkflowId();
        return workflowId != null && !visited.add(workflowId);
    }

    private void processTasks(
            Workflow workflow,
            AggregateTokenUsage tokenUsage,
            List<Workflow> pendingWorkflows,
            Set<String> scheduled) {
        List<Task> tasks = workflow.getTasks();
        if (tasks == null) {
            return;
        }
        for (Task task : tasks) {
            addTokenUsage(tokenUsage, task);
            scheduleChild(task.getSubWorkflowId(), pendingWorkflows, scheduled);
        }
    }

    private static void addTokenUsage(AggregateTokenUsage aggregate, Task task) {
        if (!"LLM_CHAT_COMPLETE".equalsIgnoreCase(task.getTaskType())) {
            return;
        }
        Map<String, Object> output = task.getOutputData();
        if (output == null) {
            return;
        }

        long promptTokens = toLong(output.get("promptTokens"));
        long completionTokens = toLong(output.get("completionTokens"));
        long totalTokens = toLong(output.get("tokenUsed"));
        aggregate.setPromptTokens(aggregate.getPromptTokens() + promptTokens);
        aggregate.setCompletionTokens(aggregate.getCompletionTokens() + completionTokens);
        aggregate.setTotalTokens(
                aggregate.getTotalTokens()
                        + (totalTokens > 0 ? totalTokens : promptTokens + completionTokens));
    }

    private void scheduleChild(
            String childId, List<Workflow> pendingWorkflows, Set<String> scheduled) {
        if (StringUtils.isBlank(childId) || !scheduled.add(childId)) {
            return;
        }
        Workflow child = loadChild(childId);
        if (child != null) {
            pendingWorkflows.add(child);
        }
    }

    private static void schedule(
            Workflow workflow, List<Workflow> pendingWorkflows, Set<String> scheduled) {
        pendingWorkflows.add(workflow);
        if (StringUtils.isNotBlank(workflow.getWorkflowId())) {
            scheduled.add(workflow.getWorkflowId());
        }
    }

    private Workflow loadChild(String childId) {
        if (childWorkflows != null) {
            return childWorkflows.get(childId);
        }
        try {
            return workflowService.getExecutionStatus(childId, true);
        } catch (RuntimeException e) {
            log.warn(
                    "Unable to include sub-workflow {} in token aggregation for {}",
                    childId,
                    rootExecutionId,
                    e);
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
