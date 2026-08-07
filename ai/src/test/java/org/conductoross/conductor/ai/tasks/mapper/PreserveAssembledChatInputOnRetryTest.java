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
package org.conductoross.conductor.ai.tasks.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.model.TaskModel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AIModelTaskMapper#preserveAssembledChatInputOnRetry(TaskModel, String...)}.
 *
 * <p>The chat-complete mappers assemble {@code messages}/{@code tools} imperatively (conversation
 * history, f-string substitution) and write them into the task's {@code inputData}. Retry and rerun
 * do not re-run the mapper: they copy the task and re-resolve the carried definition's {@code
 * inputParameters} over it ({@code putAll}), which would overwrite the assembled values with the
 * definition's static template. Detaching those keys from a task-owned copy of the definition means
 * re-resolution never produces them, so the attempt's own input survives.
 */
class PreserveAssembledChatInputOnRetryTest {

    private final ChatCompleteTaskMapper mapper = new ChatCompleteTaskMapper();

    private static WorkflowTask sharedDefinition() {
        WorkflowTask def = new WorkflowTask();
        def.setName("chat");
        def.setTaskReferenceName("chat_ref");
        Map<String, Object> params = new HashMap<>();
        params.put(
                "messages",
                List.of(
                        Map.of("role", "system", "message", "You are helpful."),
                        Map.of("role", "user", "message", "${workflow.input.question}")));
        params.put("tools", List.of("calculator"));
        params.put("llmProvider", "openai");
        params.put("model", "gpt-4o");
        def.setInputParameters(params);
        return def;
    }

    private static TaskModel mappedTask(WorkflowTask sharedDef) {
        TaskModel task = new TaskModel();
        task.setTaskId("task-1");
        task.setReferenceTaskName("chat_ref");
        task.setWorkflowTask(sharedDef);
        // What the mapper assembled imperatively: resolved template + history walked from the
        // workflow — none of it derivable from the definition.
        task.getInputData()
                .put(
                        "messages",
                        List.of(
                                Map.of("role", "system", "message", "You are helpful."),
                                Map.of("role", "user", "message", "What is the capital of France?"),
                                Map.of("role", "assistant", "message", "Paris."),
                                Map.of("role", "user", "message", "And its population?")));
        task.getInputData().put("tools", List.of("calculator"));
        task.getInputData().put("llmProvider", "openai");
        task.getInputData().put("model", "gpt-4o");
        return task;
    }

    /** Replicates WorkflowExecutorOps#taskToBeRescheduled's input handling exactly. */
    private static TaskModel simulateRetry(TaskModel task) {
        TaskModel retried = task.copy();
        // parametersUtils.getTaskInput resolves the CARRIED definition's inputParameters; for a
        // template-free map that is the map itself. The point under test is structural: which
        // keys exist in the resolved map at all.
        Map<String, Object> resolved =
                new HashMap<>(retried.getWorkflowTask().getInputParameters());
        retried.getInputData().putAll(resolved);
        return retried;
    }

    @Test
    void detachReplacesDefinitionWithTaskOwnedCopyLackingAssembledKeys() {
        WorkflowTask sharedDef = sharedDefinition();
        TaskModel task = mappedTask(sharedDef);

        mapper.preserveAssembledChatInputOnRetry(task, "messages", "tools");

        assertThat(task.getWorkflowTask()).isNotSameAs(sharedDef);
        assertThat(task.getWorkflowTask().getInputParameters())
                .doesNotContainKeys("messages", "tools")
                .containsKeys("llmProvider", "model");
        // The assembled input itself is untouched.
        assertThat((List<?>) task.getInputData().get("messages")).hasSize(4);
    }

    @Test
    void sharedCachedDefinitionIsNeverMutated() {
        WorkflowTask sharedDef = sharedDefinition();
        TaskModel task = mappedTask(sharedDef);

        mapper.preserveAssembledChatInputOnRetry(task, "messages", "tools");

        // The instance from the cached WorkflowDef — read by every execution and every DO_WHILE
        // iteration — must keep its template.
        assertThat(sharedDef.getInputParameters()).containsKeys("messages", "tools");
    }

    @Test
    void retryPreservesTheAssembledConversation() {
        WorkflowTask sharedDef = sharedDefinition();
        TaskModel task = mappedTask(sharedDef);
        mapper.preserveAssembledChatInputOnRetry(task, "messages", "tools");

        TaskModel retried = simulateRetry(task);

        // The 4-message assembled conversation survives; the static 2-message template never
        // enters the resolved input.
        assertThat((List<?>) retried.getInputData().get("messages")).hasSize(4);
        assertThat(retried.getInputData().get("tools")).isEqualTo(List.of("calculator"));
        // Declarative keys are still re-resolved as before.
        assertThat(retried.getInputData()).containsEntry("llmProvider", "openai");
    }

    @Test
    void withoutDetachRetryClobbersHistory_documentsTheBug() {
        WorkflowTask sharedDef = sharedDefinition();
        TaskModel task = mappedTask(sharedDef);
        // No detach: this is the pre-fix behaviour.

        TaskModel retried = simulateRetry(task);

        // The assembled 4-message conversation is overwritten by the 2-message static template.
        assertThat((List<?>) retried.getInputData().get("messages")).hasSize(2);
    }

    @Test
    void templatePatternsInsideConversationContentAreNeverReInterpreted() {
        WorkflowTask sharedDef = sharedDefinition();
        TaskModel task = mappedTask(sharedDef);
        // A conversation turn whose content contains template-like syntax (very common in
        // code-generation chats). It must survive retry verbatim — which is guaranteed
        // structurally, because detached keys never pass through the resolver at all.
        task.getInputData()
                .put(
                        "messages",
                        List.of(
                                Map.of(
                                        "role", "assistant",
                                        "message", "Use `${workflow.input.secret}` as the key.")));
        mapper.preserveAssembledChatInputOnRetry(task, "messages", "tools");

        TaskModel retried = simulateRetry(task);

        assertThat(retried.getWorkflowTask().getInputParameters()).doesNotContainKey("messages");
        List<?> messages = (List<?>) retried.getInputData().get("messages");
        @SuppressWarnings("unchecked")
        Map<String, Object> only = (Map<String, Object>) messages.get(0);
        assertThat(only.get("message")).isEqualTo("Use `${workflow.input.secret}` as the key.");
    }

    @Test
    void noOpWhenDefinitionDoesNotDeclareAssembledKeys() {
        WorkflowTask sharedDef = sharedDefinition();
        sharedDef.getInputParameters().remove("messages");
        sharedDef.getInputParameters().remove("tools");
        TaskModel task = mappedTask(sharedDef);

        mapper.preserveAssembledChatInputOnRetry(task, "messages", "tools");

        // Nothing to remove, so the task's definition is unchanged in content and the shared
        // instance is left alone.
        assertThat(task.getWorkflowTask().getInputParameters())
                .doesNotContainKeys("messages", "tools")
                .containsEntry("llmProvider", "openai")
                .containsEntry("model", "gpt-4o");
        assertThat(sharedDef.getInputParameters()).doesNotContainKeys("messages", "tools");
    }

    @Test
    void nullDefinitionAndNullParametersAreTolerated() {
        TaskModel task = new TaskModel();
        task.setTaskId("task-2");
        mapper.preserveAssembledChatInputOnRetry(task, "messages");

        WorkflowTask def = new WorkflowTask();
        def.setInputParameters(null);
        task.setWorkflowTask(def);
        mapper.preserveAssembledChatInputOnRetry(task, "messages");

        assertThat(task.getWorkflowTask()).isSameAs(def);
    }
}
