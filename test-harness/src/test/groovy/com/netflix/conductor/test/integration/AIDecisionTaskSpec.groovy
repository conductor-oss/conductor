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

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

import org.conductoross.conductor.ai.decision.DecisionConfiguration
import org.conductoross.conductor.ai.decision.DecisionTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

import com.sun.net.httpserver.HttpServer

/** Runs an AI_DECISION task through the system task executor and routes on it with a SWITCH. */
@TestPropertySource(properties = [
        'conductor.integrations.ai.enabled=true',
        'conductor.system-task-workers.enabled=false'
])
class AIDecisionTaskSpec extends AbstractSpecification {

    private static final String WORKFLOW_NAME = 'ai_decision_task_workflow'
    private static final String INLINE_RETRY_WORKFLOW = 'ai_decision_inline_retry_workflow'

    private static final String DECISION_RESPONSE = '''
        {"model":"typesafe/jev-test","id":"decision-test-1",
         "answers":{"route":{"type":"choice","choice":"billing","confidence":0.99}},
         "usage":{"input_tokens":12,"output_tokens":3,"cost":0.00004}}
        '''

    @Autowired
    DecisionTask decisionTask

    @Autowired
    DecisionConfiguration decisionConfiguration

    @Autowired
    QueueDAO queueDAO

    private HttpServer provider
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>()
    private final Queue<String> authorizations = new ConcurrentLinkedQueue<>()

    def setup() {
        provider = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        provider.createContext('/v1/systemone') { exchange ->
            authorizations.add(exchange.requestHeaders.getFirst('Authorization'))
            int status = statuses.poll() ?: 200
            byte[] body = (status == 200 ? DECISION_RESPONSE : 'provider error')
                    .getBytes(StandardCharsets.UTF_8)
            exchange.responseHeaders.add('Content-Type', 'application/json')
            exchange.sendResponseHeaders(status, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        }
        provider.start()
        decisionConfiguration.apiKey = 'test-key'
        decisionConfiguration.endpoint = "http://127.0.0.1:${provider.address.port}/v1/systemone"
        workflowTestUtil.registerWorkflows(
                'ai_decision_task_integration_test.json',
                'ai_decision_dynamic_fork_integration_test.json')
    }

    def cleanup() {
        provider?.stop(0)
        try {
            metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1)
        } catch (ignored) {
        }
        try {
            metadataService.unregisterWorkflowDef('ai_decision_dynamic_fork_workflow', 1)
        } catch (ignored) {
        }
        try {
            metadataService.unregisterWorkflowDef(INLINE_RETRY_WORKFLOW, 1)
        } catch (ignored) {
        }
        try {
            metadataService.unregisterTaskDef('decision_task')
        } catch (ignored) {
        }
    }

    def "the decision runs as an async task and a SWITCH routes on its selected case"() {
        expect: 'like an HTTP task, it needs no registered task definition'
        metadataService.getTaskDefs().every { it.name != 'decision_task' }

        when: 'the workflow starts'
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'decision-task', [:], null)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: 'the decision is queued for the system task worker, not evaluated in the decider'
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.size() == 1
        Task decision = workflow.tasks[0]
        decision.taskType == DecisionTask.TASK_TYPE
        decision.status == Task.Status.SCHEDULED
        queueDAO.containsMessage(DecisionTask.TASK_TYPE, decision.taskId)
        authorizations.isEmpty()

        when: 'the system task worker executes it'
        asyncSystemTaskExecutor.execute(decisionTask, decision.taskId)
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then: 'the SWITCH routes on selectedCase'
        Task completed = workflow.tasks.find { it.referenceTaskName == 'decide' }
        completed.status == Task.Status.COMPLETED
        completed.outputData.selectedCase == 'billing'
        completed.outputData.answers.route.choice == 'billing'
        completed.outputData.model == 'typesafe/jev-test'
        workflow.tasks.find { it.referenceTaskName == 'route' }.status == Task.Status.COMPLETED
        workflow.tasks.find { it.referenceTaskName == 'billing_branch' }.status == Task.Status.SCHEDULED
        authorizations.toList() == ['Bearer test-key']

