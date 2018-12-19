/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;

import java.util.List;
import java.util.Map;

/**
 * @author Viren
 */
public interface Configuration {
    String DB_PROPERTY_NAME = "db";
    String DB_DEFAULT_VALUE = "memory";

    String SWEEP_FREQUENCY_PROPERTY_NAME = "decider.sweep.frequency.seconds";
    int SWEEP_FREQUENCY_DEFAULT_VALUE = 30;

    String SWEEP_DISABLE_PROPERTY_NAME = "decider.sweep.disable";
    // FIXME This really should be typed correctly.
    String SWEEP_DISABLE_DEFAULT_VALUE = "false";

    String DISABLE_ASYNC_WORKERS_PROPERTY_NAME = "conductor.disable.async.workers";
    // FIXME This really should be typed correctly.
    String DISABLE_ASYNC_WORKERS_DEFAULT_VALUE = "false";

    String ENVIRONMENT_PROPERTY_NAME = "environment";
    String ENVIRONMENT_DEFAULT_VALUE = "test";

    String STACK_PROPERTY_NAME = "STACK";
    String STACK_DEFAULT_VALUE = "test";

    String APP_ID_PROPERTY_NAME = "APP_ID";
    String APP_ID_DEFAULT_VALUE = "conductor";

    String REGION_PROPERTY_NAME = "EC2_REGION";
    String REGION_DEFAULT_VALUE = "us-east-1";

    String AVAILABILITY_ZONE_PROPERTY_NAME = "EC2_AVAILABILITY_ZONE";
    String AVAILABILITY_ZONE_DEFAULT_VALUE = "us-east-1c";

    String JERSEY_ENABLED_PROPERTY_NAME = "conductor.jersey.enabled";
    boolean JERSEY_ENABLED_DEFAULT_VALUE = true;

    String ADDITIONAL_MODULES_PROPERTY_NAME = "conductor.additional.modules";

    //TODO add constants for input/output external payload related properties.

    default DB getDB() {
        return DB.valueOf(getDBString());
    }

    default String getDBString() {
        return getProperty(DB_PROPERTY_NAME, DB_DEFAULT_VALUE).toUpperCase();
    }

    /**
     * @return time frequency in seconds, at which the workflow sweeper should run to evaluate running workflows.
     */
    int getSweepFrequency();

    /**
     * @return when set to true, the sweep is disabled
     */
    boolean disableSweep();


    /**
     * @return when set to true, the background task workers executing async system tasks (eg HTTP) are disabled
     */
    boolean disableAsyncWorkers();

    /**
     * @return ID of the server.  Can be host name, IP address or any other meaningful identifier.  Used for logging
     */
    String getServerId();

    /**
     * @return Current environment. e.g. test, prod
     */
    String getEnvironment();

    /**
     * @return name of the stack under which the app is running.  e.g. devint, testintg, staging, prod etc.
     */
    String getStack();

    /**
     * @return APP ID.  Used for logging
     */
    String getAppId();

    /**
     * @return Data center region.  if hosting on Amazon the value is something like us-east-1, us-west-2 etc.
     */
    String getRegion();

    /**
     * @return Availability zone / rack.  for AWS deployments, the value is something like us-east-1a, etc.
     */
    String getAvailabilityZone();

    default boolean getJerseyEnabled() {
        return getBooleanProperty(JERSEY_ENABLED_PROPERTY_NAME, JERSEY_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined integer property.
     */
    int getIntProperty(String name, int defaultValue);

    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined string property.
     */
    String getProperty(String name, String defaultValue);

    boolean getBooleanProperty(String name, boolean defaultValue);

    default boolean getBoolProperty(String name, boolean defaultValue) {
        String value = getProperty(name, null);
        if (null == value || value.trim().length() == 0) {
            return defaultValue;
        }
        return Boolean.valueOf(value.trim());
    }

    /**
     * @return Returns all the configurations in a map.
     */
    Map<String, Object> getAll();

    /**
     * @return Provides a list of additional modules to configure. Use this to inject additional modules that should be
     * loaded as part of the Conductor server initialization If you are creating custom tasks
     * (com.netflix.conductor.core.execution.tasks.WorkflowSystemTask) then initialize them as part of the custom
     * modules.
     */
    default List<AbstractModule> getAdditionalModules() {
        return null;
    }


    /**
     * @param name         Name of the property
     * @param defaultValue Default value when not specified
     * @return User defined Long property.
     */
    long getLongProperty(String name, long defaultValue);

	/**
	 *
	 * @return The threshold of the workflow input payload size in KB beyond which the payload will be stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}
	 */
	Long getWorkflowInputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The maximum threshold of the workflow input payload size in KB beyond which input will be rejected and the workflow will be marked as FAILED
	 */
	Long getMaxWorkflowInputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The threshold of the workflow output payload size in KB beyond which the payload will be stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}
	 */
	Long getWorkflowOutputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The maximum threshold of the workflow output payload size in KB beyond which output will be rejected and the workflow will be marked as FAILED
	 */
	Long getMaxWorkflowOutputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The threshold of the task input payload size in KB beyond which the payload will be stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}
	 */
	Long getTaskInputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The maximum threshold of the task input payload size in KB beyond which the task input will be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR
	 */
	Long getMaxTaskInputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The threshold of the task output payload size in KB beyond which the payload will be stored in {@link com.netflix.conductor.common.utils.ExternalPayloadStorage}
	 */
	Long getTaskOutputPayloadSizeThresholdKB();

	/**
	 *
	 * @return The maximum threshold of the task output payload size in KB beyond which the task input will be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR
	 */
	Long getMaxTaskOutputPayloadSizeThresholdKB();


    enum DB {
        REDIS, DYNOMITE, MEMORY, REDIS_CLUSTER, MYSQL, CASSANDRA, REDIS_SENTINEL
    }
}
