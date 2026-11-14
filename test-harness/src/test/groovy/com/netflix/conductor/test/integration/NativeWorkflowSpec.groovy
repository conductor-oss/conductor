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

import com.netflix.conductor.common.metadata.tasks.Task
import com.netflix.conductor.common.metadata.tasks.TaskContinuation
import com.netflix.conductor.common.metadata.tasks.TaskDef
import com.netflix.conductor.common.metadata.tasks.TaskResult
import com.netflix.conductor.common.metadata.workflow.WorkflowTask
import com.netflix.conductor.common.run.Workflow
import com.netflix.conductor.core.exception.ConflictException
import com.netflix.conductor.test.base.AbstractSpecification

/**
 * Plays the native workflow SDK against the server: polls the workflow's code type like any task
 * type and completes continuations through the ordinary task update, with hand-written fragments,
 * the way a worker holding workflow code would. No SDK is involved, so the server side of the
 * protocol is tested on its own.
 *
 * <p>The execution holds exactly the tasks the code called: a continuation is a marker on the
 * task the code paused on, not a task of its own.
 */
class NativeWorkflowSpec extends AbstractSpecification {

    static final String SKELETON = 'native_skeleton'
    static final String CODE_TYPE = 'native_skeleton.v1.code'

    def setup() {
        workflowTestUtil.registerWorkflows('native_skeleton_integration_test.json')
    }

    def "a linear native workflow grows its definition one continuation at a time and holds only the tasks the code called"() {
        given: 'a native workflow started with an input'
        def workflowId = startWorkflow(SKELETON, 1, 'native-linear', [mod: 5], null)

        when: 'the worker polls the code type'
        def bootstrap = workflowExecutionService.poll(CODE_TYPE, 'worker-a')

        then: 'it is handed the first continuation of the main lane, with the workflow input'
        bootstrap != null
        bootstrap.taskId == "${workflowId}|main"
        bootstrap.taskType == CODE_TYPE
        bootstrap.continuation.lane == 'main'
        bootstrap.continuation.n == 1
        bootstrap.continuation.state == TaskContinuation.LEASED

        when: 'the worker completes it with the first task the code called, marked as where the code paused'
        complete(bootstrap, 'worker-a', completion(bootstrap,
                [paused(simple('integration_task_1', [p: '${workflow.input.mod}']), 'main', 2)],
                [stage: 'classic']))

        then: 'the execution holds that one task, and nothing else'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.status == Workflow.WorkflowStatus.RUNNING
            refs(wf.workflowDefinition.tasks) == ['integration_task_1']
            wf.tasks.size() == 1
            wf.tasks[0].referenceTaskName == 'integration_task_1'
            wf.tasks[0].status == Task.Status.SCHEDULED
            wf.tasks[0].inputData.p == 5
            wf.tasks[0].continuation.lane == 'main'
            wf.tasks[0].continuation.n == 2
            wf.tasks[0].continuation.state == TaskContinuation.WAITING
            wf.variables.stage == 'classic'
            wf.nativeState.bootstrap.state == TaskContinuation.APPLIED
            wf.nativeState.bootstrap.step == 1
        }

        when: 'the task completes'
        def polled = workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker', [result: 'ok'])

        then: 'the finished task itself is handed back as the next continuation'
        workflowTestUtil.verifyPolledAndAcknowledgedTask(polled)
        def next = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        next != null
        next.referenceTaskName == 'integration_task_1'
        next.status == Task.Status.COMPLETED
        next.outputData.result == 'ok'
        next.continuation.queue == CODE_TYPE // the shared queue: any worker of the code
        next.continuation.n == 2
        next.continuation.state == TaskContinuation.LEASED

        when: 'the code returns'
        complete(next, 'worker-a', completion(next, [], [stage: 'done'], true, [answer: 42]))

