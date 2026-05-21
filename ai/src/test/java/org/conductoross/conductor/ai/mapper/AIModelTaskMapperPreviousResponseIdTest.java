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
package org.conductoross.conductor.ai.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.AIModelProvider;
import org.conductoross.conductor.ai.models.LLMWorkerInput;
import org.conductoross.conductor.ai.tasks.mapper.ChatCompleteTaskMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@code AIModelTaskMapper.threadPreviousResponseId()} — the auto-injection of
 * OpenAI Responses API {@code previousResponseId} across iterations of the same task within a
 * workflow (typically a DoWhile loop).
 *
 * <p>The mapper is exercised via {@link ChatCompleteTaskMapper}, its concrete subclass, since the
 * threading method is private. These tests cover the workflow-side wiring that the existing
 * integration test ({@code AIModelIntegrationTest.testPreviousResponseId}) cannot — that test
 * verifies the chat-model API plumbing against a live OpenAI endpoint, but does not exercise the
 * mapper's prior-task lookup.
 */
class AIModelTaskMapperPreviousResponseIdTest {

    private static final String LLM_REF = "chat_loop_iteration";

    @Test
    @Disabled(
            "Auto-threading of previousResponseId is disabled in AIModelTaskMapper "
                    + "(see threadPreviousResponseId javadoc for token-accumulation reasoning). "
                    + "Re-enable this test alongside the feature once the mapper emits "
                    + "delta-only history for Responses API loops.")
    void priorIterationsResponseIdIsInjectedOntoCurrentTaskInput() {
        // Two terminal iterations of the LLM_CHAT_COMPLETE task already ran and
        // recorded responseId on their output. The mapper schedules a third
        // iteration; it should inherit the most recent prior responseId.
        WorkflowModel workflow = newWorkflowWithLoop();
        workflow.getTasks().add(completedIteration("resp_first"));
        workflow.getTasks().add(completedIteration("resp_second"));

        Map<String, Object> input = chatInput();
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        assertEquals(1, mapped.size());
        assertEquals(
                "resp_second",
                mapped.get(0).getInputData().get("previousResponseId"),
                "should inject the most-recent prior responseId");
    }

    @Test
    void explicitPreviousResponseIdSetByCallerIsNotOverwritten() {
        WorkflowModel workflow = newWorkflowWithLoop();
        workflow.getTasks().add(completedIteration("resp_old"));

        Map<String, Object> input = chatInput();
        input.put("previousResponseId", "resp_explicit_user_choice");

        TaskMapperContext ctx = newContext(workflow, llmTask(), input);
        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        assertEquals(
                "resp_explicit_user_choice",
                mapped.get(0).getInputData().get("previousResponseId"),
                "an explicit caller value must take precedence over auto-threading");
    }

    @Test
    void firstIterationGetsNoPreviousResponseIdInjected() {
        // No prior tasks ⇒ first turn of the conversation, request omits the field.
        WorkflowModel workflow = newWorkflowWithLoop();

        Map<String, Object> input = chatInput();
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        assertNull(mapped.get(0).getInputData().get("previousResponseId"));
    }

    @Test
    void priorTasksWithDifferentRefNameAreIgnored() {
        // Auto-threading must scope by taskReferenceName — a sibling LLM task in
        // a parallel branch must not leak its responseId into this loop's chain.
        WorkflowModel workflow = newWorkflowWithLoop();
        TaskModel sibling = completedIteration("resp_sibling");
        sibling.getWorkflowTask().setTaskReferenceName("other_llm_task");
        workflow.getTasks().add(sibling);

        Map<String, Object> input = chatInput();
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        assertNull(
                mapped.get(0).getInputData().get("previousResponseId"),
                "responseId from a different taskReferenceName must not be inherited");
    }

