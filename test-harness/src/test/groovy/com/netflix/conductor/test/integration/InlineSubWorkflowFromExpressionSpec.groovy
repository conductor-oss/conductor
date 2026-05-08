/*
 * Copyright 2025 Conductor Authors.
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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams
import com.netflix.conductor.common.metadata.workflow.WorkflowDef
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.execution.tasks.SubWorkflow
import com.netflix.conductor.test.base.AbstractSpecification

import static com.netflix.conductor.test.util.WorkflowTestUtil.verifyPolledAndAcknowledgedTask

/**
 * End-to-end test for SubWorkflowTaskMapper's ability to resolve a String expression
 * in SubWorkflowParams.workflowDefinition at runtime.
 *
 * Scenario
 * --------
 * A parent workflow first runs a SIMPLE task (wf_builder) whose output contains a
 * complete WorkflowDef Map.  The SUB_WORKFLOW task that follows has:
 *
 *   subWorkflowParam.workflowDefinition = "${wf_builder.output.result}"   // String expression
 *
 * SubWorkflowTaskMapper resolves the expression via getTaskInputV2, converting the
 * String to the actual Map.  SubWorkflow.start() then converts that Map to a
 * WorkflowDef via ObjectMapper and starts the inline sub-workflow — with no prior
 * HTTP registration required.
 *
 * Complex sub-workflow structure (exercises all four requirements)
 * ---------------------------------------------------------------
 * DO_WHILE (2 iterations, parameterised by workflow.input.iterations):
 *   - INLINE  (compute):    JavaScript — product = iteration × threshold
 *   - SWITCH  (route):      JavaScript — product > threshold?
 *       true branch  → SIMPLE integration_task_1 ("high" path, needs worker)
 *       default      → INLINE low_result (auto-executes)
 * INLINE (final_result):   JavaScript summary after the loop — auto-executes
 *
 * Parameter mappings are used throughout: workflow.input → DO_WHILE → compute →
 * SWITCH → branch tasks; sub-workflow outputParameters flow back to the parent.
 *
 * With iterations=2, threshold=5
 * --------------------------------
 * Iteration 1: product = 1×5 = 5,  5 > 5 = false  → low_result  (auto)
 * Iteration 2: product = 2×5 = 10, 10 > 5 = true   → integration_task_1 (polled)
 * After loop:  final_result produces {loopsDone: 2, allDone: true}
 */
class InlineSubWorkflowFromExpressionSpec extends AbstractSpecification {

    @Autowired
    SubWorkflow subWorkflowTask

    def "Sub-workflow definition from a String expression runs a complex DO_WHILE with SWITCH, INLINE, and SIMPLE tasks"() {
        given: "A complex sub-workflow definition built as a runtime Map (DO_WHILE + SWITCH + INLINE + SIMPLE)"
        // This Map is exactly what the wf_builder SIMPLE task will return as its output.
        // SubWorkflow.start() converts it to a WorkflowDef via ObjectMapper.
        def subWfDef = buildComplexSubWorkflowDef()

        and: "A parent workflow whose SUB_WORKFLOW task uses a String expression for workflowDefinition"
        def parentWfDef = buildParentWorkflowDef()

        when: "The parent workflow is started with iterations=2 and threshold=5"
        def workflowId = workflowExecutor.startWorkflow(
                startWorkflowInput(parentWfDef, 'inline-subwf-expr-test', [iterations: 2, threshold: 5]))

        then: "The wf_builder task (integration_task_1) is SCHEDULED"
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
            tasks.size() == 1
            tasks[0].taskType == 'integration_task_1'
            tasks[0].referenceTaskName == 'wf_builder'
            tasks[0].status == Task.Status.SCHEDULED
        }

        when: "wf_builder is polled and completed — returns the complex sub-workflow definition"
        def pollBuilder = workflowTestUtil.pollAndCompleteTask(
                'integration_task_1', 'test.builder.worker', [result: subWfDef])

        then: "wf_builder completed and acknowledged"
        verifyPolledAndAcknowledgedTask(pollBuilder)

