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
package com.netflix.conductor.rest.controllers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.model.SignalResponse;
import org.conductoross.conductor.model.WorkflowSignalReturnStrategy;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.core.execution.NotificationResult;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.WorkflowService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Shared helper that polls a workflow until it reaches a blocking or terminal state and renders the
 * result as a {@link SignalResponse} according to the requested {@link
 * WorkflowSignalReturnStrategy}.
 *
 * <p>Used by both the synchronous {@code execute} endpoint (which starts a workflow and waits for
 * it to block) and the synchronous {@code signal} endpoint (which signals a blocked task and waits
 * for the workflow to settle into its next blocking state).
 */
@Slf4j
final class WorkflowSignalResponder {

    private WorkflowSignalResponder() {}

    /**
     * Polls the workflow every 100ms until it is terminal or has a blocking task, then renders a
     * {@link SignalResponse}. If neither happens within {@code waitForSeconds}, returns the
     * workflow's current state.
     *
     * @param workflowService used to fetch the latest workflow model on each tick
     * @param workflowId the (root) workflow to poll
     * @param taskRefs additional task reference names to treat as blocking once terminal (may be
     *     empty)
     * @param returnStrategy controls what shape the response takes
     * @param requestId correlation id echoed back in the response
     * @param timeout maximum time to wait before returning the current state
     */
    static Mono<SignalResponse> awaitSignalResponse(
            WorkflowService workflowService,
            String workflowId,
            String[] taskRefs,
            WorkflowSignalReturnStrategy returnStrategy,
            String requestId,
            Duration timeout) {

        return Flux.interval(Duration.ofMillis(100))
                .map(tick -> workflowService.getWorkflowModel(workflowId, true))
                .filter(
                        workflow -> {
                            // Check if workflow is terminal
                            if (workflow.getStatus().isTerminal()) {
                                return true;
                            }

                            // Check recursively for blocking tasks in the workflow and all
                            // sub-workflows
                            return findBlockingTasks(workflowService, workflow, taskRefs)
                                    .hasBlockingTasks();
                        })
                .next() // Take the first matching workflow
                .map(
                        workflow ->
                                toSignalResponse(
                                        workflowService,
                                        workflow,
                                        taskRefs,
                                        returnStrategy,
                                        requestId))
                .timeout(
                        timeout,
                        Mono.defer(
                                () -> {
                                    log.info("Signal/execute timed out for {}", workflowId);
                                    // Timeout reached, return current state
                                    WorkflowModel workflow =
                                            workflowService.getWorkflowModel(workflowId, true);
                                    NotificationResult result =
                                            NotificationResult.builder()
                                                    .targetWorkflow(workflow)
                                                    .blockingWorkflow(workflow)
                                                    .blockingTasks(new ArrayList<>())
                                                    .signalTimeout(true)
                                                    .build();
                                    return Mono.just(result.toResponse(returnStrategy, requestId));
                                }));
    }

    private static SignalResponse toSignalResponse(
            WorkflowService workflowService,
            WorkflowModel workflow,
            String[] taskRefs,
            WorkflowSignalReturnStrategy returnStrategy,
            String requestId) {
        // Find blocking tasks and blocking workflow recursively
        BlockingTaskResult blockingResult = findBlockingTasks(workflowService, workflow, taskRefs);

        // Create NotificationResult and return response based on strategy
        NotificationResult result =
                NotificationResult.builder()
                        .targetWorkflow(workflow)
                        .blockingWorkflow(
                                blockingResult.blockingWorkflow != null
                                        ? blockingResult.blockingWorkflow
                                        : workflow)
                        .blockingTasks(blockingResult.blockingTasks)
                        .build();

        return result.toResponse(returnStrategy, requestId);
    }

    /**
     * Recursively finds blocking tasks in the workflow and all its sub-workflows. A blocking task
     * is: 1. A WAIT task that is not in terminal state 2. A task matching one of {@code taskRefs}
     * that is in terminal state.
     *
     * @param workflowService used to fetch sub-workflow models
     * @param workflow the workflow to search
     * @param taskRefs task reference names to wait for (may be empty)
     * @return a {@link BlockingTaskResult} containing the blocking tasks and the workflow they were
     *     found in
     */
    static BlockingTaskResult findBlockingTasks(
            WorkflowService workflowService, WorkflowModel workflow, String[] taskRefs) {
        List<TaskModel> blockingTasks = new ArrayList<>();
        WorkflowModel blockingWorkflow = null;

        // Check tasks in the current workflow
        for (TaskModel task : workflow.getTasks()) {
            // Check for WAIT tasks that are not terminal (actively waiting)
            if (TaskType.TASK_TYPE_WAIT.equals(task.getTaskType())
                    && !task.getStatus().isTerminal()
                    && task.getWaitTimeout() == 0) {
                blockingTasks.add(task);
                if (blockingWorkflow == null) {
                    blockingWorkflow = workflow;
                }
            }

            // Check if this task matches any of the specified task refs and is terminal
            for (String taskRef : taskRefs) {
                String trimmedRef = taskRef.trim();
                if (trimmedRef.equals(task.getReferenceTaskName())
                        && task.getStatus().isTerminal()) {
                    blockingTasks.add(task);
                    if (blockingWorkflow == null) {
                        blockingWorkflow = workflow;
                    }
                }
            }

            // If this is a SUB_WORKFLOW task, recursively check the sub-workflow
            if (TaskType.TASK_TYPE_SUB_WORKFLOW.equals(task.getTaskType())
                    && StringUtils.isNotBlank(task.getSubWorkflowId())
                    && !task.getStatus().isTerminal()) {
                try {
                    WorkflowModel subWorkflow =
                            workflowService.getWorkflowModel(task.getSubWorkflowId(), true);
                    BlockingTaskResult subResult =
                            findBlockingTasks(workflowService, subWorkflow, taskRefs);
                    if (subResult.hasBlockingTasks()) {
                        blockingTasks.addAll(subResult.blockingTasks);
                        if (blockingWorkflow == null) {
                            blockingWorkflow = subResult.blockingWorkflow;
                        }
                    }
                } catch (Exception e) {
                    // If we can't fetch the sub-workflow, skip it
                }
            }
        }

        return new BlockingTaskResult(blockingTasks, blockingWorkflow);
    }

    /** Result of finding blocking tasks in a workflow hierarchy. */
    static final class BlockingTaskResult {
        final List<TaskModel> blockingTasks;
        final WorkflowModel blockingWorkflow;

        BlockingTaskResult(List<TaskModel> blockingTasks, WorkflowModel blockingWorkflow) {
            this.blockingTasks = blockingTasks;
            this.blockingWorkflow = blockingWorkflow;
        }

        boolean hasBlockingTasks() {
            return blockingTasks != null && !blockingTasks.isEmpty();
        }
    }
}
