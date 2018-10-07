/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.events;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.ParametersUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Viren
 * Holders for internal event queues
 */
@Singleton
public class EventQueues {
	public static final String EVENT_QUEUE_PROVIDERS_QUALIFIER = "EventQueueProviders";

	private static final Logger logger = LoggerFactory.getLogger(EventQueues.class);

	private final ParametersUtils parametersUtils;

	private final Map<String, EventQueueProvider> providers;

	@Inject
	public EventQueues(@Named(EVENT_QUEUE_PROVIDERS_QUALIFIER) Map<String, EventQueueProvider> providers, ParametersUtils parametersUtils) {
	    this.providers = providers;
	    this.parametersUtils = parametersUtils;
	}

	public List<String> getProviders() {
		return providers.values().stream()
				.map(p -> p.getClass().getName())
				.collect(Collectors.toList());
	}

	public ObservableQueue getQueue(String eventType) {
		String event = parametersUtils.replace(eventType).toString();
		int index = event.indexOf(':');
		if (index == -1) {
			logger.error("Queue cannot be configured for illegal event: {}", event);
			throw new IllegalArgumentException("Illegal event " + event);
		}

		String type = event.substring(0, index);
		String queueURI = event.substring(index + 1);
		EventQueueProvider provider = providers.get(type);
		if (provider != null) {
			return provider.getQueue(queueURI);
		} else {
			logger.error("Queue {} is not configured for event:{}", type, eventType);
			throw new IllegalArgumentException("Unknown queue type " + type);
		}
	}
}
