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
package com.netflix.conductor.core.config;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.common.utils.ExternalPayloadStorage;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.core.listener.WorkflowStatusListenerStub;
import com.netflix.conductor.core.storage.DummyPayloadStorage;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.core.sync.NoopLock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConductorCoreConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConductorCoreConfiguration.class);

    @ConditionalOnProperty(prefix = "workflow", name = "decider.locking.server", havingValue = "noop_lock", matchIfMissing = true)
    @Bean
    public Lock provideLock() {
        return new NoopLock();
    }

    @ConditionalOnProperty(prefix = "workflow", name = "external.payload.storage", havingValue = "DUMMY", matchIfMissing = true)
    @Bean
    public ExternalPayloadStorage dummyExternalPayloadStorage() {
        LOGGER.info("Initialized dummy payload storage");
        return new DummyPayloadStorage();
    }

    @ConditionalOnProperty(prefix = "workflow", name = "status.listener.type", havingValue = "stub", matchIfMissing = true)
    @Bean
    public WorkflowStatusListener workflowStatusListener() {
        return new WorkflowStatusListenerStub();
    }

    @Bean
    public ExecutorService executorService(ConductorProperties conductorProperties) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("conductor-worker-%d")
            .setDaemon(true)
            .build();
        return Executors.newFixedThreadPool(conductorProperties.getExecutorServiceMaxThreads(), threadFactory);
    }

    @Bean
    @Qualifier("taskProcessorsMap")
    public Map<String, TaskMapper> getTaskMappers(List<TaskMapper> taskMappers) {
        return taskMappers.stream()
            .collect(Collectors.toMap(t -> t.getTaskType().name(), t -> t));
    }

    @Bean
    @Qualifier(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public Map<String, EventQueueProvider> getEventQueueProviders(List<EventQueueProvider> eventQueueProviders) {
        return eventQueueProviders.stream()
            .collect(Collectors.toMap(EventQueueProvider::getQueueType, e -> e));
    }
}
