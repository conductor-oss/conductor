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

import com.netflix.conductor.core.events.EventQueueProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EventQueueProviders implements BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventQueueProviders.class);
    private final Map<String, EventQueueProvider> eventQueueProviders = new HashMap<>();

    @SuppressWarnings("NullableProblems")
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof EventQueueProvider) {
            final EventQueueProvider eventQueueProvider = (EventQueueProvider) bean;
            Optional.ofNullable(eventQueueProvider.getQueueType())
                .ifPresent(queueType -> {
                    LOGGER.info("Adding Event Queue Provider bean: {} for queueType: {} to eventQueueProviders",
                        beanName, queueType);
                    eventQueueProviders.put(queueType, eventQueueProvider);
                });
        }
        return bean;
    }

    public Map<String, EventQueueProvider> getEventQueueProviders() {
        return eventQueueProviders;
    }
}
