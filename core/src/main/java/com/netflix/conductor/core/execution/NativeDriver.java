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
package com.netflix.conductor.core.execution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.NativeWorkflows.LastMain;
import com.netflix.conductor.core.execution.tasks.Join;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import static com.netflix.conductor.core.utils.Utils.DECIDER_QUEUE;

import static org.conductoross.conductor.core.execution.ExecutorUtils.expedite;

/**
 * The driver of a native (code-defined) workflow: the code decides the next task, and the
 * definition of the execution has only the tasks the code called.
 */
@Component
public class NativeDriver implements WorkflowDriver {

    /** Tasks of a native workflow that are the code's structure, never a point to rerun from. */
    private static final Set<String> CODE_STRUCTURE =
            Set.of(TaskType.TASK_TYPE_SWITCH, TaskType.TASK_TYPE_FORK, TaskType.TASK_TYPE_JOIN);

    private final StandardDriver standard;
    private final NativeContinuationService nativeContinuationService;
    private final NativeRerunPlanner nativeRerunPlanner;
    private final MetadataDAO metadataDAO;
    private final MetadataMapperService metadataMapperService;
    private final ExecutionDAOFacade executionDAOFacade;
    private final QueueDAO queueDAO;
    private final SystemTaskRegistry systemTaskRegistry;

    public NativeDriver(
            StandardDriver standard,
            NativeContinuationService nativeContinuationService,
            NativeRerunPlanner nativeRerunPlanner,
            MetadataDAO metadataDAO,
            MetadataMapperService metadataMapperService,
            ExecutionDAOFacade executionDAOFacade,
            QueueDAO queueDAO,
            SystemTaskRegistry systemTaskRegistry) {
        this.standard = standard;
        this.nativeContinuationService = nativeContinuationService;
        this.nativeRerunPlanner = nativeRerunPlanner;
        this.metadataDAO = metadataDAO;
        this.metadataMapperService = metadataMapperService;
        this.executionDAOFacade = executionDAOFacade;
        this.queueDAO = queueDAO;
        this.systemTaskRegistry = systemTaskRegistry;
    }

    @Override
    public void initialize(WorkflowModel workflow) {
        nativeContinuationService.initialize(workflow);
    }

    @Override
    public List<TaskModel> startWithNoTasks() {
        return new LinkedList<>(); // the code's first continuation appends the first tasks
    }

    /**
     * Native workflows mark the tasks their code waits on optional, so a failure continues to the
     * code's next continuation. The code receives it as an exception naming how the task failed,
     * which the status no longer says.
     */
    @Override
    public void completeWithErrors(TaskModel task) {
        task.addOutput(NativeWorkflows.OUTPUT_FAILED_STATUS, task.getStatus().name());
        standard.completeWithErrors(task);
    }

    /**
     * The output of a native workflow is what its code returned, set when the main lane's final
     * continuation was applied; the last task's output is not it.
     */
    @Override
    public void updateOutput(WorkflowModel workflow, TaskModel task) {}

    /**
     * A native execution ends when its code returned (the main lane's last continuation is done)
     * and everything that continuation appended has run: tasks all being terminal says nothing
     * about the code, and the code returning says nothing about trailing tasks (an event fired
     * last, a switch whose case has no tasks).
     */
    @Override
    public boolean isComplete(WorkflowModel workflow, BooleanSupplier tasksComplete) {
        LastMain lastMain = NativeWorkflows.lastMain(workflow);
        TaskContinuation last = lastMain == null ? null : lastMain.marker();
        // the code threw (or its fragment was rejected): the run has a reason, and is ended
        // by the completion that recorded it, not completed
        if (last == null
                || !last.isApplied()
                || !last.isDone()
                || workflow.getReasonForIncompletion() != null) {
            return false;
        }
        // code that returned without calling anything has no tasks to wait for
        if (!workflow.getTasks().isEmpty() && !tasksComplete.getAsBoolean()) {
            return false;
        }
        // a lane nothing joined may still have code to run after its last task
        if (NativeWorkflows.hasUnappliedLane(workflow)) {
            return false;
        }
        return true; // its output is what the code returned, set when that applied
    }

