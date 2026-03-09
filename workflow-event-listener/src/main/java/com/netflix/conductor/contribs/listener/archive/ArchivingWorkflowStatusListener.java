/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.contribs.listener.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.WorkflowModel;

/**
 * Provides default implementation of workflow archiving immediately after workflow is completed or
 * terminated.
 *
 * @author pavel.halabala
 */
public class ArchivingWorkflowStatusListener implements WorkflowStatusListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ArchivingWorkflowStatusListener.class);
    private final ExecutionDAOFacade executionDAOFacade;
    private final java.util.concurrent.ScheduledThreadPoolExecutor archivalRetryExecutor =
            new java.util.concurrent.ScheduledThreadPoolExecutor(
                    1,
                    (r, e) ->
                            LOGGER.warn(
                                    "Archival retry task dropped by executor {} - queue size: {}",
                                    e,
                                    e.getQueue().size()));

    public ArchivingWorkflowStatusListener(ExecutionDAOFacade executionDAOFacade) {
        this.executionDAOFacade = executionDAOFacade;
        this.archivalRetryExecutor.setRemoveOnCancelPolicy(true);
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        LOGGER.info("Archiving workflow {} on completion ", workflow.getWorkflowId());
        try {
            this.executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
            Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
        } catch (Exception e) {
            LOGGER.error(
                    "Archival failed for workflow {} on completion, scheduling retry",
                    workflow.getWorkflowId(),
                    e);
            scheduleArchivalRetry(workflow, 1);
        }
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        LOGGER.info("Archiving workflow {} on termination", workflow.getWorkflowId());
        try {
            this.executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
            Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
        } catch (Exception e) {
            LOGGER.error(
                    "Archival failed for workflow {} on termination, scheduling retry",
                    workflow.getWorkflowId(),
                    e);
            scheduleArchivalRetry(workflow, 1);
        }
    }

    private void scheduleArchivalRetry(WorkflowModel workflow, int attempt) {
        if (attempt > 3) {
            LOGGER.error(
                    "Exceeded archival retry attempts for workflow {}", workflow.getWorkflowId());
            return;
        }
        long delaySeconds = (long) Math.pow(3, attempt); // 3s, 9s, 27s
        archivalRetryExecutor.schedule(
                () -> {
                    try {
                        executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
                        Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
                        LOGGER.info(
                                "Archival retry succeeded for workflow {} on attempt {}",
                                workflow.getWorkflowId(),
                                attempt);
                    } catch (Exception ex) {
                        LOGGER.warn(
                                "Archival retry attempt {} failed for workflow {}, scheduling next",
                                attempt,
                                workflow.getWorkflowId(),
                                ex);
                        scheduleArchivalRetry(workflow, attempt + 1);
                    }
                },
                delaySeconds,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    @jakarta.annotation.PreDestroy
    void shutdownRetryExecutor() {
        try {
            archivalRetryExecutor.shutdown();
            if (!archivalRetryExecutor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                archivalRetryExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            archivalRetryExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
