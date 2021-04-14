/*
 *
 *  * Copyright 2021 Netflix, Inc.
 *  * <p>
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  * <p>
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  * <p>
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *
 */
package com.netflix.conductor.core.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import rx.Scheduler;
import rx.schedulers.Schedulers;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableAsync
public class SchedulerConfiguration implements SchedulingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerConfiguration.class);
    public static final String SWEEPER_EXECUTOR_NAME = "WorkflowSweeperExecutor";

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
            .newFixedThreadPool(properties.getEventQueueSchedulerPollThreadCount(), threadFactory);

        return Schedulers.from(executorService);
    }

    @Bean(SWEEPER_EXECUTOR_NAME)
    public Executor sweeperExecutor(ConductorProperties properties) {
        if (properties.getSweeperThreadCount() <= 0) {
            throw new IllegalStateException("Cannot set workflow sweeper thread count to <=0. To disable workflow "
                + "sweeper, set conductor.workflow-reconciler.enabled=false.");
        }
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("sweeper-thread-%d")
            .build();
        return Executors.newFixedThreadPool(properties.getSweeperThreadCount(), threadFactory);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(2); // equal to the number of scheduled jobs
        threadPoolTaskScheduler.setThreadNamePrefix("scheduled-task-pool-");
        threadPoolTaskScheduler.initialize();
        taskRegistrar.setTaskScheduler(threadPoolTaskScheduler);
    }
}
