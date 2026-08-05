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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.util.AgentExecutionTokenUsageAggregator;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;

import static org.assertj.core.api.Assertions.assertThat;

class AgentServiceTokenAggregationTest {

    @Test
    void aggregatesTokensAcrossNestedSubWorkflowsOnce() {
        Workflow root = workflow("root", llmTask(10, 2, 12), subWorkflowTask("child"));
        Workflow child = workflow("child", llmTask(20, 4, 24), subWorkflowTask("grandchild"));
        Workflow grandchild =
                workflow(
                        "grandchild",
                        llmTask(30, 6, 36),
                        // A malformed cycle must not duplicate root usage.
                        subWorkflowTask("root"));
        Map<String, Workflow> executions =
                Map.of("root", root, "child", child, "grandchild", grandchild);

        Workflow.AggregateTokenUsage aggregate =
                new AgentExecutionTokenUsageAggregator(executions).aggregate(root);

        assertThat(aggregate)
                .extracting(
                        Workflow.AggregateTokenUsage::getPromptTokens,
                        Workflow.AggregateTokenUsage::getCompletionTokens,
                        Workflow.AggregateTokenUsage::getTotalTokens)
                .containsExactly(60L, 12L, 72L);
    }

    @Test
    void fallsBackToPromptPlusCompletionWhenProviderOmitsTotal() {
        Workflow root = workflow("root", llmTask(7, 3, 0));

        assertThat(
                        new AgentExecutionTokenUsageAggregator(Map.of())
                                .aggregate(root)
                                .getTotalTokens())
                .isEqualTo(10L);
    }

    @Test
    void countsARepeatedDescendantOnlyOnce() {
        Workflow child = workflow("child", llmTask(20, 4, 24));
        Workflow root =
                workflow(
                        "root",
                        llmTask(10, 2, 12),
                        subWorkflowTask("child"),
                        subWorkflowTask("child"));
        Workflow.AggregateTokenUsage aggregate =
                new AgentExecutionTokenUsageAggregator(Map.of("child", child)).aggregate(root);

        assertThat(aggregate.getTotalTokens()).isEqualTo(36L);
    }

    @Test
    void continuesWhenAChildExecutionIsUnavailable() {
        Workflow root =
                workflow(
                        "root",
                        llmTask(10, 2, 12),
                        subWorkflowTask("pruned"),
                        subWorkflowTask("available"));
        Workflow available = workflow("available", llmTask(20, 4, 24));

        Workflow.AggregateTokenUsage aggregate =
                new AgentExecutionTokenUsageAggregator(Map.of("available", available))
                        .aggregate(root);

        assertThat(aggregate)
                .extracting(
                        Workflow.AggregateTokenUsage::getPromptTokens,
                        Workflow.AggregateTokenUsage::getCompletionTokens,
                        Workflow.AggregateTokenUsage::getTotalTokens)
                .containsExactly(30L, 6L, 36L);
    }

    @Test
    void preservesTokenCountsLargerThanIntegerRange() {
        long largeTokenCount = (long) Integer.MAX_VALUE + 1;
        Workflow root = workflow("root", llmTask(largeTokenCount, "2", largeTokenCount + 2));

        assertThat(new AgentExecutionTokenUsageAggregator(Map.of()).aggregate(root))
                .extracting(
                        Workflow.AggregateTokenUsage::getPromptTokens,
                        Workflow.AggregateTokenUsage::getCompletionTokens,
                        Workflow.AggregateTokenUsage::getTotalTokens)
                .containsExactly(largeTokenCount, 2L, largeTokenCount + 2);
    }

    private static Workflow workflow(String id, Task... tasks) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(id);
        workflow.setTasks(List.of(tasks));
        return workflow;
    }

    private static Task llmTask(Object promptTokens, Object completionTokens, Object tokenUsed) {
        Task task = new Task();
        task.setTaskType("LLM_CHAT_COMPLETE");
        Map<String, Object> output = new HashMap<>();
        output.put("promptTokens", promptTokens);
        output.put("completionTokens", completionTokens);
        output.put("tokenUsed", tokenUsed);
        task.setOutputData(output);
        return task;
    }

    private static Task subWorkflowTask(String childId) {
        Task task = new Task();
        task.setTaskType("SUB_WORKFLOW");
        task.setSubWorkflowId(childId);
        return task;
    }
}
