/*
 * Copyright 2020 Netflix, Inc.
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

    String SYSTEM_TASK_WORKER_THREAD_COUNT_PROPERTY_NAME = "workflow.system.task.worker.thread.count";
    int SYSTEM_TASK_WORKER_THREAD_COUNT_DEFAULT_VALUE = 10;

    String SYSTEM_TASK_WORKER_CALLBACK_SECONDS_PROPERTY_NAME = "workflow.system.task.worker.callback.seconds";
    int SYSTEM_TASK_WORKER_CALLBACK_SECONDS_DEFAULT_VALUE = 30;

    String SYSTEM_TASK_WORKER_POLL_INTERVAL_PROPERTY_NAME = "workflow.system.task.worker.poll.interval";
    int SYSTEM_TASK_WORKER_POLL_INTERVAL_DEFAULT_VALUE = 50;

    String SYSTEM_TASK_WORKER_EXECUTION_NAMESPACE_PROPERTY_NAME = "workflow.system.task.worker.executionNameSpace";
    String SYSTEM_TASK_WORKER_EXECUTION_NAMESPACE_DEFAULT_VALUE = "";

    String SYSTEM_TASK_WORKER_ISOLATED_THREAD_COUNT_PROPERTY_NAME = "workflow.isolated.system.task.worker.thread.count";
    int SYSTEM_TASK_WORKER_ISOLATED_THREAD_COUNT_DEFAULT_VALUE = 1;

    String SYSTEM_TASK_MAX_POLL_COUNT_PROPERTY_NAME = "workflow.system.task.queue.pollCount";
    int SYSTEM_TASK_MAX_POLL_COUNT_DEFAULT_VALUE = 1;

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

    String ASYNC_INDEXING_ENABLED_PROPERTY_NAME = "async.indexing.enabled";
    boolean ASYNC_INDEXING_ENABLED_DEFAULT_VALUE = false;

    String ASYNC_UPDATE_SHORT_WORKFLOW_DURATION_PROPERTY_NAME = "async.update.short.workflow.duration.seconds";
    int ASYNC_UPDATE_SHORT_WORKFLOW_DURATION_DEFAULT_VALUE = 30;

    String ASYNC_UPDATE_DELAY_PROPERTY_NAME = "async.update.delay.seconds";
    int ASYNC_UPDATE_DELAY_DEFAULT_VALUE = 60;

    String ADDITIONAL_MODULES_PROPERTY_NAME = "conductor.additional.modules";

    String EXECUTION_LOCK_ENABLED_PROPERTY_NAME = "workflow.decider.locking.enabled";
    boolean EXECUTION_LOCK_ENABLED_DEFAULT_VALUE = false;

    String LOCKING_SERVER_PROPERTY_NAME = "workflow.decider.locking.server";
    String LOCKING_SERVER_DEFAULT_VALUE = "noop_lock";

    String IGNORE_LOCKING_EXCEPTIONS_PROPERTY_NAME = "workflow.decider.locking.exceptions.ignore";
    boolean IGNORE_LOCKING_EXCEPTIONS_DEFAULT_VALUE = false;

    String EVENT_MESSAGE_INDEXING_ENABLED_PROPERTY_NAME = "workflow.event.message.indexing.enabled";
    boolean EVENT_MESSAGE_INDEXING_ENABLED_DEFAULT_VALUE = true;

    String EVENT_EXECUTION_INDEXING_ENABLED_PROPERTY_NAME = "workflow.event.execution.indexing.enabled";
    boolean EVENT_EXECUTION_INDEXING_ENABLED_DEFAULT_VALUE = true;

    String TASKEXECLOG_INDEXING_ENABLED_PROPERTY_NAME = "workflow.taskExecLog.indexing.enabled";
    boolean TASKEXECLOG_INDEXING_ENABLED_DEFAULT_VALUE = true;

    String INDEXING_ENABLED_PROPERTY_NAME = "workflow.indexing.enabled";
    boolean INDEXING_ENABLED_DEFAULT_VALUE = true;

    String TASK_DEF_REFRESH_TIME_SECS_PROPERTY_NAME = "conductor.taskdef.cache.refresh.time.seconds";
    int TASK_DEF_REFRESH_TIME_SECS_DEFAULT_VALUE = 60;

    String EVENT_HANDLER_REFRESH_TIME_SECS_PROPERTY_NAME = "conductor.eventhandler.cache.refresh.time.seconds";
    int EVENT_HANDLER_REFRESH_TIME_SECS_DEFAULT_VALUE = 60;

    String EVENT_EXECUTION_PERSISTENCE_TTL_SECS_PROPERTY_NAME = "workflow.event.execution.persistence.ttl.seconds";
    int EVENT_EXECUTION_PERSISTENCE_TTL_SECS_DEFAULT_VALUE = 0;

    String WORKFLOW_ARCHIVAL_TTL_SECS_PROPERTY_NAME = "workflow.archival.ttl.seconds";
    int WORKFLOW_ARCHIVAL_TTL_SECS_DEFAULT_VALUE = 0;

    String WORKFLOW_ARCHIVAL_DELAY_SECS_PROPERTY_NAME = "workflow.archival.delay.seconds";

    String WORKFLOW_ARCHIVAL_DELAY_QUEUE_WORKER_THREAD_COUNT_PROPERTY_NAME = "workflow.archival.delay.queue.worker.thread.count";
    int WORKFLOW_ARCHIVAL_DELAY_QUEUE_WORKER_THREAD_COUNT_DEFAULT_VALUE = 5;

    String OWNER_EMAIL_MANDATORY_NAME = "workflow.owner.email.mandatory";
    boolean OWNER_EMAIL_MANDATORY_DEFAULT_VALUE = true;

    String ELASTIC_SEARCH_AUTO_INDEX_MANAGEMENT_ENABLED_PROPERTY_NAME = "workflow.elasticsearch.auto.index.management.enabled";
    boolean ELASTIC_SEARCH_AUTO_INDEX_MANAGEMENT_ENABLED_DEFAULT_VALUE = true;

    String ELASTIC_SEARCH_DOCUMENT_TYPE_OVERRIDE_PROPERTY_NAME = "workflow.elasticsearch.document.type.override";
    String ELASTIC_SEARCH_DOCUMENT_TYPE_OVERRIDE_DEFAULT_VALUE = "";

    String EVENT_QUEUE_POLL_SCHEDULER_THREAD_COUNT_PROPERTY_NAME = "workflow.event.queue.scheduler.poll.thread.count";

    String WORKFLOW_REPAIR_SERVICE_ENABLED = "workflow.repairservice.enabled";
    boolean WORKFLOW_REPAIR_SERVICE_ENABLED_DEFAULT_VALUE = false;

    //TODO add constants for input/output external payload related properties.

    default DB getDB() {
        return DB.valueOf(getDBString());
    }

    default LOCKING_SERVER getLockingServer() {
        return LOCKING_SERVER.valueOf(getLockingServerString());
    }

    default String getDBString() {
        return getProperty(DB_PROPERTY_NAME, DB_DEFAULT_VALUE).toUpperCase();
    }

    default String getLockingServerString() {
        return getProperty(LOCKING_SERVER_PROPERTY_NAME, LOCKING_SERVER_DEFAULT_VALUE).toUpperCase();
    }

    default boolean ignoreLockingExceptions() {
        return getBooleanProperty(IGNORE_LOCKING_EXCEPTIONS_PROPERTY_NAME, IGNORE_LOCKING_EXCEPTIONS_DEFAULT_VALUE);
    }

    /**
     * @return when set to true(default), locking is enabled for workflow execution
     */
    default boolean enableWorkflowExecutionLock() {
        return getBooleanProperty(EXECUTION_LOCK_ENABLED_PROPERTY_NAME, EXECUTION_LOCK_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @return if true(default), enables task execution log indexing
     */
    default boolean isTaskExecLogIndexingEnabled() {
        return getBooleanProperty(TASKEXECLOG_INDEXING_ENABLED_PROPERTY_NAME, TASKEXECLOG_INDEXING_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @return if true(default), enables indexing persistence
     */
    default boolean isIndexingPersistenceEnabled() {
            return getBooleanProperty(INDEXING_ENABLED_PROPERTY_NAME, INDEXING_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @return the number of threads to be used within the threadpool for system task workers
     */
    default int getSystemTaskWorkerThreadCount() {
        return getIntProperty(SYSTEM_TASK_WORKER_THREAD_COUNT_PROPERTY_NAME, SYSTEM_TASK_WORKER_THREAD_COUNT_DEFAULT_VALUE);
    }

    /**
     * @return the interval (in seconds) after which a system task will be checked for completion
     */
    default int getSystemTaskWorkerCallbackSeconds() {
        return getIntProperty(SYSTEM_TASK_WORKER_CALLBACK_SECONDS_PROPERTY_NAME, SYSTEM_TASK_WORKER_CALLBACK_SECONDS_DEFAULT_VALUE);
    }

    /**
     * @return the interval (in seconds) at which system task queues will be polled by the system task workers
     */
    default int getSystemTaskWorkerPollInterval() {
        return getIntProperty(SYSTEM_TASK_WORKER_POLL_INTERVAL_PROPERTY_NAME, SYSTEM_TASK_WORKER_POLL_INTERVAL_DEFAULT_VALUE);
    }

    /**
     * @return the namespace for the system task workers to provide instance level isolation
     */
    default String getSystemTaskWorkerExecutionNamespace() {
        return getProperty(SYSTEM_TASK_WORKER_EXECUTION_NAMESPACE_PROPERTY_NAME, SYSTEM_TASK_WORKER_EXECUTION_NAMESPACE_DEFAULT_VALUE);
    }

    /**
     * @return the number of threads to be used within the threadpool for system task workers in each isolation group
     */
    default int getSystemTaskWorkerIsolatedThreadCount() {
        return getIntProperty(SYSTEM_TASK_WORKER_ISOLATED_THREAD_COUNT_PROPERTY_NAME, SYSTEM_TASK_WORKER_ISOLATED_THREAD_COUNT_DEFAULT_VALUE);
    }

    /**
     * @return the max number of system task to poll from queues
     */
    default int getSystemTaskMaxPollCount() {
        return getIntProperty(SYSTEM_TASK_MAX_POLL_COUNT_PROPERTY_NAME, SYSTEM_TASK_MAX_POLL_COUNT_DEFAULT_VALUE);
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
     *
     * @return when set to true, message from the event processing are indexed
     */
    boolean isEventMessageIndexingEnabled();

    /**
     * @return when set to true, event execution results are indexed
     */
    boolean isEventExecutionIndexingEnabled();

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

    /**
     * @return when set to true, the indexing operation to elasticsearch will be performed asynchronously
     */
    default boolean enableAsyncIndexing() {
        return getBooleanProperty(ASYNC_INDEXING_ENABLED_PROPERTY_NAME, ASYNC_INDEXING_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @return the duration of workflow execution which qualifies a workflow as a short-running workflow for async
     * updating to the index
     */
    default int getAsyncUpdateShortRunningWorkflowDuration() {
        return getIntProperty(ASYNC_UPDATE_SHORT_WORKFLOW_DURATION_PROPERTY_NAME, ASYNC_UPDATE_SHORT_WORKFLOW_DURATION_DEFAULT_VALUE);
    }

    /**
     * @return the delay with which short-running workflows will be updated in the index
     */
    default int getAsyncUpdateDelay() {
        return getIntProperty(ASYNC_UPDATE_DELAY_PROPERTY_NAME, ASYNC_UPDATE_DELAY_DEFAULT_VALUE);
    }

    default boolean getJerseyEnabled() {
        return getBooleanProperty(JERSEY_ENABLED_PROPERTY_NAME, JERSEY_ENABLED_DEFAULT_VALUE);
    }

    /**
     * @return true if owner email is mandatory for task definitions and workflow definitions
     */
    default boolean isOwnerEmailMandatory() {
            return getBooleanProperty(OWNER_EMAIL_MANDATORY_NAME, OWNER_EMAIL_MANDATORY_DEFAULT_VALUE);
    }

    /**
     * @return the refresh time for the in-memory task definition cache
     */
    default int getTaskDefRefreshTimeSecsDefaultValue() {
        return getIntProperty(TASK_DEF_REFRESH_TIME_SECS_PROPERTY_NAME, TASK_DEF_REFRESH_TIME_SECS_DEFAULT_VALUE);
    }

    /**
     * @return the refresh time for the in-memory event handler cache
     */
    default int getEventHandlerRefreshTimeSecsDefaultValue() {
        return getIntProperty(EVENT_HANDLER_REFRESH_TIME_SECS_PROPERTY_NAME,
            EVENT_HANDLER_REFRESH_TIME_SECS_DEFAULT_VALUE);
    }

    /**
     * @return The time to live in seconds of the event execution persisted. Currently, only RedisExecutionDAO supports it.
     */
    default int getEventExecutionPersistenceTTL() {
        return getIntProperty(EVENT_EXECUTION_PERSISTENCE_TTL_SECS_PROPERTY_NAME, EVENT_EXECUTION_PERSISTENCE_TTL_SECS_DEFAULT_VALUE);
    }

    default boolean isElasticSearchAutoIndexManagementEnabled() {
        return getBooleanProperty(ELASTIC_SEARCH_AUTO_INDEX_MANAGEMENT_ENABLED_PROPERTY_NAME,
            ELASTIC_SEARCH_AUTO_INDEX_MANAGEMENT_ENABLED_DEFAULT_VALUE);
    }

    /**
     * Document types are deprecated in ES6 and removed from ES7. This property can be used to disable the use of
     * specific document types with an override. This property is currently used in ES6 module.
     * <p>
     * <em>Note that this property will only take effect if
     * {@link Configuration#isElasticSearchAutoIndexManagementEnabled} is set to false and index management
     * is handled outside of this module.</em>
     */
    default String getElasticSearchDocumentTypeOverride() {
        return getProperty(ELASTIC_SEARCH_DOCUMENT_TYPE_OVERRIDE_PROPERTY_NAME,
            ELASTIC_SEARCH_DOCUMENT_TYPE_OVERRIDE_DEFAULT_VALUE);
    }

    /**
     * @return The time to live in seconds for workflow archiving module. Currently, only RedisExecutionDAO supports it.
     */
    default int getWorkflowArchivalTTL() {
        return getIntProperty(WORKFLOW_ARCHIVAL_TTL_SECS_PROPERTY_NAME, WORKFLOW_ARCHIVAL_TTL_SECS_DEFAULT_VALUE);
    }

    /**
     * @return the time to delay the archival of workflow
     */
    default int getWorkflowArchivalDelay() {
        return getIntProperty(WORKFLOW_ARCHIVAL_DELAY_SECS_PROPERTY_NAME, getAsyncUpdateDelay());
    }

    /**
     * @return the number of threads to process the delay queue in workflow archival
     */
    default int getWorkflowArchivalDelayQueueWorkerThreadCount() {
        return getIntProperty(WORKFLOW_ARCHIVAL_DELAY_QUEUE_WORKER_THREAD_COUNT_PROPERTY_NAME, WORKFLOW_ARCHIVAL_DELAY_QUEUE_WORKER_THREAD_COUNT_DEFAULT_VALUE);
    }


    /**
     * @return the number of threads to be use in Scheduler used for polling events from multiple event queues.
     * By default, a thread count equal to the number of CPU cores is chosen.
     */
    default int getEventSchedulerPollThreadCount()
    {
        return getIntProperty(EVENT_QUEUE_POLL_SCHEDULER_THREAD_COUNT_PROPERTY_NAME, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Configuration to enable {@link com.netflix.conductor.core.execution.WorkflowRepairService}, that tries to keep
     * ExecutionDAO and QueueDAO in sync, based on the task or workflow state.
     *
     * This is disabled by default; To enable, the Queueing layer must implement QueueDAO.containsMessage method.
     * @return
     */
    default boolean isWorkflowRepairServiceEnabled() {
        return getBooleanProperty(WORKFLOW_REPAIR_SERVICE_ENABLED, WORKFLOW_REPAIR_SERVICE_ENABLED_DEFAULT_VALUE);
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
        return Boolean.parseBoolean(value.trim());
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
     * @return The maximum threshold of the workflow variables payload size in KB beyond which the task changes will be rejected and the task will be marked as FAILED_WITH_TERMINAL_ERROR
     */
    Long getMaxWorkflowVariablesPayloadSizeThresholdKB();

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
        REDIS, DYNOMITE, MEMORY, REDIS_CLUSTER, MYSQL, POSTGRES, CASSANDRA, REDIS_SENTINEL
    }

    enum LOCKING_SERVER {
        NOOP_LOCK, REDIS, ZOOKEEPER, LOCAL_ONLY
    }
}
