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

import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicInteger

import org.conductoross.conductor.ai.decision.DecisionClient
import org.conductoross.conductor.ai.decision.DecisionQuestion
import org.conductoross.conductor.ai.decision.DecisionRequest
import org.conductoross.conductor.ai.decision.DecisionResult
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.dao.QueueDAO
import com.netflix.conductor.test.base.AbstractSpecification

/** Exercises decision evaluation and branch scheduling with the async system-task worker disabled. */
@TestPropertySource(properties = [
        'conductor.integrations.ai.enabled=true',
        'conductor.system-task-workers.enabled=false'
])
class DecisionSwitchSpec extends AbstractSpecification {

    private static final String WORKFLOW_NAME = 'decision_switch_workflow'

    @SpringBean
    DecisionClient decisionClient = Stub()

    @Autowired
    QueueDAO queueDAO

    private final AtomicInteger decisionCalls = new AtomicInteger()

    def setup() {
        decisionCalls.set(0)
        TaskDef switchTaskDefinition = new TaskDef('decision_switch')
        switchTaskDefinition.retryCount = 1
        switchTaskDefinition.retryDelaySeconds = 0
        metadataService.registerTaskDef([switchTaskDefinition])
        workflowTestUtil.registerWorkflows('decision_switch_integration_test.json')
    }

    def cleanup() {
        try {
            metadataService.unregisterWorkflowDef(WORKFLOW_NAME, 1)
        } catch (ignored) {
        }
        try {
            metadataService.unregisterTaskDef('decision_switch')
        } catch (ignored) {
        }
    }

    def "decision SWITCH evaluates and schedules its selected branch during workflow start"() {
        given:
        decisionClient.decide(_ as DecisionRequest) >> { DecisionRequest request ->
            assert request.model() == 'jev-1.13'
            assert request.questions().keySet() == ['route'] as Set
            decisionCalls.incrementAndGet()
            return decisionResult('billing')
        }

        when:
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'decision-switch', [:], null)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.RUNNING
        workflow.tasks.size() == 2
        Task decision = workflow.tasks.find { it.taskType == 'SWITCH' }
        decision.status == Task.Status.COMPLETED
        decision.outputData.selectedCase == 'billing'
        decision.outputData.model == 'typesafe/jev-test'
        decision.outputData.latencyMs == 17L
        decision.outputData.answers.route.choice == 'billing'
        decision.outputData.usage.inputTokens == 12L
        !queueDAO.containsMessage('SWITCH', decision.taskId)
        Task branch = workflow.tasks.find { it.referenceTaskName == 'billing_branch' }
        branch.status == Task.Status.SCHEDULED
        decisionCalls.get() == 1

        when:
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'decision.switch.integration.worker')

        then:
        conditions.eventually {
            Workflow completed = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert completed.status == Workflow.WorkflowStatus.COMPLETED
            assert completed.tasks.count { it.referenceTaskName == 'billing_branch' } == 1
            assert decisionCalls.get() == 1
        }
    }

    def "decision failure uses the built-in SWITCH failure path without task retries"() {
        given:
        decisionClient.decide(_ as DecisionRequest) >> {
            decisionCalls.incrementAndGet()
            throw new IllegalStateException('Decision HTTP status 503')
        }

        when:
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'decision-switch-failure', [:], null)
        Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)

        then:
        workflow.status == Workflow.WorkflowStatus.FAILED
        workflow.tasks.size() == 1
        Task decision = workflow.tasks[0]
        decision.status == Task.Status.FAILED
        decision.reasonForIncompletion == 'Decision HTTP status 503'
        !decision.retried
        !queueDAO.containsMessage('SWITCH', decision.taskId)
        decisionCalls.get() == 1
    }

    private static DecisionResult decisionResult(String choice) {
        return new DecisionResult(
                'typesafe',
                'typesafe/jev-test',
                [route: new DecisionResult.Answer(
                        DecisionQuestion.Type.CHOICE, choice, null, null, 0.99d)],
                new DecisionResult.Usage(12L, 3L, new BigDecimal('0.00004'), 'USD'),
                17L,
                'decision-test-1')
    }
}
