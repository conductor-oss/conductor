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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow", name = "external.payload.storage", havingValue = "AZURE_BLOB")
public class AzureBlobProperties {

    public static final String CONNECTION_STRING_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.connection_string";
    public static final String ENDPOINT_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.endpoint";

    @Value("${" + CONNECTION_STRING_PROPERTY_NAME + ":#{null}}")
    private String connectionString;

    @Value("${workflow.external.payload.storage.azure_blob.container_name:conductor-payloads}")
    private String containerName;

    @Value("${" + ENDPOINT_PROPERTY_NAME + ":#{null}}")
    private String endpoint;

    @Value("${workflow.external.payload.storage.azure_blob.sas_token:#{null}}")
    private String sasToken;

    @Value("${workflow.external.payload.storage.azure_blob.signedurlexpirationseconds:5}")
    private int signedUrlExpirationSeconds;

    @Value("${workflow.external.payload.storage.azure_blob.workflow_input_path:workflow/input/}")
    private String workflowInputPath;

    @Value("${workflow.external.payload.storage.azure_blob.workflow_output_path:workflow/output/}")
    private String workflowOutputPath;

    @Value("${workflow.external.payload.storage.azure_blob.task_input_path:task/input/}")
    private String taskInputPath;

    @Value("${workflow.external.payload.storage.azure_blob.task_output_path:task/output/}")
    private String taskOutputPath;

    public String getConnectionString() {
        return connectionString;
    }

    public String getContainerName() {
        return containerName;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getSasToken() {
        return sasToken;
    }

    public int getSignedUrlExpirationSeconds() {
        return signedUrlExpirationSeconds;
    }

    public String getWorkflowInputPath() {
        return workflowInputPath;
    }

    public String getWorkflowOutputPath() {
        return workflowOutputPath;
    }

    public String getTaskInputPath() {
        return taskInputPath;
    }

    public String getTaskOutputPath() {
        return taskOutputPath;
    }
}
