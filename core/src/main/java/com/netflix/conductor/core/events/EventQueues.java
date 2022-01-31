/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.core.events;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.utils.ParametersUtils;

/** Holders for internal event queues */
@Component
public class EventQueues {

    public static final String EVENT_QUEUE_PROVIDERS_QUALIFIER = "EventQueueProviders";

    private static final Logger LOGGER = LoggerFactory.getLogger(EventQueues.class);

    private final ParametersUtils parametersUtils;
    private final Map<String, EventQueueProvider> providers;

    @Autowired
    public EventQueues(
            @Qualifier(EVENT_QUEUE_PROVIDERS_QUALIFIER) Map<String, EventQueueProvider> providers,
            ParametersUtils parametersUtils) {
        this.providers = providers;
        this.parametersUtils = parametersUtils;
    }

    public List<String> getProviders() {
        return providers.values().stream()
                .map(p -> p.getClass().getName())
                .collect(Collectors.toList());
    }

    @NonNull
    public ObservableQueue getQueue(String eventType) {
        String event = parametersUtils.replace(eventType).toString();
        int index = event.indexOf(':');
        if (index == -1) {
            throw new IllegalArgumentException("Illegal event " + event);
        }

        String type = event.substring(0, index);
        String queueURI = event.substring(index + 1);
        EventQueueProvider provider = providers.get(type);
        if (provider != null) {
            return provider.getQueue(queueURI);
        } else {
            throw new IllegalArgumentException("Unknown queue type " + type);
        }
    }
}
