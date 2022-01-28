/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.azureblob.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.external-payload-storage.azureblob")
public class AzureBlobProperties {

    /** The connection string to be used to connect to Azure Blob storage */
    private String connectionString = null;

    /** The name of the container where the payloads will be stored */
    private String containerName = "conductor-payloads";

    /** The endpoint to be used to connect to Azure Blob storage */
    private String endpoint = null;

    /** The sas token to be used for authenticating requests */
    private String sasToken = null;

    /** The time for which the shared access signature is valid */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration signedUrlExpirationDuration = Duration.ofSeconds(5);

    /** The path at which the workflow inputs will be stored */
    private String workflowInputPath = "workflow/input/";

    /** The path at which the workflow outputs will be stored */
    private String workflowOutputPath = "workflow/output/";

    /** The path at which the task inputs will be stored */
    private String taskInputPath = "task/input/";

    /** The path at which the task outputs will be stored */
    private String taskOutputPath = "task/output/";

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getSasToken() {
        return sasToken;
    }

    public void setSasToken(String sasToken) {
        this.sasToken = sasToken;
    }

    public Duration getSignedUrlExpirationDuration() {
        return signedUrlExpirationDuration;
    }

    public void setSignedUrlExpirationDuration(Duration signedUrlExpirationDuration) {
        this.signedUrlExpirationDuration = signedUrlExpirationDuration;
    }

    public String getWorkflowInputPath() {
        return workflowInputPath;
    }

    public void setWorkflowInputPath(String workflowInputPath) {
        this.workflowInputPath = workflowInputPath;
    }

    public String getWorkflowOutputPath() {
        return workflowOutputPath;
    }

    public void setWorkflowOutputPath(String workflowOutputPath) {
        this.workflowOutputPath = workflowOutputPath;
    }

    public String getTaskInputPath() {
        return taskInputPath;
    }

    public void setTaskInputPath(String taskInputPath) {
        this.taskInputPath = taskInputPath;
    }

    public String getTaskOutputPath() {
        return taskOutputPath;
    }

    public void setTaskOutputPath(String taskOutputPath) {
        this.taskOutputPath = taskOutputPath;
    }
}