    @Test
    void localHistoryInjectionIsSkippedWhenPreviousResponseIdIsSet() {
        // Two prior completed iterations recorded responseId + actual chat output.
        // ChatCompleteTaskMapper.getHistory() would normally append those prior
        // assistant messages into the next iteration's messages array — but it
        // drops the matching prior user messages, leaving a malformed conversation
        // that confuses the model. When previousResponseId is set (here, by the
        // caller; auto-threading from prior iterations is currently disabled in
        // AIModelTaskMapper), OpenAI's server-side conversation store is the
        // single source of truth for same-refName loop iterations, so that
        // specific branch of history injection MUST be skipped. (Participants,
        // tool calls, sub-workflow tool results are still preserved by
        // getHistory(); see separate tests.)
        WorkflowModel workflow = newWorkflowWithLoop();
        TaskModel prior1 = completedIteration("resp_one");
        prior1.getOutputData().put("result", "Got it.");
        prior1.setIteration(1); // makes isLoopOverTask() true
        workflow.getTasks().add(prior1);

        TaskModel prior2 = completedIteration("resp_two");
        prior2.getOutputData().put("result", "Got it.");
        prior2.setIteration(2);
        workflow.getTasks().add(prior2);

        Map<String, Object> input = chatInput();
        input.put("messages", new ArrayList<Map<String, Object>>());
        input.put("previousResponseId", "resp_two");
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        // previousResponseId passed through from input unchanged.
        assertEquals("resp_two", mapped.get(0).getInputData().get("previousResponseId"));

        // The messages array on input must NOT have been augmented with prior
        // assistant responses — that's what getHistory() does, and we now skip
        // it whenever previousResponseId is in play.
        @SuppressWarnings("unchecked")
        List<Object> messages = (List<Object>) mapped.get(0).getInputData().get("messages");
        assertTrue(
                messages == null || messages.isEmpty(),
                "with previousResponseId set, no local history should be appended; "
                        + "saw messages="
                        + messages);
    }

    @Test
    void participantHistoryIsPreservedEvenWhenPreviousResponseIdIsSet() {
        // Regression: getHistory() used to be skipped entirely when
        // previousResponseId was set, which dropped participant messages,
        // sub-workflow tool calls, and media. Now only the same-refName
        // loop-iteration assistant branch is suppressed — participants
        // (a different refName) must still flow through, because OpenAI's
        // Responses API server-side conversation store has never seen them.
        WorkflowModel workflow = newWorkflowWithLoop();

        // A prior loop iteration of the chat task itself — must be skipped.
        TaskModel priorChat = completedIteration("resp_chat");
        priorChat.getOutputData().put("result", "I am the assistant.");
        priorChat.setIteration(1);
        workflow.getTasks().add(priorChat);

        // A participant task — different refName, registered in participants
        // map — must NOT be skipped. We use a non-SIMPLE task type so the
        // participant flows through getHistory()'s plain text-message branch
        // rather than the SIMPLE/HTTP tool-call wrapping branch; that keeps
        // the assertion focused on the "did this task contribute history"
        // question rather than re-testing tool-call serialization shape.
        TaskModel participant = new TaskModel();
        participant.setStatus(TaskModel.Status.COMPLETED);
        participant.setTaskType("HUMAN");
        WorkflowTask participantTask = new WorkflowTask();
        participantTask.setName("user_proxy");
        participantTask.setTaskReferenceName("user_proxy_ref");
        participantTask.setType("HUMAN");
        participant.setWorkflowTask(participantTask);
        Map<String, Object> participantOutput = new HashMap<>();
        participantOutput.put("result", "User says: continue.");
        participant.setOutputData(participantOutput);
        workflow.getTasks().add(participant);

        Map<String, Object> input = chatInput();
        input.put("messages", new ArrayList<Map<String, Object>>());
        input.put("previousResponseId", "resp_chat");
        Map<String, String> participants = new HashMap<>();
        participants.put("user_proxy_ref", "user"); // participant role
        input.put("participants", participants);
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        // previousResponseId passed through from input unchanged.
        assertEquals("resp_chat", mapped.get(0).getInputData().get("previousResponseId"));

        // The participant message must have made it through. The loop-iteration
        // assistant message must NOT have. ``messages`` is a List<ChatMessage>
        // at this point — the mapper translates inputData → ChatCompletion →
        // ChatMessage list and writes it back.
        @SuppressWarnings("unchecked")
        List<org.conductoross.conductor.ai.models.ChatMessage> messages =
                (List<org.conductoross.conductor.ai.models.ChatMessage>)
                        mapped.get(0).getInputData().get("messages");
        assertTrue(
                messages != null && !messages.isEmpty(), "participant message must be preserved");
        boolean sawParticipant =
                messages.stream().anyMatch(m -> "User says: continue.".equals(m.getMessage()));
        boolean sawAssistantLoopIteration =
                messages.stream().anyMatch(m -> "I am the assistant.".equals(m.getMessage()));
        assertTrue(sawParticipant, "participant 'user' message must flow through: " + messages);
        assertTrue(
                !sawAssistantLoopIteration,
                "same-refName loop-iteration assistant must NOT flow through: " + messages);
    }

