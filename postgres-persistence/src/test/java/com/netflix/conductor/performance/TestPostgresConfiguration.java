package com.netflix.conductor.performance;

import com.google.inject.AbstractModule;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.execution.TestConfiguration;
import com.netflix.conductor.postgres.PostgresConfiguration;
import java.util.List;
import java.util.Map;

class TestPostgresConfiguration implements PostgresConfiguration {
    private final TestConfiguration testConfiguration;
    private final String jdbcUrl;
    private final int connectionMax;
    private final int connecionIdle;

    public TestPostgresConfiguration(TestConfiguration testConfiguration, String jdbcUrl, int maxConns, int connecionIdle) {
        this.jdbcUrl = jdbcUrl;
        this.testConfiguration = testConfiguration;
        this.connectionMax = maxConns;
        this.connecionIdle = connecionIdle;
    }

    @Override
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public String getJdbcUserName() {
        return "postgres";
    }

    @Override
    public String getJdbcPassword() {
        return "postgres";
    }


    @Override
    public String getTransactionIsolationLevel() {
        return "TRANSACTION_REPEATABLE_READ";
//            return "TRANSACTION_SERIALIZABLE";
    }

    @Override
    public int getConnectionPoolMaxSize() {
        return connectionMax;
    }

    @Override
    public int getConnectionPoolMinIdle() {
        return connecionIdle;
    }

    @Override
    public int getSweepFrequency() {
        return testConfiguration.getSweepFrequency();
    }

    @Override
    public boolean disableSweep() {
        return testConfiguration.disableSweep();
    }

    @Override
    public boolean disableAsyncWorkers() {
        return testConfiguration.disableAsyncWorkers();
    }

    @Override
    public boolean isEventMessageIndexingEnabled() {
        return testConfiguration.isEventMessageIndexingEnabled();
    }

    @Override
    public boolean isEventExecutionIndexingEnabled() {
        return testConfiguration.isEventExecutionIndexingEnabled();
    }

    @Override
    public String getServerId() {
        return testConfiguration.getServerId();
    }

    @Override
    public String getEnvironment() {
        return testConfiguration.getEnvironment();
    }

    @Override
    public String getStack() {
        return testConfiguration.getStack();
    }

    @Override
    public String getAppId() {
        return testConfiguration.getAppId();
    }

    @Override
    public boolean enableAsyncIndexing() {
        return testConfiguration.enableAsyncIndexing();
    }

    @Override
    public String getProperty(String string, String def) {
        return testConfiguration.getProperty(string, def);
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return testConfiguration.getBooleanProperty(name, defaultValue);
    }

    @Override
    public String getAvailabilityZone() {
        return testConfiguration.getAvailabilityZone();
    }

    @Override
    public int getIntProperty(String string, int def) {
        return testConfiguration.getIntProperty(string, def);
    }

    @Override
    public String getRegion() {
        return testConfiguration.getRegion();
    }

    @Override
    public Long getWorkflowInputPayloadSizeThresholdKB() {
        return testConfiguration.getWorkflowInputPayloadSizeThresholdKB();
    }

    @Override
    public Long getMaxWorkflowInputPayloadSizeThresholdKB() {
        return testConfiguration.getMaxWorkflowInputPayloadSizeThresholdKB();
    }

    @Override
    public Long getWorkflowOutputPayloadSizeThresholdKB() {
        return testConfiguration.getWorkflowOutputPayloadSizeThresholdKB();
    }

    @Override
    public Long getMaxWorkflowOutputPayloadSizeThresholdKB() {
        return testConfiguration.getMaxWorkflowOutputPayloadSizeThresholdKB();
    }

    @Override
    public Long getMaxWorkflowVariablesPayloadSizeThresholdKB() {
        return testConfiguration.getMaxWorkflowVariablesPayloadSizeThresholdKB();
    }

    @Override
    public Long getTaskInputPayloadSizeThresholdKB() {
        return testConfiguration.getTaskInputPayloadSizeThresholdKB();
    }

    @Override
    public Long getMaxTaskInputPayloadSizeThresholdKB() {
        return testConfiguration.getMaxTaskInputPayloadSizeThresholdKB();
    }

    @Override
    public Long getTaskOutputPayloadSizeThresholdKB() {
        return testConfiguration.getTaskOutputPayloadSizeThresholdKB();
    }

    @Override
    public Long getMaxTaskOutputPayloadSizeThresholdKB() {
        return testConfiguration.getMaxTaskOutputPayloadSizeThresholdKB();
    }