        then: 'the workflow completes with the returned value as its output, still with one task'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.status == Workflow.WorkflowStatus.COMPLETED
            wf.output == [answer: 42]
            wf.variables.stage == 'done'
            refs(wf.workflowDefinition.tasks) == ['integration_task_1']
            wf.tasks.size() == 1
            wf.tasks[0].status == Task.Status.COMPLETED
            wf.tasks[0].continuation.state == TaskContinuation.APPLIED
            wf.tasks[0].continuation.done
        }
    }

    def "a fork branch grows independently of its sibling"() {
        given:
        def workflowId = startWorkflow(SKELETON, 1, 'native-fork', [:], null)
        def bootstrap = workflowExecutionService.poll(CODE_TYPE, 'worker-a')

        when: 'the main lane forks two lanes, each paused on its first task, and pauses on the join'
        def fork = new WorkflowTask(name: 'fork_1', taskReferenceName: 'fork_1', type: 'FORK_JOIN')
        fork.metadata.lane = 'main'
        fork.forkTasks = [
                [paused(simple('integration_task_1', [:], 'fork_1.0'), 'fork_1.0', 1)],
                [paused(simple('integration_task_2', [:], 'fork_1.1'), 'fork_1.1', 1)]
        ]
        def join = new WorkflowTask(name: 'join_1', taskReferenceName: 'join_1', type: 'JOIN', joinOn: [])
        join.metadata.lane = 'main'
        complete(bootstrap, 'worker-a', completion(bootstrap, [fork, paused(join, 'main', 2)], null))

        then: 'both branch tasks are scheduled'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.status == Workflow.WorkflowStatus.RUNNING
            refs(wf.workflowDefinition.tasks) == ['fork_1', 'join_1']
            wf.getTaskByRefName('integration_task_1').status == Task.Status.SCHEDULED
            wf.getTaskByRefName('integration_task_2').status == Task.Status.SCHEDULED
            wf.getTaskByRefName('join_1').status == Task.Status.IN_PROGRESS
        }

        when: 'only branch 0 completes its task'
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker', [:])

        then: 'branch 0 continuation is offered while branch 1 is still waiting'
        def branch0 = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        branch0 != null
        branch0.referenceTaskName == 'integration_task_1'
        branch0.continuation.lane == 'fork_1.0'
        workflowExecutionService.getExecutionStatus(workflowId, true).getTaskByRefName('integration_task_2').status == Task.Status.SCHEDULED

        when: 'branch 0 continues with another task'
        complete(branch0, 'worker-a', completion(branch0,
                [paused(simple('integration_task_3', [:], 'fork_1.0'), 'fork_1.0', 2)], null))

        then: 'the fragment lands inside branch 0 only, and its task runs before branch 1 has moved'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            def branches = wf.workflowDefinition.tasks[0].forkTasks
            refs(branches[0]) == ['integration_task_1', 'integration_task_3']
            refs(branches[1]) == ['integration_task_2']
            wf.getTaskByRefName('integration_task_3').status == Task.Status.SCHEDULED
            wf.getTaskByRefName('integration_task_2').status == Task.Status.SCHEDULED
            wf.getTaskByRefName('join_1').status == Task.Status.IN_PROGRESS
            wf.tasks.size() == 5 // fork, join, and the three tasks the code called
        }
    }

    def "a completion that does not match the current lease is refused"() {
        given: 'a workflow whose first continuation was handed to worker-a'
        def workflowId = startWorkflow(SKELETON, 1, 'native-stale', [:], null)
        def first = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        first != null

        when: 'a completion arrives for a lease the server never handed out'
        def stale = completion(first, [paused(simple('integration_task_1', [:]), 'main', 2)], [cancelled: true])
        stale.epoch = first.continuation.epoch + 1
        complete(first, 'worker-a', stale)

        then: 'it is refused and nothing was applied'
        thrown(ConflictException)
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.status == Workflow.WorkflowStatus.RUNNING
            wf.tasks.isEmpty()
            wf.workflowDefinition.tasks.isEmpty()
            !wf.variables.containsKey('cancelled')
        }

        when: 'the holder of the lease completes it'
        complete(first, 'worker-a', completion(first, [paused(simple('integration_task_1', [:]), 'main', 2)], null))

        then: 'it is applied'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            refs(wf.workflowDefinition.tasks) == ['integration_task_1']
        }

        when: 'the same completion is delivered again (a retried update after a lost response)'
        complete(first, 'worker-a', completion(first, [paused(simple('integration_task_1', [:]), 'main', 2)], null))

        then: 'it is refused: the continuation was already applied'
        thrown(ConflictException)
        workflowExecutionService.getExecutionStatus(workflowId, true).tasks.size() == 1
    }

    def "a continuation that fails runs again and leaves the task it paused on as its worker finished it"() {
        given: 'a workflow paused on a finished task, whose continuation a worker holds'
        def workflowId = startWorkflow(SKELETON, 1, 'native-failed-continuation', [:], null)
        def bootstrap = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        complete(bootstrap, 'worker-a', completion(bootstrap, [paused(simple('integration_task_1', [:]), 'main', 2)], null))
        workflowTestUtil.pollAndCompleteTask('integration_task_1', 'task1.worker', [result: 'ok'])
        def held = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        held != null

        when: 'the code fails to run, for a reason that has nothing to do with the task'
        def result = new TaskResult(held)
        result.status = TaskResult.Status.FAILED
        result.reasonForIncompletion = 'timeout reading the execution'
        result.workerId = 'worker-a'
        result.outputData = [protocol: 2, n: held.continuation.n, epoch: held.continuation.epoch]
        workflowExecutionService.updateTask(result)

        then: 'the task keeps its result and the continuation is offered again'
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.status == Workflow.WorkflowStatus.RUNNING
            wf.tasks.size() == 1
            wf.tasks[0].status == Task.Status.COMPLETED
            wf.tasks[0].outputData.result == 'ok'
            // offered again at once, at a new epoch, delivered after its backoff
            wf.tasks[0].continuation.state == TaskContinuation.LEASED
            wf.tasks[0].continuation.epoch == held.continuation.epoch + 1
            wf.tasks[0].continuation.deferred == 'worker reported FAILED'
        }
        workflowExecutionService.poll(CODE_TYPE, 'worker-b') == null // within the backoff

        when: 'the backoff has passed'
        Thread.sleep(1500)
        def again = workflowExecutionService.poll(CODE_TYPE, 'worker-b')

        then:
        again != null
        again.continuation.epoch == held.continuation.epoch + 1
    }

    def "a task whose name looks like a code type is an ordinary task"() {
        given: 'task definitions named like code types'
        metadataService.registerTaskDef(['plain.code', 'paused.code'].collect { new TaskDef(it, it, 'test@harness.com', 0, 0, 60) })

        and: 'a workflow that calls one without pausing on it and pauses on the other'
        def workflowId = startWorkflow(SKELETON, 1, 'native-code-suffix', [:], null)
        def bootstrap = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        complete(bootstrap, 'worker-a', completion(bootstrap,
                [simple('plain.code', [:]), paused(simple('paused.code', [:]), 'main', 2)], null))

        when: 'workers poll both task types'
        def first = workflowTestUtil.pollAndCompleteTask('plain.code', 'task.worker', [from: 'plain'])
        def second = workflowTestUtil.pollAndCompleteTask('paused.code', 'task.worker', [from: 'paused'])

        then: 'both are delivered and run, in order'
        workflowTestUtil.verifyPolledAndAcknowledgedTask(first)
        workflowTestUtil.verifyPolledAndAcknowledgedTask(second)
        with(workflowExecutionService.getExecutionStatus(workflowId, true)) { Workflow wf ->
            wf.tasks*.status == [Task.Status.COMPLETED, Task.Status.COMPLETED]
            wf.tasks*.outputData*.from == ['plain', 'paused']
        }

        and: 'the continuation of the one paused on still arrives on the code type'
        def next = workflowExecutionService.poll(CODE_TYPE, 'worker-a')
        next != null
        next.referenceTaskName == 'paused.code'
        next.continuation.state == TaskContinuation.LEASED
    }

    // ------------------------------------------------------------------ the SDK, in miniature

    private void complete(Task task, String workerId, Map<String, Object> output) {
        def result = new TaskResult(task)
        result.status = TaskResult.Status.COMPLETED
        result.workerId = workerId
        result.outputData = output
        workflowExecutionService.updateTask(result)
    }

    private static Map<String, Object> completion(Task task, List<WorkflowTask> fragment, Map<String, Object> variables,
                                                  boolean done = false, Map<String, Object> result = null) {
        def output = [protocol: 2, n: task.continuation.n, epoch: task.continuation.epoch,
                      fragment: fragment, done: done] as Map<String, Object>
        if (variables != null) {
            output.variables = variables
        }
        if (result != null) {
            output.result = result
        }
        return output
    }

    private static WorkflowTask simple(String name, Map<String, Object> input, String lane = 'main') {
        def task = new WorkflowTask(name: name, taskReferenceName: name, type: 'SIMPLE', inputParameters: input)
        task.optional = true
        task.metadata.lane = lane
        return task
    }

    /** Marks the entry as the task the code paused on: continuation {@code n} of {@code lane} follows it. */
    private static WorkflowTask paused(WorkflowTask task, String lane, int n) {
        task.metadata.continuation = [lane: lane, n: n]
        return task
    }

    private static List<String> refs(List<WorkflowTask> tasks) {
        tasks*.taskReferenceName
    }
}