    /**
     * Wakes the code for every marker that is now runnable, and repairs markers a crash left
     * behind. Saved before pushed, pushed idempotently. Then wakes the JOINs whose lanes ended.
     */
    @Override
    public void continueCode(WorkflowModel workflow) {
        NativeContinuationService.Wakeups wakeups =
                nativeContinuationService.repairAndWake(workflow);
        if (wakeups.workflowTouched) {
            executionDAOFacade.updateWorkflow(workflow);
        }
        if (!wakeups.offers.isEmpty()) {
            nativeContinuationService.push(workflow, wakeups.offers);
        }
        wakeJoins(workflow);
    }

    /**
     * Puts each JOIN that can end now on its queue with no delay. A JOIN is an async task that
     * checks its tasks again on a backoff of up to the system task callback time; nothing else
     * tells it that a lane ended. Each end of a lane (its last task, or the continuation after it)
     * is followed by a decide, so the JOIN finishes right after its last lane and not one backoff
     * later. A JOIN evaluation that is in progress at that moment can set the backoff again; the
     * JOIN then ends at its next evaluation, one backoff later at most.
     */
    private void wakeJoins(WorkflowModel workflow) {
        for (TaskModel task : workflow.getTasks()) {
            if (TaskType.TASK_TYPE_JOIN.equals(task.getTaskType())
                    && task.getStatus() == TaskModel.Status.IN_PROGRESS
                    && task.getWorkflowTask() != null
                    && Join.isReady(workflow, task)) {
                expedite(queueDAO, task);
            }
        }
    }

    /**
     * Work that a repair may have to rescue: revisit on the normal offset. Only ever shortens what
     * is set, because a busy workflow decides constantly and pushing the revisit out each time
     * would keep the sweeper away.
     */
    @Override
    public void postponeDecide(WorkflowModel workflow, Duration offset, Duration timeout) {
        if (!NativeWorkflows.needsPromptRevisit(workflow)) {
            standard.postponeDecide(workflow, offset, timeout);
            return;
        }
        queueDAO.setUnackTimeoutIfShorter(
                DECIDER_QUEUE,
                workflow.getWorkflowId(),
                Math.min(offset.getSeconds(), timeout.getSeconds()) * 1000);
    }

    /**
     * The running work of a native execution can be a queued or leased continuation, which a decide
     * pushes again; all of its tasks being terminal is normal.
     */
    @Override
    public boolean isStalled(WorkflowModel workflow) {
        return false;
    }

    /**
     * A native execution's own definition has grown; restart from the registered skeleton, with no
     * variables and no owner, as a fresh run would.
     */
    @Override
    public WorkflowDef resetForRestart(
            WorkflowModel workflow, WorkflowDef definition, boolean useLatestDefinitions) {
        return startOver(
                workflow, useLatestDefinitions ? definition : registeredDefinition(workflow));
    }

    /**
     * When the failure of a task ended the run, the execution reruns from that task; failures the
     * code caught are never run again. When the code itself failed (it threw, or diverged from its
     * recording), the last continuation of the main lane runs again.
     */
    @Override
    public RetryOutcome retry(WorkflowModel workflow) {
        removeThrowMark(workflow);
        if (failedTaskIsInTheExecution(workflow)) {
            return RetryOutcome.rerunFrom(workflow.getFailedTaskId());
        }
        LastMain last = nativeRerunPlanner.rearmLastMain(workflow);
        if (last == null) {
            return RetryOutcome.retryFailedTasks();
        }
        runAgain(workflow, last);
        return RetryOutcome.done();
    }

    /**
     * An exception the code threw is marked by a TERMINATE task where it threw. A retry runs the
     * code again (a fixed build may now pass that point), so the mark goes, with what the
     * continuation that threw drew, as for a failure with no failed task.
     */
    private void removeThrowMark(WorkflowModel workflow) {
        workflow.getTasks().stream()
                .filter(t -> t.getTaskId().equals(workflow.getFailedTaskId()))
                .filter(t -> TaskType.TERMINATE.name().equals(t.getTaskType()))
                .findFirst()
                .ifPresent(
                        thrown -> {
                            workflow.getTasks().remove(thrown);
                            nativeRerunPlanner.undoThrow(workflow, thrown);
                            executionDAOFacade.removeTask(thrown.getTaskId());
                            workflow.setFailedTaskId(null);
                        });
    }

