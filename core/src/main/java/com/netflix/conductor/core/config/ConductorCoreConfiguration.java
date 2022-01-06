/*
 * Copyright 2021 Netflix, Inc.
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListenerStub;
import com.netflix.conductor.core.storage.DummyPayloadStorage;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.core.sync.NoopLock;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;
import static com.netflix.conductor.core.execution.tasks.SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER;

import static java.util.function.Function.identity;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConductorProperties.class)
public class ConductorCoreConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorCoreConfiguration.class);

    @ConditionalOnProperty(
            name = "conductor.workflow-execution-lock.type",
            havingValue = "noop_lock",
            matchIfMissing = true)
    @Bean
    public Lock provideLock() {
        return new NoopLock();
    }

    @ConditionalOnProperty(
            name = "conductor.external-payload-storage.type",
            havingValue = "dummy",
            matchIfMissing = true)
    @Bean
    public ExternalPayloadStorage dummyExternalPayloadStorage() {
        LOGGER.info("Initialized dummy payload storage!");
        return new DummyPayloadStorage();
    }

    @ConditionalOnProperty(
            name = "conductor.workflow-status-listener.type",
            havingValue = "stub",
            matchIfMissing = true)
    @Bean
    public WorkflowStatusListener workflowStatusListener() {
        return new WorkflowStatusListenerStub();
    }

    @Bean
    public ExecutorService executorService(ConductorProperties conductorProperties) {
        ThreadFactory threadFactory =
                new ThreadFactoryBuilder()
                        .setNameFormat("conductor-worker-%d")
                        .setDaemon(true)
                        .build();
        return Executors.newFixedThreadPool(
                conductorProperties.getExecutorServiceMaxThreadCount(), threadFactory);
    }

    @Bean
    @Qualifier("taskProcessorsMap")
    public Map<TaskType, TaskMapper> getTaskMappers(List<TaskMapper> taskMappers) {
        return taskMappers.stream().collect(Collectors.toMap(TaskMapper::getTaskType, identity()));
    }

    @Bean
    @Qualifier(ASYNC_SYSTEM_TASKS_QUALIFIER)
    public Set<WorkflowSystemTask> asyncSystemTasks(Set<WorkflowSystemTask> allSystemTasks) {
        return allSystemTasks.stream()
                .filter(WorkflowSystemTask::isAsync)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Bean
    @Qualifier(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public Map<String, EventQueueProvider> getEventQueueProviders(
            List<EventQueueProvider> eventQueueProviders) {
        return eventQueueProviders.stream()
                .collect(Collectors.toMap(EventQueueProvider::getQueueType, identity()));
    }
}
