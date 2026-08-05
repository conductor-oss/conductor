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

    public Workflow.AggregateTokenUsage aggregate(Workflow root) {
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        Set<String> visited = new HashSet<>();
        Set<String> scheduled = new HashSet<>();
        Deque<Workflow> pending = new ArrayDeque<>();
        pending.add(root);
        if (StringUtils.isNotBlank(root.getWorkflowId())) {
            scheduled.add(root.getWorkflowId());
        }

        while (!pending.isEmpty()) {
            Workflow workflow = pending.removeFirst();
            String workflowId = workflow.getWorkflowId();
            if (workflowId != null && !visited.add(workflowId)) {
                continue;
            }

            List<Task> workflowTasks = workflow.getTasks();
            if (workflowTasks == null) {
                continue;
            }
            for (Task task : workflowTasks) {
                if ("LLM_CHAT_COMPLETE".equalsIgnoreCase(task.getTaskType())) {
                    Map<String, Object> output = task.getOutputData();
                    if (output != null) {
                        long taskPromptTokens = toLong(output.get("promptTokens"));
                        long taskCompletionTokens = toLong(output.get("completionTokens"));
                        long taskTotalTokens = toLong(output.get("tokenUsed"));
                        promptTokens += taskPromptTokens;
                        completionTokens += taskCompletionTokens;
                        totalTokens +=
                                taskTotalTokens > 0
                                        ? taskTotalTokens
                                        : taskPromptTokens + taskCompletionTokens;
                    }
                }

                String childId = task.getSubWorkflowId();
                if (StringUtils.isNotBlank(childId) && scheduled.add(childId)) {
                    Workflow child = loadChild(childId);
                    if (child != null) {
                        pending.addLast(child);
                    }
                }
            }
        }

        return new Workflow.AggregateTokenUsage(promptTokens, completionTokens, totalTokens);
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