    private static boolean failedTaskIsInTheExecution(WorkflowModel workflow) {
        return workflow.getFailedTaskId() != null
                && workflow.getTasks().stream()
                        .anyMatch(t -> workflow.getFailedTaskId().equals(t.getTaskId()));
    }

    /** Saves the execution as running; the decide that follows offers the continuation. */
    private void runAgain(WorkflowModel workflow, LastMain last) {
        workflow.setStatus(WorkflowModel.Status.RUNNING);
        workflow.setReasonForIncompletion(null);
        workflow.setFailedTaskId(null);
        workflow.setEndTime(0);
        workflow.setLastRetriedTime(System.currentTimeMillis());
        executionDAOFacade.updateWorkflow(workflow); // with the marker that runs again
        queueDAO.push(DECIDER_QUEUE, workflow.getWorkflowId(), workflow.getPriority(), 0);
    }

    /** The recorded steps are the code's, not a definition to walk: the code runs again. */
    @Override
    public void resetForRerun(WorkflowModel workflow) {
        startOver(workflow, registeredDefinition(workflow));
        workflow.setOutput(null);
        workflow.setExternalOutputPayloadStoragePath(null);
    }

    @Override
    public void checkCanRerunFrom(TaskModel rerunFrom) {
        if (CODE_STRUCTURE.contains(rerunFrom.getTaskType())) {
            throw new IllegalArgumentException(
                    "Task "
                            + rerunFrom.getTaskId()
                            + " is a "
                            + rerunFrom.getTaskType()
                            + " of a native workflow, and where the flow goes there belongs"
                            + " to the code. Rerun from a task before it, or from a task"
                            + " inside it.");
        }
    }

    /** The code after the task saw its result, so that code runs again: nothing stays in place. */
    @Override
    public boolean rerunsSubWorkflowInPlace() {
        return false;
    }

    /** Removes what the code did after the task; the plan is made before a task is removed. */
    @Override
    public List<TaskModel> undoAfter(WorkflowModel workflow, TaskModel rerunFrom) {
        NativeRerunPlanner.RerunPlan plan = nativeRerunPlanner.planRerun(workflow, rerunFrom);
        standard.removeTasks(workflow, task -> plan.dropTaskIds().contains(task.getTaskId()));
        List<TaskModel> touched = new ArrayList<>();
        List<TaskModel> reopenedJoins =
                nativeRerunPlanner.adjustRerun(workflow, rerunFrom, plan, touched);
        if (!touched.isEmpty()) {
            executionDAOFacade.updateTasks(touched);
        }
        executionDAOFacade.updateWorkflow(workflow);
        return reopenedJoins;
    }

    /** A native rerun starts a new child now. */
    @Override
    public void rerunSubWorkflowTask(
            WorkflowModel workflow,
            TaskModel task,
            Map<String, Object> taskInput,
            WorkflowExecutor executor) {
        if (taskInput != null) {
            task.setInputData(taskInput);
        }
        WorkflowExecutorOps.clearLegacySubWorkflowId(task);
        task.setStatus(TaskModel.Status.SCHEDULED);
        task.setReasonForIncompletion(null);
        systemTaskRegistry.get(TaskType.TASK_TYPE_SUB_WORKFLOW).start(workflow, task, executor);
    }

    /** Failures the code consumed, and attempts a retry superseded, are history, not work. */
    @Override
    public boolean runsAgainOnRerun(TaskModel task) {
        return standard.runsAgainOnRerun(task) && !(task.isExecuted() || task.isRetried());
    }

    /** The registered definition of the execution's version: a native execution's own grew. */
    private WorkflowDef registeredDefinition(WorkflowModel workflow) {
        return metadataDAO
                .getWorkflowDef(workflow.getWorkflowName(), workflow.getWorkflowVersion())
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Unable to find definition for %s",
                                        workflow.getWorkflowId()));
    }

    /**
     * Puts a native execution back at its beginning: the registered skeleton, no variables, no
     * owner. The code then runs again as a fresh run would.
     *
     * @return the definition the execution now runs
     */
    private WorkflowDef startOver(WorkflowModel workflow, WorkflowDef registered) {
        WorkflowDef def = metadataMapperService.populateTaskDefinitions(registered);
        workflow.setWorkflowDefinition(def);
        workflow.getVariables().clear();
        nativeContinuationService.initialize(workflow);
        return def;
    }
}
