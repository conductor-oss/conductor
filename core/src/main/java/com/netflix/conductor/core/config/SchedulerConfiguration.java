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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration(proxyBeanMethods = false)
public class SchedulerConfiguration {

    /**
     * Used by some {@link com.netflix.conductor.core.events.queue.ObservableQueue} implementations.
     *
     * @see com.netflix.conductor.core.events.queue.ConductorObservableQueue
     */
    @Bean
    public Scheduler scheduler(ConductorProperties properties) {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("event-queue-poll-scheduler-thread-%d")
                .build();
        Executor executorService = Executors
                .newFixedThreadPool(properties.getEventSchedulerPollThreadCount(), threadFactory);

        return Schedulers.from(executorService);
    }
}
