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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

@Configuration
public class EventQueueConfiguration {

    @Bean
    public EventQueueProviders eventQueueProviders() {
        return new EventQueueProviders();
    }

    @Bean
    @Qualifier(EVENT_QUEUE_PROVIDERS_QUALIFIER)
    public Map<String, EventQueueProvider> getEventQueueProviders(EventQueueProviders eventQueueProviders) {
        return eventQueueProviders.getEventQueueProviders();
    }
}
