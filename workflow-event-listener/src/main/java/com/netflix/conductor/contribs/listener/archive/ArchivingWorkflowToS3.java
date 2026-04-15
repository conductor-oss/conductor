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

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

public class ArchivingWorkflowToS3 implements WorkflowStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchivingWorkflowToS3.class);
    private final ExecutionDAOFacade executionDAOFacade;

    private final ArchivingWorkflowListenerProperties properties;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    private final S3Client s3Client;

    private final String bucketName;
    private final String bucketRegion;
    private final ObjectMapper objectMapper;
    private final int delayArchiveSeconds;

    public ArchivingWorkflowToS3(
            ExecutionDAOFacade executionDAOFacade, ArchivingWorkflowListenerProperties properties) {
        this.executionDAOFacade = executionDAOFacade;
        this.properties = properties;
        bucketName = properties.getWorkflowS3ArchivalDefaultBucketName();
        bucketRegion = properties.getWorkflowS3ArchivalBucketRegion();
        s3Client = S3Client.builder().region(Region.of(bucketRegion)).build();
        this.delayArchiveSeconds = properties.getWorkflowArchivalDelay();
        objectMapper = new ObjectMapper();
        this.scheduledThreadPoolExecutor =
                new ScheduledThreadPoolExecutor(
                        properties.getDelayQueueWorkerThreadCount(),
                        (runnable, executor) -> {
                            LOGGER.warn(
                                    "Request {} to delay S3 archiving index dropped in executor {}",
                                    runnable,
                                    executor);
                            Monitors.recordDiscardedArchivalCount();
                        });
        this.scheduledThreadPoolExecutor.setRemoveOnCancelPolicy(true);
        LOGGER.warn(
                "Workflow removal archiving in S3 with TTL is no longer supported, "
                        + "when using this class, workflows will be removed immediately");
    }

    @PreDestroy
    public void shutdownExecutorService() {
        try {
            LOGGER.info("Gracefully shutdown executor service in S3 Archival Listener");
            scheduledThreadPoolExecutor.shutdown();
            if (scheduledThreadPoolExecutor.awaitTermination(
                    delayArchiveSeconds, TimeUnit.SECONDS)) {
                LOGGER.debug("tasks completed, shutting down");
            } else {
                LOGGER.warn("Forcing shutdown after waiting for {} seconds", delayArchiveSeconds);
                scheduledThreadPoolExecutor.shutdownNow();
            }
            // Close S3 client
            s3Client.close();
        } catch (InterruptedException ie) {
            LOGGER.warn(
                    "Shutdown interrupted, invoking shutdownNow on scheduledThreadPoolExecutor for delay queue S3 Archival Listener");
            scheduledThreadPoolExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        archiveWorkflow(workflow);
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        archiveWorkflow(workflow);
    }

    private void archiveWorkflow(final WorkflowModel workflow) {
        // Only archive unsuccessful workflows if enabled
        if (!properties.getWorkflowArchiveUnsuccessfulOnly()
                || !workflow.getStatus().isSuccessful()) {
            final String fileName = workflow.getWorkflowId() + ".json";
            final String filePathPrefix = workflow.getWorkflowName();
            final String fullFilePath = filePathPrefix + '/' + fileName;

            try {
                // Convert workflow to JSON string
                String workflowJson = objectMapper.writeValueAsString(workflow);

                // Create put object request
                PutObjectRequest putObjectRequest =
                        PutObjectRequest.builder()
                                .bucket(bucketName)
                                .key(fullFilePath)
                                .contentType("application/json")
                                .build();

                // Upload workflow as a json file to s3
                s3Client.putObject(putObjectRequest, RequestBody.fromString(workflowJson));

                LOGGER.debug(
                        "Archived workflow. Workflow Name :{} Workflow Id :{} Workflow Status :{} to S3 bucket:{}",
                        workflow.getWorkflowName(),
                        workflow.getWorkflowId(),
                        workflow.getStatus(),
                        bucketName);
            } catch (final Exception e) {
                LOGGER.error(
                        "Exception occurred when archiving workflow to S3. Workflow Name : {} Workflow Id : {} Workflow Status : {} :",
                        workflow.getWorkflowName(),
                        workflow.getWorkflowId(),
                        workflow.getStatus(),
                        e);
                throw new RuntimeException(e);
            }
        }
        if (delayArchiveSeconds > 0) {
            scheduledThreadPoolExecutor.schedule(
                    new DelayS3ArchiveWorkflow(workflow, executionDAOFacade),
                    delayArchiveSeconds,
                    TimeUnit.SECONDS);
        } else {
            LOGGER.info(
                    "Archived workflow. Workflow Name : {} Workflow Id : {} Workflow Status : {}",
                    workflow.getWorkflowName(),
                    workflow.getWorkflowId(),
                    workflow.getStatus());
            this.executionDAOFacade.removeWorkflow(workflow.getWorkflowId(), true);
            Monitors.recordWorkflowArchived(workflow.getWorkflowName(), workflow.getStatus());
        }
    }

    private class DelayS3ArchiveWorkflow implements Runnable {

        private final String workflowId;
        private final String workflowName;
        private final WorkflowModel.Status status;
        private final ExecutionDAOFacade executionDAOFacade;

        DelayS3ArchiveWorkflow(WorkflowModel workflow, ExecutionDAOFacade executionDAOFacade) {
            this.workflowId = workflow.getWorkflowId();
            this.workflowName = workflow.getWorkflowName();
            this.status = workflow.getStatus();
            this.executionDAOFacade = executionDAOFacade;
        }

        @Override
        public void run() {
            try {
                this.executionDAOFacade.removeWorkflow(workflowId, true);
                LOGGER.info(
                        "Archived workflow. Workflow Name : {} Workflow Id : {} Workflow Status : {}",
                        workflowName,
                        workflowId,
                        status);
                Monitors.recordWorkflowArchived(workflowName, status);
                Monitors.recordArchivalDelayQueueSize(
                        scheduledThreadPoolExecutor.getQueue().size());
            } catch (Exception e) {
                LOGGER.error(
                        "Unable to archive workflow. Workflow Name : {} Workflow Id : {} Workflow Status : {}",
                        workflowName,
                        workflowId,
                        status,
                        e);
            }
        }
    }
}
