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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.core.events.ActionProcessor;
import com.netflix.conductor.core.events.EventProcessor;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.SimpleActionProcessor;
import com.netflix.conductor.core.events.SimpleEventProcessor;
import com.netflix.conductor.core.events.queue.ConductorEventQueueProvider;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.core.utils.ParametersUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration(proxyBeanMethods = false)
public class EventConfiguration {

    @Bean
    public ActionProcessor actionProcessor(WorkflowExecutor workflowExecutor, ParametersUtils parametersUtils,
        JsonUtils jsonUtils) {
        return new SimpleActionProcessor(workflowExecutor, parametersUtils, jsonUtils);
    }

    @Bean
    public EventProcessor eventProcessor(ExecutionService executionService, MetadataService metadataService,
        ActionProcessor actionProcessor, EventQueues eventQueues, JsonUtils jsonUtils, ConductorProperties properties,
        ObjectMapper objectMapper) {
        return new SimpleEventProcessor(executionService, metadataService, actionProcessor, eventQueues, jsonUtils,
            properties, objectMapper);
    }

    @Bean
    public Scheduler scheduler(ConductorProperties properties) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("event-queue-poll-scheduler-thread-%d")
            .build();
        Executor executorService = Executors
            .newFixedThreadPool(properties.getEventSchedulerPollThreadCount(), threadFactory);

        return Schedulers.from(executorService);
    }

    @Bean
    public EventQueueProvider conductorEventQueueProvider(QueueDAO queueDAO, ConductorProperties properties,
        Scheduler scheduler) {
        return new ConductorEventQueueProvider(queueDAO, properties, scheduler);
    }
}
