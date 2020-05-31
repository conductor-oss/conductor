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
package com.netflix.conductor.core.events.queue;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.conductor.core.config.Configuration;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Provides the Scheduler to be used for polling the messages from the event queues.
 */
public class EventPollSchedulerProvider implements Provider<Scheduler> {

    private final Configuration configuration;

    @Inject
    public EventPollSchedulerProvider(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Scheduler get() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("event-queue-poll-scheduler-thread-%d")
                .build();
        Executor executorService = Executors.newFixedThreadPool(configuration.getEventSchedulerPollThreadCount(), threadFactory);

        return Schedulers.from(executorService);
    }
}
