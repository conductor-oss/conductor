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
import org.springframework.test.context.TestPropertySource

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.Switch
import com.netflix.conductor.test.base.AbstractSystemTaskWorkerSpecification

/**
 * Exercises the complete deferred SWITCH lifecycle with the real persistence, queue, system-task
 * worker and decider. Only the external decision provider is mocked.
 */
@TestPropertySource(properties = ['conductor.integrations.ai.enabled=true'])
class DeferredSwitchSystemTaskSpec extends AbstractSystemTaskWorkerSpecification {

    private static final String WORKFLOW_NAME = 'deferred_switch_system_task'

    @SpringBean
    DecisionClient decisionClient = Mock()

    private final AtomicInteger decisionCalls = new AtomicInteger()

    def setup() {
        decisionCalls.set(0)
        TaskDef switchTaskDefinition = new TaskDef('decision_switch')
        switchTaskDefinition.retryCount = 1
        switchTaskDefinition.retryDelaySeconds = 0
        switchTaskDefinition.responseTimeoutSeconds = 30
        metadataService.registerTaskDef([switchTaskDefinition])
        workflowTestUtil.registerWorkflows('deferred_switch_system_task_integration_test.json')
        decisionClient.decide(_ as DecisionRequest) >> { DecisionRequest request ->
            assert request.model() == 'jev-1.13'
            assert request.questions().keySet() == ['route'] as Set
            if (decisionCalls.incrementAndGet() == 1) {
                throw new IllegalStateException('Decision HTTP status 503')
            }
            return decisionResult('billing')
        }
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

    def "deferred SWITCH survives the queue hop, retries, and schedules its selected branch"() {
        when: "a workflow starts with a decision-backed SWITCH"
        String workflowId = startWorkflow(WORKFLOW_NAME, 1, 'deferred-switch', [:], null)

        then: "the real system-task worker retries the transient failure and the decider expands the branch"
        conditions.eventually {
            Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)
            List<Task> switchAttempts = workflow.tasks
                    .findAll { it.taskType == 'SWITCH' }
                    .sort { it.retryCount }

            assert switchAttempts.size() == 2
            assert switchAttempts[0].status == Task.Status.FAILED
            assert switchAttempts[0].retried
            assert switchAttempts[1].status == Task.Status.COMPLETED
            assert switchAttempts[1].inputData[Switch.DEFERRED_EVALUATOR] == true
            assert switchAttempts[1].outputData.selectedCase == 'billing'
            assert switchAttempts[1].outputData.model == 'typesafe/jev-test'
            assert switchAttempts[1].outputData.latencyMs == 17L

            Task branch = workflow.tasks.find { it.referenceTaskName == 'billing_branch' }
            assert branch != null
            assert branch.status == Task.Status.SCHEDULED
            assert decisionCalls.get() == 2
        }

        when: "the selected branch finishes"
        workflowTestUtil.pollAndCompleteTask(
                'integration_task_1', 'deferred.switch.integration.worker')

        then: "the workflow completes without scheduling another branch"
        conditions.eventually {
            Workflow workflow = workflowExecutionService.getExecutionStatus(workflowId, true)
            assert workflow.status == Workflow.WorkflowStatus.COMPLETED
            assert workflow.tasks.count { it.referenceTaskName == 'billing_branch' } == 1
            assert decisionCalls.get() == 2
        }
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