    @Override
    public Map<String, Object> getAll() {
        return testConfiguration.getAll();
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        return testConfiguration.getLongProperty(name, defaultValue);
    }

    @Override
    public LOCKING_SERVER getLockingServer() {
        return testConfiguration.getLockingServer();
    }

    @Override
    public String getLockingServerString() {
        return testConfiguration.getLockingServerString();
    }

    @Override
    public boolean ignoreLockingExceptions() {
        return testConfiguration.ignoreLockingExceptions();
    }

    @Override
    public boolean enableWorkflowExecutionLock() {
        return testConfiguration.enableWorkflowExecutionLock();
    }

    @Override
    public boolean isTaskExecLogIndexingEnabled() {
        return testConfiguration.isTaskExecLogIndexingEnabled();
    }

    @Override
    public boolean isIndexingPersistenceEnabled() {
        return testConfiguration.isIndexingPersistenceEnabled();
    }

    @Override
    public int getSystemTaskWorkerThreadCount() {
        return testConfiguration.getSystemTaskWorkerThreadCount();
    }

    @Override
    public int getSystemTaskWorkerCallbackSeconds() {
        return testConfiguration.getSystemTaskWorkerCallbackSeconds();
    }

    @Override
    public int getSystemTaskWorkerPollInterval() {
        return testConfiguration.getSystemTaskWorkerPollInterval();
    }

    @Override
    public String getSystemTaskWorkerExecutionNamespace() {
        return testConfiguration.getSystemTaskWorkerExecutionNamespace();
    }

    @Override
    public int getSystemTaskWorkerIsolatedThreadCount() {
        return testConfiguration.getSystemTaskWorkerIsolatedThreadCount();
    }

    @Override
    public int getSystemTaskMaxPollCount() {
        return testConfiguration.getSystemTaskMaxPollCount();
    }

    @Override
    public boolean isOwnerEmailMandatory() {
        return testConfiguration.isOwnerEmailMandatory();
    }

    @Override
    public int getTaskDefRefreshTimeSecsDefaultValue() {
        return testConfiguration.getTaskDefRefreshTimeSecsDefaultValue();
    }

    @Override
    public int getEventHandlerRefreshTimeSecsDefaultValue() {
        return testConfiguration.getEventHandlerRefreshTimeSecsDefaultValue();
    }

    @Override
    public int getEventExecutionPersistenceTTL() {
        return testConfiguration.getEventExecutionPersistenceTTL();
    }

    @Override
    public boolean isElasticSearchAutoIndexManagementEnabled() {
        return testConfiguration.isElasticSearchAutoIndexManagementEnabled();
    }

    @Override
    public String getElasticSearchDocumentTypeOverride() {
        return testConfiguration.getElasticSearchDocumentTypeOverride();
    }

    @Override
    public int getWorkflowArchivalTTL() {
        return testConfiguration.getWorkflowArchivalTTL();
    }

    @Override
    public int getWorkflowArchivalDelay() {
        return testConfiguration.getWorkflowArchivalDelay();
    }

    @Override
    public int getWorkflowArchivalDelayQueueWorkerThreadCount() {
        return testConfiguration.getWorkflowArchivalDelayQueueWorkerThreadCount();
    }

    @Override
    public int getEventSchedulerPollThreadCount() {
        return testConfiguration.getEventSchedulerPollThreadCount();
    }

    @Override
    public boolean isWorkflowRepairServiceEnabled() {
        return testConfiguration.isWorkflowRepairServiceEnabled();
    }

    @Override
    public DB getDB() {
        return testConfiguration.getDB();
    }

    @Override
    public String getDBString() {
        return testConfiguration.getDBString();
    }

    @Override
    public int getAsyncUpdateShortRunningWorkflowDuration() {
        return testConfiguration.getAsyncUpdateShortRunningWorkflowDuration();
    }

    @Override
    public int getAsyncUpdateDelay() {
        return testConfiguration.getAsyncUpdateDelay();
    }

    @Override
    public boolean getJerseyEnabled() {
        return testConfiguration.getJerseyEnabled();
    }

    @Override
    public boolean getBoolProperty(String name, boolean defaultValue) {
        return testConfiguration.getBoolProperty(name, defaultValue);
    }

    @Override
    public List<AbstractModule> getAdditionalModules() {
        return testConfiguration.getAdditionalModules();
    }
}