    @Test
    void localHistoryInjectionIsSkippedWhenProviderRejectsPrefill() {
        // Provider-agnostic check: the mapper asks the resolved AIModel
        // whether it accepts assistant-message prefill, and suppresses the
        // same-refName loop-iteration injection when the provider says no.
        // Anthropic Claude Sonnet 4.6+ is the original motivating case (it
        // returns 400 "This model does not support assistant message prefill.
        // The conversation must end with a user message"), but the suppression
        // path itself is keyed off the capability flag — not a hardcoded
        // provider name — so any future provider that declares the same
        // capability is covered automatically.
        WorkflowModel workflow = newWorkflowWithLoop();
        TaskModel prior = completedIteration("ignored_resp");
        prior.getOutputData().put("result", "Loop-iteration assistant.");
        prior.setIteration(1); // makes isLoopOverTask() true
        workflow.getTasks().add(prior);

        Map<String, Object> input = chatInput();
        input.put("llmProvider", "anthropic");
        input.put("model", "claude-sonnet-4-6");
        input.put("messages", new ArrayList<Map<String, Object>>());
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        ChatCompleteTaskMapper mapper = new ChatCompleteTaskMapper(providerFor("anthropic", false));
        List<TaskModel> mapped = mapper.getMappedTasks(ctx);

        @SuppressWarnings("unchecked")
        List<org.conductoross.conductor.ai.models.ChatMessage> messages =
                (List<org.conductoross.conductor.ai.models.ChatMessage>)
                        mapped.get(0).getInputData().get("messages");
        assertTrue(
                messages == null || messages.isEmpty(),
                "providers that reject prefill must not auto-inject loop assistant history; saw "
                        + messages);
    }

    @Test
    void localHistoryInjectionStillRunsWhenProviderAcceptsPrefill() {
        // Regression safety: the capability check must not over-suppress.
        // A provider that accepts prefill (the historical default) should
        // still receive the auto-injected loop-iteration assistant history,
        // matching pre-capability behavior for OpenAI without previousResponseId,
        // Gemini, and similar.
        WorkflowModel workflow = newWorkflowWithLoop();
        TaskModel prior = completedIteration("ignored_resp");
        prior.getOutputData().put("result", "Loop-iteration assistant.");
        prior.setIteration(1);
        workflow.getTasks().add(prior);

        Map<String, Object> input = chatInput();
        input.put("messages", new ArrayList<Map<String, Object>>());
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        ChatCompleteTaskMapper mapper = new ChatCompleteTaskMapper(providerFor("openai", true));
        List<TaskModel> mapped = mapper.getMappedTasks(ctx);

        @SuppressWarnings("unchecked")
        List<org.conductoross.conductor.ai.models.ChatMessage> messages =
                (List<org.conductoross.conductor.ai.models.ChatMessage>)
                        mapped.get(0).getInputData().get("messages");
        boolean sawAssistantLoopIteration =
                messages != null
                        && messages.stream()
                                .anyMatch(m -> "Loop-iteration assistant.".equals(m.getMessage()));
        assertTrue(
                sawAssistantLoopIteration,
                "providers that accept prefill must still receive loop-iteration history; saw "
                        + messages);
    }

    @Test
    void participantHistoryIsPreservedWhenProviderRejectsPrefill() {
        // Regression safety against an over-broad suppression. The capability
        // gate must scope to the same-refName loop-iteration assistant branch
        // only — participants (a different refName, registered in participants
        // map) are legitimate conversation turns and must still flow through.
        WorkflowModel workflow = newWorkflowWithLoop();

        TaskModel priorChat = completedIteration("ignored_resp");
        priorChat.getOutputData().put("result", "Loop-iteration assistant.");
        priorChat.setIteration(1);
        workflow.getTasks().add(priorChat);

        TaskModel participant = new TaskModel();
        participant.setStatus(TaskModel.Status.COMPLETED);
        participant.setTaskType("HUMAN");
        WorkflowTask participantTask = new WorkflowTask();
        participantTask.setName("user_proxy");
        participantTask.setTaskReferenceName("user_proxy_ref");
        participantTask.setType("HUMAN");
        participant.setWorkflowTask(participantTask);
        Map<String, Object> participantOutput = new HashMap<>();
        participantOutput.put("result", "User says: continue.");
        participant.setOutputData(participantOutput);
        workflow.getTasks().add(participant);

        Map<String, Object> input = chatInput();
        input.put("llmProvider", "anthropic");
        input.put("model", "claude-sonnet-4-6");
        input.put("messages", new ArrayList<Map<String, Object>>());
        Map<String, String> participants = new HashMap<>();
        participants.put("user_proxy_ref", "user");
        input.put("participants", participants);
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        ChatCompleteTaskMapper mapper = new ChatCompleteTaskMapper(providerFor("anthropic", false));
        List<TaskModel> mapped = mapper.getMappedTasks(ctx);

        @SuppressWarnings("unchecked")
        List<org.conductoross.conductor.ai.models.ChatMessage> messages =
                (List<org.conductoross.conductor.ai.models.ChatMessage>)
                        mapped.get(0).getInputData().get("messages");
        assertTrue(
                messages != null && !messages.isEmpty(),
                "participant message must be preserved when provider rejects prefill");
        boolean sawParticipant =
                messages.stream().anyMatch(m -> "User says: continue.".equals(m.getMessage()));
        boolean sawAssistantLoopIteration =
                messages.stream().anyMatch(m -> "Loop-iteration assistant.".equals(m.getMessage()));
        assertTrue(sawParticipant, "participant 'user' message must flow through: " + messages);
        assertTrue(
                !sawAssistantLoopIteration,
                "same-refName loop-iteration assistant must NOT flow when provider rejects prefill: "
                        + messages);
    }

