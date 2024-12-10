/*
 * Copyright 2024 Conductor Authors.
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

import java.util.List;

import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

public interface WorkflowExecutor {

    /**
     * Resets callbacks for the workflow - all the scheduled tasks will be immediately ready to be
     * polled
     *
     * @param workflowId id of the workflow
     */
    void resetCallbacksForWorkflow(String workflowId);

    /**
     * Retrun a workflow
     *
     * @param request request parameters
     * @return id of the workflow
     */
    String rerun(RerunWorkflowRequest request);

    /**
     * Restart the workflow from the beginning. If useLatestDefinitions is specified - use the
     * latest definition
     *
     * @param workflowId id of the workflow
     * @param useLatestDefinitions use latest definition if specified as true
     * @throws ConflictException if the workflow is not in terminal state
     * @throws NotFoundException if no such workflow by id
     */
    void restart(String workflowId, boolean useLatestDefinitions)
            throws ConflictException, NotFoundException;

    /**
     * Gets the last instance of each failed task and reschedule each Gets all cancelled tasks and
     * schedule all of them except JOIN (join should change status to INPROGRESS) Switch workflow
     * back to RUNNING status and call decider.
     *
     * @param workflowId the id of the workflow to be retried
     * @param resumeSubworkflowTasks Resumes the tasks inside the subworkflow if given
     */
    void retry(String workflowId, boolean resumeSubworkflowTasks);

    /**
     * @param taskResult the task result to be updated.
     * @throws IllegalArgumentException if the {@link TaskResult} is null.
     * @throws NotFoundException if the Task is not found.
     */
    TaskModel updateTask(TaskResult taskResult);

    /**
     * @param taskId id of the task
     * @return task
     */
    TaskModel getTask(String taskId);

    /**
     * @param workflowName name of the workflow
     * @param version version
     * @return list of running workflows
     */
    List<Workflow> getRunningWorkflows(String workflowName, int version);

    /**
     * @param name name of the workflow
     * @param version version
     * @param startTime from when
     * @param endTime till when
     * @return list of workflow ids matching criteria
     */
    List<String> getWorkflows(String name, Integer version, Long startTime, Long endTime);

    /**
     * @param workflowName name
     * @param version version
     * @return list of running workflow ids
     */
    List<String> getRunningWorkflowIds(String workflowName, int version);

    /**
     * @param workflowId id of the workflow to be evaluated
     * @return updated workflow
     */
    WorkflowModel decide(String workflowId);

    /**
     * @param workflowId id of the workflow to be terminated
     * @param reason termination reason to be recorded
     */
    void terminateWorkflow(String workflowId, String reason);

    /**
     * @param workflow the workflow to be terminated
     * @param reason the reason for termination
     * @param failureWorkflow the failure workflow (if any) to be triggered as a result of this
     *     termination
     */
    WorkflowModel terminateWorkflow(WorkflowModel workflow, String reason, String failureWorkflow);

    /**
     * @param workflowId
     */
    void pauseWorkflow(String workflowId);

    /**
     * @param workflowId the workflow to be resumed
     * @throws IllegalStateException if the workflow is not in PAUSED state
     */
    void resumeWorkflow(String workflowId);

    /**
     * @param workflowId the id of the workflow
     * @param taskReferenceName the referenceName of the task to be skipped
     * @param skipTaskRequest the {@link SkipTaskRequest} object
     * @throws IllegalStateException
     */
    void skipTaskFromWorkflow(
            String workflowId, String taskReferenceName, SkipTaskRequest skipTaskRequest);

    /**
     * @param workflowId id of the workflow
     * @param includeTasks includes the tasks if specified
     * @return
     */
    WorkflowModel getWorkflow(String workflowId, boolean includeTasks);

    /**
     * Used by tasks such as do while
     *
     * @param task parent task
     * @param workflow workflow
     */
    void scheduleNextIteration(TaskModel task, WorkflowModel workflow);

    /**
     * @param input Starts a new workflow execution
     * @return id of the workflow
     */
    String startWorkflow(StartWorkflowInput input);
}
