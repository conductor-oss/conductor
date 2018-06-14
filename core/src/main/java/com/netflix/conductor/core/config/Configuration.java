/**
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
/**
 *
 */
package com.netflix.conductor.core.config;

import com.google.inject.AbstractModule;

import java.util.List;
import java.util.Map;

/**
 * @author Viren
 *
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

    default DB getDB(){
        return DB.valueOf(getDBString());
    }

    default String getDBString() {
        return getProperty(DB_PROPERTY_NAME, DB_DEFAULT_VALUE).toUpperCase();
    }

    /**
     *
     * @return time frequency in seconds, at which the workflow sweeper should run to evaluate running workflows.
     */
    public int getSweepFrequency();

    /**
     *
     * @return when set to true, the sweep is disabled
     */
    public boolean disableSweep();


    /**
     *
     * @return when set to true, the background task workers executing async system tasks (eg HTTP) are disabled
     *
     */
    public boolean disableAsyncWorkers();

    /**
     *
     * @return ID of the server.  Can be host name, IP address or any other meaningful identifier.  Used for logging
     */
    public String getServerId();

    /**
     *
     * @return Current environment. e.g. test, prod
     */
    public String getEnvironment();

    /**
     *
     * @return name of the stack under which the app is running.  e.g. devint, testintg, staging, prod etc.
     */
    public String getStack();

    /**
     *
     * @return APP ID.  Used for logging
     */
    public String getAppId();

    /**
     *
     * @return Data center region.  if hosting on Amazon the value is something like us-east-1, us-west-2 etc.
     */
    public String getRegion();

    /**
     *
     * @return Availability zone / rack.  for AWS deployments, the value is something like us-east-1a, etc.
     */
    public String getAvailabilityZone();

    default boolean getJerseyEnabled(){
        return getBooleanProperty(JERSEY_ENABLED_PROPERTY_NAME, JERSEY_ENABLED_DEFAULT_VALUE);
    }

    /**
     *
     * @param name Name of the property
     * @param defaultValue  Default value when not specified
     * @return User defined integer property.
     */
    public int getIntProperty(String name, int defaultValue);

    /**
     *
     * @param name Name of the property
     * @param defaultValue  Default value when not specified
     * @return User defined string property.
     */
    public String getProperty(String name, String defaultValue);

    boolean getBooleanProperty(String name, boolean defaultValue);

    /**
     *
     * @return Returns all the configurations in a map.
     */
    public Map<String, Object> getAll();

    /**
     *
     * @return Provides a list of additional modules to configure.
     * Use this to inject additional modules that should be loaded as part of the Conductor server initialization
     * If you are creating custom tasks (com.netflix.conductor.core.execution.tasks.WorkflowSystemTask) then initialize them as part of the custom modules.
     */
    public default List<AbstractModule> getAdditionalModules() {
        return null;
    }


    enum DB {
        REDIS, DYNOMITE, MEMORY, REDIS_CLUSTER, MYSQL
    }

}
