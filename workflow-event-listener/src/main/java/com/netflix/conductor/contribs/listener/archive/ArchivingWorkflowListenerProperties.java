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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.env.Environment;

@ConfigurationProperties("conductor.workflow-status-listener.archival")
public class ArchivingWorkflowListenerProperties {

    private final Environment environment;

    @Autowired
    public ArchivingWorkflowListenerProperties(Environment environment) {
        this.environment = environment;
    }

    /** Type of archival */
    public enum ArchivalType {
        DEFAULT,
        S3
    }

    /** Archival type that we need in place. By Default the value is default */
    private ArchivalType workflowArchivalType = ArchivalType.DEFAULT;

    /** name of the S3 bucket where we want to archive the workflow */
    private String workflowS3ArchivalDefaultBucketName = "";

    /** region of the S3 bucket where we want to archive the workflow */
    private String workflowS3ArchivalBucketRegion = "us-east-1";

    /**
     * Set this variable to true if you want to archive only the workflows that didn't succeed. When
     * true, only unsuccessful workflows will be archived, while both successful and unsuccessful
     * workflows will be deleted from the datastore. This helps manage storage costs on S3 and keeps
     * only the failed workflows for debugging.
     */
    private Boolean workflowArchiveUnsuccessfulOnly = false;

    /**
     * The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO
     * supports this
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration ttlDuration = Duration.ZERO;

    /** The number of threads to process the delay queue in workflow archival */
    private int delayQueueWorkerThreadCount = 5;

    public Duration getTtlDuration() {
        return ttlDuration;
    }

    public void setTtlDuration(Duration ttlDuration) {
        this.ttlDuration = ttlDuration;
    }

    public int getDelayQueueWorkerThreadCount() {
        return delayQueueWorkerThreadCount;
    }

    public void setDelayQueueWorkerThreadCount(int delayQueueWorkerThreadCount) {
        this.delayQueueWorkerThreadCount = delayQueueWorkerThreadCount;
    }

    public ArchivalType getWorkflowArchivalType() {
        return workflowArchivalType;
    }

    public void setWorkflowArchivalType(ArchivalType workflowArchivalType) {
        this.workflowArchivalType = workflowArchivalType;
    }

    public String getWorkflowS3ArchivalDefaultBucketName() {
        return workflowS3ArchivalDefaultBucketName;
    }

    public void setWorkflowS3ArchivalDefaultBucketName(String workflowS3ArchivalDefaultBucketName) {
        this.workflowS3ArchivalDefaultBucketName = workflowS3ArchivalDefaultBucketName;
    }

    public String getWorkflowS3ArchivalBucketRegion() {
        return workflowS3ArchivalBucketRegion;
    }

    public void setWorkflowS3ArchivalBucketRegion(String workflowS3ArchivalBucketRegion) {
        this.workflowS3ArchivalBucketRegion = workflowS3ArchivalBucketRegion;
    }

    public Boolean getWorkflowArchiveUnsuccessfulOnly() {
        return workflowArchiveUnsuccessfulOnly;
    }

    public void setWorkflowArchiveUnsuccessfulOnly(Boolean workflowArchiveUnsuccessfulOnly) {
        this.workflowArchiveUnsuccessfulOnly = workflowArchiveUnsuccessfulOnly;
    }

    /** The time to delay the archival of workflow */
    public int getWorkflowFirstArchivalDelay() {
        return environment.getProperty(
                "conductor.workflow-status-listener.archival.firstDelaySeconds",
                Integer.class,
                environment.getProperty(
                        "conductor.app.asyncUpdateDelaySeconds", Integer.class, 60));
    }

    public int getWorkflowSecondArchivalDelay() {
        int firstDelay = getWorkflowFirstArchivalDelay();
        int secondDelay =
                environment.getProperty(
                        "conductor.workflow-status-listener.archival.secondDelaySeconds",
                        Integer.class,
                        0);
        return Math.max(firstDelay, secondDelay);
    }
}