        when:
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'decision.task.integration.worker')

        then:
        conditions.eventually {
            assert workflowExecutionService.getExecutionStatus(workflowId, false).status ==
                    Workflow.WorkflowStatus.COMPLETED
        }
    }

    def "a dynamically forked decision, as agentspan emits for a decision tool, is scheduled"() {
        given:
        Map input = [
                dynamicTasks     : [[name: 'AI_DECISION', taskReferenceName: 'tool_call', type: 'AI_DECISION']],
                dynamicTasksInput: [tool_call: [
                        model    : 'jev-1.13',
                        state    : 'The customer was charged twice.',
                        question : 'route',
                        questions: [route: [
                                type        : 'choice',
                                instructions: 'Choose a team.',
                                choices     : [billing: 'Billing', technical: 'Technical']]]]]]

        expect: 'no task definition is registered for the forked task'
        metadataService.getTaskDefs().every { it.name != 'AI_DECISION' }

        when:
        String workflowId = startWorkflow('ai_decision_dynamic_fork_workflow', 1, 'decision-fork', input, null)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.RUNNING
        Task forked = workflow.tasks.find { it.referenceTaskName == 'tool_call' }
        forked.taskType == DecisionTask.TASK_TYPE
        forked.status == Task.Status.SCHEDULED
        queueDAO.containsMessage(DecisionTask.TASK_TYPE, forked.taskId)

        when:
        asyncSystemTaskExecutor.execute(decisionTask, forked.taskId)

        then:
        workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks.find { it.referenceTaskName == 'tool_call' }.outputData.selectedCase == 'billing'
    }

    def "a provider server error is retried with the task definition's retry policy"() {
        given:
        TaskDef decisionTaskDefinition = new TaskDef('decision_task')
        decisionTaskDefinition.retryCount = 1
        decisionTaskDefinition.retryDelaySeconds = 0
        metadataService.registerTaskDef([decisionTaskDefinition])
        statuses.add(500)

        when:
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'decision-task-retry', [:], null)
        Task first = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]
        asyncSystemTaskExecutor.execute(decisionTask, first.taskId)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks[0].status == Task.Status.FAILED
        workflow.tasks[0].reasonForIncompletion == 'Decision HTTP status 500'
        Task retry = workflow.tasks.find { it.referenceTaskName == 'decide' && it.retryCount == 1 }
        retry.status == Task.Status.SCHEDULED

        when:
        asyncSystemTaskExecutor.execute(decisionTask, retry.taskId)
        workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.tasks.find { it.taskId == retry.taskId }.status == Task.Status.COMPLETED
        workflow.tasks.find { it.referenceTaskName == 'billing_branch' }.status == Task.Status.SCHEDULED
        authorizations.size() == 2
    }

    def "an inline task definition retries a server error, as agentspan emits for routers"() {
        given: 'the decision carries its retry policy and no definition is registered'
        WorkflowDef inline = metadataService.getWorkflowDef(WORKFLOW_NAME, 1)
        inline.name = INLINE_RETRY_WORKFLOW
        inline.tasks[0].taskDefinition = inlineRetryPolicy()
        metadataService.registerWorkflowDef(inline)
        statuses.add(500)

        expect:
        metadataService.getTaskDefs().every { it.name != 'decision_task' }

        when:
        String workflowId = startWorkflow(INLINE_RETRY_WORKFLOW, 1, 'decision-inline-retry', [:], null)
        Task first = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]
        asyncSystemTaskExecutor.execute(decisionTask, first.taskId)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks[0].status == Task.Status.FAILED
        Task retry = workflow.tasks.find { it.referenceTaskName == 'decide' && it.retryCount == 1 }
        retry.status == Task.Status.SCHEDULED

        when:
        asyncSystemTaskExecutor.execute(decisionTask, retry.taskId)

        then:
        workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks.find { it.taskId == retry.taskId }.status == Task.Status.COMPLETED
        authorizations.size() == 2
    }

    def "an inline task definition retries a forked decision, as agentspan emits for tools"() {
        given:
        statuses.add(503)
        Map input = [
                dynamicTasks     : [[name          : 'AI_DECISION', taskReferenceName: 'tool_call', type: 'AI_DECISION',
                                     taskDefinition: [name: 'AI_DECISION', retryCount: 1, retryDelaySeconds: 0]]],
                dynamicTasksInput: [tool_call: [
                        model    : 'jev-1.13',
                        state    : 'The customer was charged twice.',
                        question : 'route',
                        questions: [route: [
                                type        : 'choice',
                                instructions: 'Choose a team.',
                                choices     : [billing: 'Billing', technical: 'Technical']]]]]]

        expect:
        metadataService.getTaskDefs().every { it.name != 'AI_DECISION' }

        when:
        String workflowId = startWorkflow('ai_decision_dynamic_fork_workflow', 1, 'decision-fork-retry', input, null)
        Task forked = workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks.find { it.referenceTaskName == 'tool_call' }
        asyncSystemTaskExecutor.execute(decisionTask, forked.taskId)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.find { it.taskId == forked.taskId }.status == Task.Status.FAILED
        Task retry = workflow.tasks.find { it.referenceTaskName == 'tool_call' && it.retryCount == 1 }
        retry.status == Task.Status.SCHEDULED

        when:
        asyncSystemTaskExecutor.execute(decisionTask, retry.taskId)

        then:
        workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks.find { it.taskId == retry.taskId }.outputData.selectedCase == 'billing'
        authorizations.size() == 2
    }

    private static TaskDef inlineRetryPolicy() {
        TaskDef policy = new TaskDef('decision_task')
        policy.retryCount = 1
        policy.retryDelaySeconds = 0
        return policy
    }

    def "a rejected request fails the workflow without retrying"() {
        given:
        statuses.add(400)

        when:
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'decision-task-rejected', [:], null)
        Task decision = workflowExecutionService.getExecutionStatus(workflowId, true).tasks[0]
        asyncSystemTaskExecutor.execute(decisionTask, decision.taskId)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.FAILED
        workflow.tasks.size() == 1
        workflow.tasks[0].status == Task.Status.FAILED_WITH_TERMINAL_ERROR
        workflow.tasks[0].reasonForIncompletion == 'Decision HTTP status 400'
        authorizations.size() == 1
    }
}
