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

import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.tasks.TaskType
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.core.dal.ExecutionDAOFacade
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel
import com.netflix.conductor.test.base.AbstractSpecification

/** Regression coverage for rematerializing workflow-derived LLM chat input when it is retried. */
class LlmChatRetrySpec extends AbstractSpecification {

    private static final String WORKFLOW_NAME = 'retry_llm_chat_input'
    private static final String ADD_TASK = 'add'
    private static final String CHAT_TASK = 'chat'
    private static final String DEFINITION_MESSAGE = 'Use the preceding calculation.'

    @Autowired
    ExecutionDAOFacade executionDAOFacade

    def setup() {
        registerChatTaskDefinition()
        metadataService.registerWorkflowDef(workflowDefinition())
    }

    def cleanup() {
        try {
            metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1)
        } catch (ignored) {
            // The definition may already have been removed when setup failed.
        }
    }

    def "LLM chat retry preserves the originally assembled input"() {
        given: "A chat task enriched from a preceding calculation"
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'retry-llm-chat-input', [:], null)
        TaskModel initialChat = awaitChatAttempt(workflowId, 0)
        assert messageTexts(initialChat) == [DEFINITION_MESSAGE, '3']

        when: "The participant output changes after the chat input was assembled"
        TaskModel addTask = executionDAOFacade.getWorkflowModel(workflowId, true).getTaskByRefName(ADD_TASK)
        addTask.outputData.result = 4
        executionDAOFacade.updateTask(addTask)

        and: "The chat task fails and the workflow is retried"
        workflowExecutor.updateTask(new TaskResult(
                taskId: initialChat.taskId,
                workflowInstanceId: workflowId,
                status: TaskResult.Status.FAILED_WITH_TERMINAL_ERROR,
                reasonForIncompletion: 'intentional retry regression failure'))

        conditions.eventually {
            assert executionDAOFacade.getWorkflowModel(workflowId, false).status == WorkflowModel.Status.FAILED
        }
        workflowExecutor.retry(workflowId, false)

        then: "The queued retry keeps the conversation assembled for the original attempt"
        messageTexts(awaitChatAttempt(workflowId, 1)) == [DEFINITION_MESSAGE, '3']
    }

    private void registerChatTaskDefinition() {
        TaskDef taskDef = new TaskDef(
                name: TaskType.LLM_CHAT_COMPLETE.name(),
                retryCount: 0,
                timeoutSeconds: 120)
        try {
            metadataService.registerTaskDef([taskDef])
        } catch (ignored) {
            // Other AI integration tests may already have registered the shared system task name.
        }
    }

    private static WorkflowDef workflowDefinition() {
        WorkflowTask add = new WorkflowTask(
                name: 'inline',
                taskReferenceName: ADD_TASK,
                type: TaskType.INLINE.name(),
                inputParameters: [
                    a: 1,
                    b: 2,
                    evaluatorType: 'graaljs',
                    expression: '$.a + $.b'
                ])

        WorkflowTask chat = new WorkflowTask(
                name: TaskType.LLM_CHAT_COMPLETE.name(),
                taskReferenceName: CHAT_TASK,
                type: TaskType.LLM_CHAT_COMPLETE.name(),
                inputParameters: [
                    llmProvider: 'unused',
                    model: 'unused',
                    messages: [[role: 'user', message: DEFINITION_MESSAGE]],
                    participants: [(ADD_TASK): 'user']
                ])

        return new WorkflowDef(
                name: WORKFLOW_NAME,
                version: 1,
                schemaVersion: 2,
                ownerEmail: 'test@conductor.test',
                tasks: [add, chat])
    }

    private static List<String> messageTexts(TaskModel task) {
        return (task.inputData.messages as List<Map<String, Object>>)*.message
    }

    private TaskModel awaitChatAttempt(String workflowId, int retryCount) {
        TaskModel chat
        conditions.eventually {
            chat = executionDAOFacade.getWorkflowModel(workflowId, true).tasks.find {
                it.referenceTaskName == CHAT_TASK && it.retryCount == retryCount
            }
            assert chat != null
        }
        return chat
    }
}
