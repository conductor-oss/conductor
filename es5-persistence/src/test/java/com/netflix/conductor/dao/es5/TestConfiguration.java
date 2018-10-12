package com.netflix.conductor.dao.es5;

import com.netflix.conductor.core.config.Configuration;

import java.util.HashMap;
import java.util.Map;

public class TestConfiguration implements Configuration {

    private static final Map<String, String> properties = new HashMap<>();

    static {
        properties.put("workflow.elasticsearch.index.name", "conductor");
        properties.put("workflow.elasticsearch.tasklog.index.name", "task_log");
        properties.put("workflow.elasticsearch.url", "http://127.0.0.1:9200");
    }


    @Override
    public int getSweepFrequency() {
        return 0;
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
        return null;
    }

    @Override
    public String getEnvironment() {
        return null;
    }

    @Override
    public String getStack() {
        return null;
    }

    @Override
    public String getAppId() {
        return null;
    }

    @Override
    public String getRegion() {
        return null;
    }

    @Override
    public String getAvailabilityZone() {
        return null;
    }

    @Override
    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getTaskInputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getTaskOutputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return null;
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        return 0;
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return 0;
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        return properties.getOrDefault(name, defaultValue);
    }

    @Override
    public Map<String, Object> getAll() {
        return null;
    }
}
