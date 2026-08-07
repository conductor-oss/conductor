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
package com.netflix.conductor.test.integration

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel
import com.netflix.conductor.test.base.AbstractSpecification

import groovy.json.JsonOutput

/**
 * <p>Conversation history is not carried by {@code ${...}} references. The LLM_CHAT_COMPLETE task
 * mapper reconstructs it at scheduling time by walking the workflow's completed tasks. Retry is a
 * different code path — it copies the task and re-resolves the carried definition's
 * inputParameters over it (WorkflowExecutorOps#taskToBeRescheduled) — so it can hand the model a
 * bare template with none of the tool exchanges that already happened. The model, seeing what
 * looks like a new request, re-issues a tool call it has already made.
 */
@TestPropertySource(properties = ["conductor.integrations.ai.enabled=true"])
class LlmChatRetryContextSpec extends AbstractSpecification {

    private static final String CHAT_WORKFLOW = 'retry_llm_chat_context'
    private static final String TOOL_WORKFLOW = 'retry_tool_input_control'

    private static final String CUSTOMER_TOOL = 'lookup_customer'
    private static final String WEATHER_TOOL = 'fetch_weather'
    private static final String CHAT_REF = 'chat'

    private static final String SYSTEM_MESSAGE = 'Answer using the customer lookup you already ran.'
    private static final String USER_MESSAGE = "What's the weather where customer 4471 lives?"

    /** Distinctive token from the tool result. Asserting on it proves the ACTUAL prior exchange
     * was preserved — a merely tool-shaped message would say nothing about which call it describes. */
    private static final String TOOL_RESULT_MARKER = 'Seattle'

    @Autowired
    ExecutionDAOFacade executionDAOFacade

    def setup() {
        registerTaskDef(TaskType.LLM_CHAT_COMPLETE.name())
        registerTaskDef(CUSTOMER_TOOL)
        registerTaskDef(WEATHER_TOOL)
        metadataService.registerWorkflowDef(chatWorkflowDefinition())
        metadataService.registerWorkflowDef(toolWorkflowDefinition())
    }

    def cleanup() {
        unregister(CHAT_WORKFLOW)
        unregister(TOOL_WORKFLOW)
    }

    def "a retried LLM chat task keeps its assembled conversation"() {
        given: "a chat task scheduled after a tool call has already completed"
        String workflowId = startWorkflow(CHAT_WORKFLOW, 1, 'retry-llm-chat-context', [:], null)
        workflowTestUtil.pollAndCompleteTask(CUSTOMER_TOOL, 'worker',
                [customer_id: '4471', name: 'Jane Doe', city: TOOL_RESULT_MARKER])

        TaskModel firstAttempt = awaitChatAttempt(workflowId, 0)

        and: "the fresh dispatch did receive the tool exchange — the behaviour a retry must match"
        List<Map<String, Object>> assembled = messages(firstAttempt)
        assert roles(assembled) != ['system', 'user']
        assert hasToolExchange(assembled)

        and: "the keys are dropped from the task's own copy, not from the shared definition"
        WorkflowTask runtimeTemplate = workflowModel(workflowId)
                .workflowDefinition
                .getTaskByRefName(CHAT_REF)
        assert runtimeTemplate.inputParameters.messages*.message == [SYSTEM_MESSAGE, USER_MESSAGE]
        assert runtimeTemplate.inputParameters.tools*.name == [CUSTOMER_TOOL]
        assert !firstAttempt.workflowTask.inputParameters.containsKey('messages')
        assert !firstAttempt.workflowTask.inputParameters.containsKey('tools')
        assert firstAttempt.inputData.tools*.name == [CUSTOMER_TOOL]

        when: "the chat task fails and the workflow is retried"
        failIfPending(workflowId, firstAttempt)
        conditions.eventually {
            assert workflowModel(workflowId).status == WorkflowModel.Status.FAILED
        }
        workflowExecutor.retry(workflowId, false)

        then: "the retried attempt still carries the exchange, not the definition's bare template"
        TaskModel retried = awaitChatAttempt(workflowId, 1)
        List<Map<String, Object>> retriedMessages = messages(retried)

        // Structural: some tool exchange survived. Only [system, user] means the retry path
        // discarded the history the mapper assembled at scheduling time.
        hasToolExchange(retriedMessages)

        and: "it is the ACTUAL prior exchange, not merely something tool-shaped"
        String blob = JsonOutput.toJson(retriedMessages)
        blob.contains(CUSTOMER_TOOL)
        blob.contains(TOOL_RESULT_MARKER)

        and: "byte-for-byte what the first attempt was dispatched with"
        retriedMessages == assembled

        and: "the retried attempt carries the stripped definition too, so a second retry also holds"
        !retried.workflowTask.inputParameters.containsKey('messages')
        !retried.workflowTask.inputParameters.containsKey('tools')
    }