    @Test
    void inProgressPriorTaskIsSkippedEvenIfItAlreadyHasAResponseId() {
        // A task is only considered if its status is terminal. An in-flight
        // task with a (partial) responseId on its output must not be threaded.
        WorkflowModel workflow = newWorkflowWithLoop();
        TaskModel inFlight = completedIteration("resp_partial");
        inFlight.setStatus(TaskModel.Status.IN_PROGRESS);
        workflow.getTasks().add(inFlight);

        Map<String, Object> input = chatInput();
        TaskMapperContext ctx = newContext(workflow, llmTask(), input);

        List<TaskModel> mapped = new ChatCompleteTaskMapper().getMappedTasks(ctx);

        assertTrue(
                mapped.get(0).getInputData().get("previousResponseId") == null,
                "in-progress prior task should not contribute a responseId");
    }

    // -- fixtures --

    private static WorkflowModel newWorkflowWithLoop() {
        WorkflowModel workflow = new WorkflowModel();
        workflow.setWorkflowDefinition(new WorkflowDef());
        workflow.setTasks(new ArrayList<>());
        return workflow;
    }

    private static WorkflowTask llmTask() {
        WorkflowTask wt = new WorkflowTask();
        wt.setName("chat_complete");
        wt.setTaskReferenceName(LLM_REF);
        wt.setType("LLM_CHAT_COMPLETE");
        return wt;
    }

    private static Map<String, Object> chatInput() {
        Map<String, Object> input = new HashMap<>();
        input.put("llmProvider", "openai");
        input.put("model", "gpt-5.3-codex");
        return input;
    }

    private static TaskModel completedIteration(String responseId) {
        TaskModel prior = new TaskModel();
        prior.setStatus(TaskModel.Status.COMPLETED);
        prior.setTaskType("LLM_CHAT_COMPLETE");
        prior.setWorkflowTask(llmTask());
        Map<String, Object> out = new HashMap<>();
        out.put("responseId", responseId);
        prior.setOutputData(out);
        return prior;
    }

    private static TaskMapperContext newContext(
            WorkflowModel workflow, WorkflowTask task, Map<String, Object> input) {
        return TaskMapperContext.newBuilder()
                .withWorkflowModel(workflow)
                .withTaskDefinition(new TaskDef())
                .withWorkflowTask(task)
                .withTaskInput(input)
                .withRetryCount(0)
                .withTaskId("task-" + System.nanoTime())
                .build();
    }

    /**
     * Stubs an {@link AIModelProvider} that returns a single {@link AIModel} for the given provider
     * name, with {@link AIModel#supportsAssistantPrefill()} configured per the second argument. The
     * model's other abstract methods are never invoked by the mapper code under test, so Mockito's
     * default returns are fine.
     */
    private static AIModelProvider providerFor(String providerName, boolean supportsPrefill) {
        AIModel model = mock(AIModel.class);
        when(model.getModelProvider()).thenReturn(providerName);
        when(model.supportsAssistantPrefill()).thenReturn(supportsPrefill);
        AIModelProvider provider = mock(AIModelProvider.class);
        when(provider.getModel(any(LLMWorkerInput.class))).thenReturn(model);
        return provider;
    }
}
