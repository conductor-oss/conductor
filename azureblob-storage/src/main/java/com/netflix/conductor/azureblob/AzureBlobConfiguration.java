package com.netflix.conductor.azureblob;

import com.netflix.conductor.core.config.Configuration;

public interface AzureBlobConfiguration extends Configuration {

    String CONNECTION_STRING_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.connection_string";
    String CONNECTION_STRING_DEFAULT_VALUE = null;

    String CONTAINER_NAME_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.container_name";
    String CONTAINER_NAME_DEFAULT_VALUE = "conductor-payloads";

    String ENDPOINT_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.endpoint";
    String ENDPOINT_DEFAULT_VALUE = null;

    String SAS_TOKEN_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.sas_token";
    String SAS_TOKEN_DEFAULT_VALUE = null;

    String SIGNED_URL_EXPIRATION_SECONDS_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.signedurlexpirationseconds";
    int SIGNED_URL_EXPIRATION_SECONDS_DEFAULT_VALUE = 5;

    String WORKFLOW_INPUT_PATH_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.workflow_input_path";
    String WORKFLOW_INPUT_PATH_DEFAULT_VALUE = "workflow/input/";

    String WORKFLOW_OUTPUT_PATH_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.workflow_output_path";
    String WORKFLOW_OUTPUT_PATH_DEFAULT_VALUE = "workflow/output/";

    String TASK_INPUT_PATH_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.task_input_path";
    String TASK_INPUT_PATH_DEFAULT_VALUE = "task/input/";

    String TASK_OUTPUT_PATH_PROPERTY_NAME = "workflow.external.payload.storage.azure_blob.task_output_path";
    String TASK_OUTPUT_PATH_DEFAULT_VALUE = "task/output/";

    default String getConnectionString() {
        return getProperty(CONNECTION_STRING_PROPERTY_NAME, CONNECTION_STRING_DEFAULT_VALUE);
    }

    default String getContainerName() {
        return getProperty(CONTAINER_NAME_PROPERTY_NAME, CONTAINER_NAME_DEFAULT_VALUE);
    }

    default String getEndpoint() {
        return getProperty(ENDPOINT_PROPERTY_NAME, ENDPOINT_DEFAULT_VALUE);
    }

    default String getSasToken() {
        return getProperty(SAS_TOKEN_PROPERTY_NAME, SAS_TOKEN_DEFAULT_VALUE);
    }

    default int getSignedUrlExpirationSeconds() {
        return getIntProperty(SIGNED_URL_EXPIRATION_SECONDS_PROPERTY_NAME, SIGNED_URL_EXPIRATION_SECONDS_DEFAULT_VALUE);
    }

    default String getWorkflowInputPath() {
        return getProperty(WORKFLOW_INPUT_PATH_PROPERTY_NAME, WORKFLOW_INPUT_PATH_DEFAULT_VALUE);
    }

    default String getWorkflowOutputPath() {
        return getProperty(WORKFLOW_OUTPUT_PATH_PROPERTY_NAME, WORKFLOW_OUTPUT_PATH_DEFAULT_VALUE);
    }

    default String getTaskInputPath() {
        return getProperty(TASK_INPUT_PATH_PROPERTY_NAME, TASK_INPUT_PATH_DEFAULT_VALUE);
    }

    default String getTaskOutputPath() {
        return getProperty(TASK_OUTPUT_PATH_PROPERTY_NAME, TASK_OUTPUT_PATH_DEFAULT_VALUE);
    }
}