    // ── fixtures ─────────────────────────────────────────────────────────────

    private void registerTaskDef(String name) {
        try {
            metadataService.registerTaskDef([new TaskDef(name: name, retryCount: 0, timeoutSeconds: 120)])
        } catch (ignored) {
            // Shared system task names may already be registered by another AI spec.
        }
    }

    private void unregister(String name) {
        try {
            metadataService.unregisterWorkflowDef(name, 1)
        } catch (ignored) {
            // The definition may already have been removed when setup failed.
        }
    }

    /**
     * The tool runs first; the chat task names it in {@code participants} so the mapper folds its
     * result into the conversation. The definition's own {@code messages} is the two-entry template
     * that a retry would otherwise resolve back over the assembled value.
     */
    private static WorkflowDef chatWorkflowDefinition() {
        WorkflowTask tool = new WorkflowTask(
                name: CUSTOMER_TOOL,
                taskReferenceName: CUSTOMER_TOOL,
                type: TaskType.SIMPLE.name(),
                inputParameters: [customer_id: '4471'])

        WorkflowTask chat = new WorkflowTask(
                name: TaskType.LLM_CHAT_COMPLETE.name(),
                taskReferenceName: CHAT_REF,
                type: TaskType.LLM_CHAT_COMPLETE.name(),
                inputParameters: [
                    llmProvider : 'test',
                    model       : 'test',
                    messages    : [
                        [role: 'system', message: SYSTEM_MESSAGE],
                        [role: 'user', message: USER_MESSAGE]
                    ],
                    tools       : [
                        [name: CUSTOMER_TOOL, type: 'SIMPLE', description: 'Look up a customer.']
                    ],
                    participants: [(CUSTOMER_TOOL): 'user']
                ])

        return new WorkflowDef(
                name: CHAT_WORKFLOW,
                version: 1,
                schemaVersion: 2,
                ownerEmail: 'test@conductor.test',
                tasks: [tool, chat])
    }

    private static WorkflowDef toolWorkflowDefinition() {
        WorkflowTask weather = new WorkflowTask(
                name: WEATHER_TOOL,
                taskReferenceName: WEATHER_TOOL,
                type: TaskType.SIMPLE.name(),
                inputParameters: [city: '${workflow.input.city}'])

        return new WorkflowDef(
                name: TOOL_WORKFLOW,
                version: 1,
                schemaVersion: 2,
                ownerEmail: 'test@conductor.test',
                tasks: [weather])
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkflowModel workflowModel(String workflowId, boolean includeTasks = false) {
        return executionDAOFacade.getWorkflowModel(workflowId, includeTasks)
    }

    private TaskModel awaitChatAttempt(String workflowId, int retryCount) {
        return awaitTaskAttempt(workflowId, CHAT_REF, retryCount)
    }

    /** Identity comes from retryCount, never from the input: the failed attempt still holds the
     * assembled conversation, so selecting "the task whose messages have history" would find it
     * every time and pass against a broken server. */
    private TaskModel awaitTaskAttempt(String workflowId, String refName, int retryCount) {
        TaskModel found
        conditions.eventually {
            found = workflowModel(workflowId, true).tasks.find {
                it.referenceTaskName == refName && it.retryCount == retryCount
            }
            assert found != null
            assert found.inputData
        }
        return found
    }

    /** The chat task is a system task and may already have failed on its own (no LLM provider is
     * configured here). Only fail it explicitly if it is still pending. */
    private void failIfPending(String workflowId, TaskModel task) {
        TaskModel current = workflowModel(workflowId, true).tasks.find { it.taskId == task.taskId }
        if (current == null || current.status.terminal) {
            return
        }
        workflowExecutor.updateTask(new TaskResult(
                taskId: task.taskId,
                workflowInstanceId: workflowId,
                status: TaskResult.Status.FAILED_WITH_TERMINAL_ERROR,
                reasonForIncompletion: 'intentional retry regression failure'))
    }

    @SuppressWarnings('unchecked')
    private static List<Map<String, Object>> messages(TaskModel task) {
        return (task.inputData.messages ?: []) as List<Map<String, Object>>
    }

    private static List<String> roles(List<Map<String, Object>> messages) {
        return messages*.role*.toString()
    }

    private static boolean hasToolExchange(List<Map<String, Object>> messages) {
        return messages.any { it.toolCalls || it.role?.toString()?.toLowerCase() in ['tool', 'tool_call'] }
    }
}