        and: "The SUB_WORKFLOW task starts (expression resolved → inline WorkflowDef created)"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
                tasks.size() == 2
                tasks[1].taskType == 'SUB_WORKFLOW'
                tasks[1].status == Task.Status.IN_PROGRESS
            }
        }

        when: "The sub-workflow ID is retrieved from the SUB_WORKFLOW task"
        def subWorkflowId = workflowExecutionService.getExecutionStatus(workflowId, true)
                .tasks[1].subWorkflowId

        then: "The sub-workflow is RUNNING — the inline WorkflowDef was resolved and started"
        with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
            status == Workflow.WorkflowStatus.RUNNING
        }

        // Iteration 1 (product=5, 5>5=false) runs entirely via system tasks (INLINE + SWITCH + INLINE)
        // and advances automatically.  Iteration 2 (product=10, 10>5=true) schedules the SIMPLE task.
        and: "integration_task_1 is SCHEDULED for the high-value path in iteration 2 (product > threshold)"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
                tasks.any { t ->
                    t.taskType == 'integration_task_1' &&
                            t.status == Task.Status.SCHEDULED &&
                            t.inputData['category'] == 'high'
                }
            }
        }

        when: "The integration_task_1 for the high-value path (iteration 2) is polled and completed"
        def pollHighTask = workflowTestUtil.pollAndCompleteTask(
                'integration_task_1', 'test.high.worker', [label: 'high', done: true])

        then: "integration_task_1 completed and acknowledged"
        verifyPolledAndAcknowledgedTask(pollHighTask)

        // After iteration 2 completes: condition 2 < 2 = false → DO_WHILE exits.
        // final_result INLINE then auto-executes and produces {loopsDone: 2, allDone: true}.
        and: "The sub-workflow COMPLETES with the expected loop summary output"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(subWorkflowId, true)) {
                status == Workflow.WorkflowStatus.COMPLETED
                // outputParameters mapped from final_result INLINE output
                output['loopsDone'] == 2
                output['allDone'] == true
            }
        }

        and: "The parent workflow COMPLETES — SUB_WORKFLOW task succeeded"
        conditions.eventually {
            with(workflowExecutionService.getExecutionStatus(workflowId, true)) {
                status == Workflow.WorkflowStatus.COMPLETED
                tasks[1].taskType == 'SUB_WORKFLOW'
                tasks[1].status == Task.Status.COMPLETED
            }
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds the parent workflow definition programmatically.
     *
     * Task 1 — wf_builder (SIMPLE, integration_task_1):
     *   Worker returns the complex sub-workflow definition as output["result"].
     *
     * Task 2 — exec (SUB_WORKFLOW):
     *   subWorkflowParam.workflowDefinition is set to the String expression
     *   "${wf_builder.output.result}".  SubWorkflowTaskMapper resolves this
     *   expression at runtime to the actual Map and injects it as the inline
     *   sub-workflow definition.
     */
    private static WorkflowDef buildParentWorkflowDef() {
        // Task 1: SIMPLE task that produces the sub-workflow definition
        def builderTask = new WorkflowTask()
        builderTask.name = 'integration_task_1'
        builderTask.taskReferenceName = 'wf_builder'
        builderTask.type = 'SIMPLE'
        builderTask.inputParameters = [
                tp1: '${workflow.input.threshold}',
                tp2: '${workflow.input.iterations}'
        ]

        // Task 2: SUB_WORKFLOW whose workflowDefinition is a String expression
        def subWfTask = new WorkflowTask()
        subWfTask.name = 'exec_plan'
        subWfTask.taskReferenceName = 'exec'
        subWfTask.type = 'SUB_WORKFLOW'
        // These inputParameters become the sub-workflow's workflow.input
        subWfTask.inputParameters = [
                threshold : '${workflow.input.threshold}',
                iterations: '${workflow.input.iterations}'
        ]
        def subParams = new SubWorkflowParams()
        subParams.name = 'complex_dynamic_plan_wf'
        subParams.version = 1
        // String expression — resolved by the fixed SubWorkflowTaskMapper
        subParams.workflowDefinition = '${wf_builder.output.result}'
        subWfTask.subWorkflowParam = subParams

        def wfDef = new WorkflowDef()
        wfDef.name = 'test_inline_subwf_expr_parent_wf'
        wfDef.version = 1
        wfDef.schemaVersion = 2
        wfDef.ownerEmail = 'test@harness.com'
        wfDef.tasks = [builderTask, subWfTask]
        wfDef.inputParameters = ['iterations', 'threshold']
        wfDef.outputParameters = [
                loopsDone: '${exec.output.loopsDone}',
                allDone  : '${exec.output.allDone}'
        ]
        return wfDef
    }

    /**
     * Builds the complex sub-workflow definition as a plain Map.
     *
     * This Map is returned as the output of the wf_builder SIMPLE task.
     * SubWorkflow.start() receives it via inputData["subWorkflowDefinition"] and
     * converts it to a WorkflowDef via ObjectMapper.convertValue().
     *
     * Structure
     * ---------
     * DO_WHILE (do_loop):
     *   loopCondition: $.do_loop['iteration'] < $.iters   (parametrised by input)
     *   loopOver:
     *     INLINE  compute    — product = iteration × threshold   (JavaScript)
     *     SWITCH  route      — $.product > $.threshold           (JavaScript)
     *       case "true" → SIMPLE integration_task_1 ref=high_task  (needs worker)
     *       default     → INLINE low_result                         (auto)
     * INLINE  final_result   — {loopsDone: iteration, allDone: true}
     *
     * inputParameters  : ["iterations", "threshold"]
     * outputParameters : loopsDone, allDone  (mapped from final_result output)
     */
    private static Map<String, Object> buildComplexSubWorkflowDef() {
        // INLINE: compute product = iteration * threshold
        def computeTask = [
                name             : 'compute',
                taskReferenceName: 'compute',
                type             : 'INLINE',
                inputParameters  : [
                        evaluatorType: 'javascript',
                        expression   : 'function f() { return $.iteration * $.threshold; } f();',
                        iteration    : '${do_loop.output.iteration}',
                        threshold    : '${workflow.input.threshold}'
                ]
        ]

        // SIMPLE: executed only when product > threshold (high-value branch)
        def highTask = [
                name             : 'integration_task_1',
                taskReferenceName: 'high_task',
                type             : 'SIMPLE',
                inputParameters  : [
                        product : '${compute.output.result}',
                        category: 'high'
                ]
        ]

        // INLINE: executed when product <= threshold (low-value branch, auto-completes)
        def lowResultTask = [
                name             : 'low_result',
                taskReferenceName: 'low_result',
                type             : 'INLINE',
                inputParameters  : [
                        evaluatorType: 'javascript',
                        expression   : 'function f() { return {label: "low", product: $.product}; } f();',
                        product      : '${compute.output.result}'
                ]
        ]

        // SWITCH: routes on product > threshold using JavaScript evaluator
        def routeTask = [
                name             : 'route',
                taskReferenceName: 'route',
                type             : 'SWITCH',
                evaluatorType    : 'javascript',
                expression       : '$.product > $.threshold',
                inputParameters  : [
                        product  : '${compute.output.result}',
                        threshold: '${workflow.input.threshold}'
                ],
                decisionCases    : [
                        'true': [highTask]
                ],
                defaultCase      : [lowResultTask]
        ]

        // DO_WHILE: loops $.iters times; iters and threshold come from sub-workflow input
        def doLoopTask = [
                name             : 'do_loop',
                taskReferenceName: 'do_loop',
                type             : 'DO_WHILE',
                inputParameters  : [
                        iters    : '${workflow.input.iterations}',
                        threshold: '${workflow.input.threshold}'
                ],
                loopCondition    : '''$.do_loop['iteration'] < $.iters''',
                loopOver         : [computeTask, routeTask]
        ]

        // INLINE: summarises results after DO_WHILE exits
        def finalResultTask = [
                name             : 'final_result',
                taskReferenceName: 'final_result',
                type             : 'INLINE',
                inputParameters  : [
                        evaluatorType: 'javascript',
                        expression   : 'function f() { return {loopsDone: $.loopsDone, allDone: true}; } f();',
                        loopsDone    : '${do_loop.output.iteration}'
                ]
        ]

        return [
                name            : 'complex_dynamic_plan_wf',
                version         : 1,
                schemaVersion   : 2,
                tasks           : [doLoopTask, finalResultTask],
                inputParameters : ['iterations', 'threshold'],
                outputParameters: [
                        loopsDone: '${final_result.output.result.loopsDone}',
                        allDone  : '${final_result.output.result.allDone}'
                ],
                timeoutPolicy   : 'ALERT_ONLY',
                timeoutSeconds  : 0
        ]
    }
}
