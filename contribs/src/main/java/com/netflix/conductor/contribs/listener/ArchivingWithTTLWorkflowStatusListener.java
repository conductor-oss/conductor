/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.contribs.listener;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.core.orchestration.ExecutionDAOFacade;
import com.netflix.conductor.metrics.Monitors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ArchivingWithTTLWorkflowStatusListener implements WorkflowStatusListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchivingWorkflowStatusListener.class);

    private final ExecutionDAOFacade executionDAOFacade;
    private final int archiveTTLSeconds;
    private final int delayArchiveSeconds;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    @Inject
    public ArchivingWithTTLWorkflowStatusListener(ExecutionDAOFacade executionDAOFacade, Configuration config) {
        this.executionDAOFacade = executionDAOFacade;
        this.archiveTTLSeconds = config.getWorkflowArchivalTTL();
        this.delayArchiveSeconds = config.getWorkflowArchivalDelay();

        this.scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(config.getWorkflowArchivalDelayQueueWorkerThreadCount(),
                (runnable, executor) -> {
                    LOGGER.warn("Request {} to delay archiving index dropped in executor {}", runnable, executor);
                    Monitors.recordDiscardedArchivalCount();
                });
        this.scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    public void shutdownExecutorService() {
        try {
            LOGGER.info("Gracefully shutdown executor service");
            scheduledThreadPoolExecutor.shutdown();
            if (scheduledThreadPoolExecutor.awaitTermination(delayArchiveSeconds, TimeUnit.SECONDS)) {
                LOGGER.debug("tasks completed, shutting down");
            } else {
                LOGGER.warn("Forcing shutdown after waiting for {} seconds", delayArchiveSeconds);
                scheduledThreadPoolExecutor.shutdownNow();
            }
        } catch (InterruptedException ie) {
            LOGGER.warn("Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue");
            scheduledThreadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onWorkflowCompleted(Workflow workflow) {
        LOGGER.info("Archiving workflow {} on completion ", workflow.getWorkflowId());
        if (delayArchiveSeconds > 0) {
            scheduledThreadPoolExecutor.schedule(new DelayArchiveWorkflow(workflow, executionDAOFacade), delayArchiveSeconds, TimeUnit.SECONDS);
        } else {
            this.executionDAOFacade.removeWorkflowWithExpiry(workflow.getWorkflowId(), true, archiveTTLSeconds);
            Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
        }
    }

    @Override
    public void onWorkflowTerminated(Workflow workflow) {
        LOGGER.info("Archiving workflow {} on termination", workflow.getWorkflowId());
        if (delayArchiveSeconds > 0) {
            scheduledThreadPoolExecutor.schedule(new DelayArchiveWorkflow(workflow, executionDAOFacade), delayArchiveSeconds, TimeUnit.SECONDS);
        } else {
            this.executionDAOFacade.removeWorkflowWithExpiry(workflow.getWorkflowId(), true, archiveTTLSeconds);
            Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
        }
    }

    private class DelayArchiveWorkflow implements Runnable {
        private final String workflowId;
        private final String workflowName;
        private final Workflow.WorkflowStatus status;
        private final ExecutionDAOFacade executionDAOFacade;

        DelayArchiveWorkflow(Workflow workflow, ExecutionDAOFacade executionDAOFacade) {
            this.workflowId = workflow.getWorkflowId();
            this.workflowName = workflow.getWorkflowName();
            this.status = workflow.getStatus();
            this.executionDAOFacade = executionDAOFacade;
        }

        @Override
        public void run() {
            try {
                this.executionDAOFacade.removeWorkflowWithExpiry(workflowId, true, archiveTTLSeconds);
                LOGGER.info("Archived workflow {}", workflowId);
                Monitors.recordWorkflowArchived(workflowName, status);
                Monitors.recordArchivalDelayQueueSize(scheduledThreadPoolExecutor.getQueue().size());
            } catch (Exception e) {
                LOGGER.error("Unable to archive workflow: {}", workflowId, e);
            }
        }
    }
}
