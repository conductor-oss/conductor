/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Viren
 *
 */
public class SystemPropertiesConfiguration implements Configuration {

    private static Logger logger = LoggerFactory.getLogger(SystemPropertiesConfiguration.class);

    @Override
    public int getSweepFrequency() {
        return getIntProperty(SWEEP_FREQUENCY_PROPERTY_NAME, SWEEP_FREQUENCY_DEFAULT_VALUE);
    }

    @Override
    public boolean disableSweep() {
        String disable = getProperty(SWEEP_DISABLE_PROPERTY_NAME, SWEEP_DISABLE_DEFAULT_VALUE);
        return Boolean.getBoolean(disable);
    }

    @Override
    public boolean disableAsyncWorkers() {
        String disable = getProperty(DISABLE_ASYNC_WORKERS_PROPERTY_NAME, DISABLE_ASYNC_WORKERS_DEFAULT_VALUE);
        return Boolean.getBoolean(disable);
    }

    @Override
    public boolean isEventMessageIndexingEnabled() {
        return getBooleanProperty(EVENT_MESSAGE_INDEXING_ENABLED_PROPERTY_NAME, EVENT_MESSAGE_INDEXING_ENABLED_DEFAULT_VALUE);
    }

    @Override
    public boolean isEventExecutionIndexingEnabled() {
        return getBooleanProperty(EVENT_EXECUTION_INDEXING_ENABLED_PROPERTY_NAME, EVENT_EXECUTION_INDEXING_ENABLED_DEFAULT_VALUE);
    }

    @Override
    public String getServerId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    @Override
    public String getEnvironment() {
        return getProperty(ENVIRONMENT_PROPERTY_NAME, ENVIRONMENT_DEFAULT_VALUE);
    }

    @Override
    public String getStack() {
        return getProperty(STACK_PROPERTY_NAME, STACK_DEFAULT_VALUE);
    }

    @Override
    public String getAppId() {
        return getProperty(APP_ID_PROPERTY_NAME, APP_ID_DEFAULT_VALUE);
    }

    @Override
    public String getRegion() {
        return getProperty(REGION_PROPERTY_NAME, REGION_DEFAULT_VALUE);
    }

    @Override
    public String getAvailabilityZone() {
        return getProperty(AVAILABILITY_ZONE_PROPERTY_NAME, AVAILABILITY_ZONE_DEFAULT_VALUE);
    }

    @Override
    public int getIntProperty(String key, int defaultValue) {
        String val = getProperty(key, Integer.toString(defaultValue));
        try {
            defaultValue = Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
        }
        return defaultValue;
    }

    @Override
    public long getLongProperty(String key, long defaultValue) {
        String val = getProperty(key, Long.toString(defaultValue));
        try {
            defaultValue = Integer.parseInt(val);
        } catch (NumberFormatException ignored) {
        }
        return defaultValue;
    }

    @Override
    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.workflow.input.payload.threshold.kb", 5120L);
    }

    @Override
    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.max.workflow.input.payload.threshold.kb", 10240L);
    }

    @Override
    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.workflow.output.payload.threshold.kb", 5120L);
    }

    @Override
    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.max.workflow.output.payload.threshold.kb", 10240L);
    }

    @Override
    public Long getMaxWorkflowVariablesPayloadSizeThresholdKB() {
        return getLongProperty("conductor.max.workflow.variables.payload.threshold.kb", 256L);
    }

    @Override
    public Long getTaskInputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.task.input.payload.threshold.kb", 3072L);
    }

    @Override
    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.max.task.input.payload.threshold.kb", 10240L);
    }

    @Override
    public Long getTaskOutputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.task.output.payload.threshold.kb", 3072L);
    }

    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return getLongProperty("conductor.max.task.output.payload.threshold.kb", 10240L);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        String val;
        val = System.getenv(key.replace('.', '_'));
        if (val == null || val.isEmpty()) {
            val = Optional.ofNullable(System.getProperty(key))
                    .orElse(defaultValue);
        }
        return val;
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String val = getProperty(name, null);

        if (val != null) {
            return Boolean.parseBoolean(val);
        } else {
            return defaultValue;
        }
    }

    @Override
    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        Properties props = System.getProperties();
        props.forEach((key, value) -> map.put(key.toString(), value));
        return map;
    }

    @Override
    public List<AbstractModule> getAdditionalModules() {

        String additionalModuleClasses = getProperty(ADDITIONAL_MODULES_PROPERTY_NAME, null);

        List<AbstractModule> modules = new LinkedList<>();

        if (!StringUtils.isEmpty(additionalModuleClasses)) {
            try {
                String[] classes = additionalModuleClasses.split(",");
                for (String clazz : classes) {
                    Object moduleObj = Class.forName(clazz).newInstance();
                    if (moduleObj instanceof AbstractModule) {
                        AbstractModule abstractModule = (AbstractModule) moduleObj;
                        modules.add(abstractModule);
                    } else {
                        logger.error(clazz + " does not implement " + AbstractModule.class.getName() + ", skipping...");
                    }
                }
            } catch (Exception e) {
                logger.warn("Error adding additional modules", e);
            }
        }

        return modules;
    }
}
