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
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry
import com.netflix.conductor.model.TaskModel
import com.netflix.conductor.model.WorkflowModel
import com.netflix.conductor.test.base.AbstractSpecification

/** Regression coverage for rematerializing workflow-derived LLM chat input when it is executed. */
class LlmChatRetrySpec extends AbstractSpecification {

    private static final String WORKFLOW_NAME = 'retry_llm_chat_input'
    private static final String ADD_TASK = 'add'
    private static final String CHAT_TASK = 'chat'
    private static final String DEFINITION_MESSAGE = 'Use the preceding calculation.'

    @Autowired
    ExecutionDAOFacade executionDAOFacade

    @Autowired
    SystemTaskRegistry systemTaskRegistry

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

    def "LLM chat retry enriches mapper input when the task is picked up"() {
        when: "A deterministic calculation precedes an LLM chat task"
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'retry-llm-chat-input', [:], null)

        TaskModel initialChat = null
        WorkflowModel initialWorkflow = null
        conditions.eventually {
            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId, true)
            initialWorkflow = workflow
            initialChat = workflow.tasks.find {
                it.referenceTaskName == CHAT_TASK && it.status == TaskModel.Status.SCHEDULED
            }
            assert initialChat != null
        }

        then: "The chat mapper enriches the definition message with the calculation result"
        messageTexts(initialChat) == [DEFINITION_MESSAGE, '3']
        definitionMessageTexts(initialWorkflow) == [DEFINITION_MESSAGE]

        when: "The queued chat task fails without invoking an LLM provider"
        TaskResult failed = new TaskResult(
                taskId: initialChat.taskId,
                workflowInstanceId: workflowId,
                status: TaskResult.Status.FAILED_WITH_TERMINAL_ERROR,
                reasonForIncompletion: 'intentional retry regression failure')
        workflowExecutor.updateTask(failed)

        conditions.eventually {
            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId, true)
            assert workflow.status == WorkflowModel.Status.FAILED
        }

        and: "The failed workflow is retried"
        workflowExecutor.retry(workflowId, false)

        then: "The queued retry contains only the workflow-definition input"
        TaskModel retryChat = null
        WorkflowModel retryWorkflow = null
        conditions.eventually {
            WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId, true)
            retryWorkflow = workflow
            retryChat = workflow.tasks.find {
                it.referenceTaskName == CHAT_TASK && it.retryCount == 1
            }
            assert retryChat != null
        }
        messageTexts(retryChat) == [DEFINITION_MESSAGE]
        definitionMessageTexts(retryWorkflow) == [DEFINITION_MESSAGE]

        when: "The retry is picked up by its annotated system task"
        systemTaskRegistry.get(TaskType.LLM_CHAT_COMPLETE.name()).execute(
                retryWorkflow, retryChat, workflowExecutor)

        then: "The same chat mapper rematerializes the workflow-derived history before execution"
        messageTexts(retryChat) == [DEFINITION_MESSAGE, '3']
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

    private static List<String> definitionMessageTexts(WorkflowModel workflow) {
        WorkflowTask chat = workflow.workflowDefinition.tasks.find {
            it.taskReferenceName == CHAT_TASK
        }
        return (chat.inputParameters.messages as List<Map<String, Object>>)*.message
    }
}
