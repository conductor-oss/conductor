package com.netflix.conductor.config;

import com.netflix.conductor.azureblob.AzureBlobConfiguration;

import java.util.Map;

public class TestConfiguration implements AzureBlobConfiguration {

    private String connectionString = null;
    private String endpoint = null;
    private String sasToken = null;

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public void setSasToken(String sasToken) {
        this.sasToken = sasToken;
    }

    @Override
    public String getConnectionString() {
        return connectionString;
    }

    @Override
    public String getEndpoint() {
        return endpoint;
    }

    @Override
    public String getSasToken() {
        return sasToken;
    }

    // Copied from com.netflix.conductor.core.execution.TestConfiguration
    @Override
    public int getSweepFrequency() {
        return 1;
    }

    @Override
    public boolean disableSweep() {
        return false;
    }

    @Override
    public boolean disableAsyncWorkers() {
        return false;
    }

    @Override
    public String getServerId() {
        return "server_id";
    }

    @Override
    public String getEnvironment() {
        return "test";
    }

    @Override
    public String getStack() {
        return "junit";
    }

    @Override
    public String getAppId() {
        return "workflow";
    }

    @Override
    public boolean isEventMessageIndexingEnabled() {
        return true;
    }

    @Override
    public boolean isEventExecutionIndexingEnabled() {
        return true;
    }

    @Override
    public String getProperty(String string, String def) {
        return def;
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return false;
    }

    @Override
    public String getAvailabilityZone() {
        return "us-east-1a";
    }

    @Override
    public int getIntProperty(String string, int def) {
        return 100;
    }

    @Override
    public String getRegion() {
        return "us-east-1";
    }

    @Override
    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return 10L;
    }

    @Override
    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return 10L;
    }

    @Override
    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getMaxWorkflowVariablesPayloadSizeThresholdKB() {
        return 256L;
    }

    @Override
    public Long getTaskInputPayloadSizeThresholdKB() {
        return 10L;
    }

    @Override
    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Long getTaskOutputPayloadSizeThresholdKB() {
        return 10L;
    }

    @Override
    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return 10240L;
    }

    @Override
    public Map<String, Object> getAll() {
        return null;
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return 1000000L;
    }

}
