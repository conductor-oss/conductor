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
import org.conductoross.conductor.ai.model.LLMResponse;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.run.AggregateTokenUsage;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTokenAggregationTest {

    @Test
    void aggregatesTokensAcrossSiblingAndNestedSubWorkflowsOnce() {
        Workflow root =
                workflow(
                        "root",
                        llmTask(10, 2, 12),
                        llmTask(20, 4, 24),
                        llmTask(30, 6, 36),
                        subWorkflowTask("researcher"),
                        subWorkflowTask("writer"));
        Workflow researcher =
                workflow(
                        "researcher",
                        llmTask(40, 8, 48),
                        llmTask(50, 10, 60),
                        subWorkflowTask("search"),
                        subWorkflowTask("analysis"));
        Workflow writer =
                workflow(
                        "writer",
                        llmTask(60, 12, 72),
                        llmTask(70, 14, 84),
                        subWorkflowTask("outline"),
                        subWorkflowTask("review"));
        Workflow search = workflow("search", llmTask(80, 16, 96));
        Workflow analysis = workflow("analysis", llmTask(90, 18, 108));
        Workflow outline = workflow("outline", llmTask(100, 20, 120));
        Workflow review =
                workflow(
                        "review",
                        llmTask(110, 22, 132),
                        // A malformed cycle must not duplicate root usage.
                        subWorkflowTask("root"));
        Map<String, Workflow> executions =
                Map.of(
                        "root", root,
                        "researcher", researcher,
                        "writer", writer,
                        "search", search,
                        "analysis", analysis,
                        "outline", outline,
                        "review", review);

        AggregateTokenUsage aggregate = aggregatorWith(executions).aggregate(root);

        assertThat(aggregate)
                .extracting(
                        AggregateTokenUsage::getPromptTokens,
                        AggregateTokenUsage::getCompletionTokens,
                        AggregateTokenUsage::getTotalTokens)
                .containsExactly(660L, 132L, 792L);
    }

    @Test
    void fallsBackToPromptPlusCompletionWhenProviderOmitsTotal() {
        Workflow root = workflow("root", llmTask(7, 3, 0));

        assertThat(aggregatorWith(Map.of()).aggregate(root).getTotalTokens()).isEqualTo(10L);
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
        AggregateTokenUsage aggregate = aggregatorWith(Map.of("child", child)).aggregate(root);

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

        AggregateTokenUsage aggregate =
                aggregatorWith(Map.of("available", available)).aggregate(root);

        assertThat(aggregate)
                .extracting(
                        AggregateTokenUsage::getPromptTokens,
                        AggregateTokenUsage::getCompletionTokens,
                        AggregateTokenUsage::getTotalTokens)
                .containsExactly(30L, 6L, 36L);
    }

    @Test
    void preservesTokenCountsLargerThanIntegerRange() {
        long largeTokenCount = (long) Integer.MAX_VALUE + 1;
        Workflow root = workflow("root", llmTask(largeTokenCount, "2", largeTokenCount + 2));

        assertThat(aggregatorWith(Map.of()).aggregate(root))
                .extracting(
                        AggregateTokenUsage::getPromptTokens,
                        AggregateTokenUsage::getCompletionTokens,
                        AggregateTokenUsage::getTotalTokens)
                .containsExactly(largeTokenCount, 2L, largeTokenCount + 2);
    }

    private static Workflow workflow(String id, Task... tasks) {
        Workflow workflow = new Workflow();
        workflow.setWorkflowId(id);
        workflow.setTasks(List.of(tasks));
        return workflow;
    }

    private static AgentExecutionTokenUsageAggregator aggregatorWith(
            Map<String, Workflow> executions) {
        WorkflowService workflowService = mock(WorkflowService.class);
        when(workflowService.getExecutionStatus(anyString(), eq(true)))
                .thenAnswer(invocation -> executions.get(invocation.getArgument(0)));
        return new AgentExecutionTokenUsageAggregator(workflowService);
    }

    private static Task llmTask(Object promptTokens, Object completionTokens, Object tokenUsed) {
        Task task = new Task();
        task.setTaskType(TaskType.LLM_CHAT_COMPLETE.name());
        Map<String, Object> output = new HashMap<>();
        output.put(LLMResponse.PROMPT_TOKENS, promptTokens);
        output.put(LLMResponse.COMPLETION_TOKENS, completionTokens);
        output.put(LLMResponse.TOKEN_USED, tokenUsed);
        task.setOutputData(output);
        return task;
    }

    private static Task subWorkflowTask(String childId) {
        Task task = new Task();
        task.setTaskType(TaskType.SUB_WORKFLOW.name());
        task.setSubWorkflowId(childId);
        return task;
    }
}
