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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.ParametersUtils;

/**
 * @author Viren
 * Static holders for internal event queues
 */
public class EventQueues {
	
	private static Logger logger = LoggerFactory.getLogger(EventQueues.class);
	
	private static ParametersUtils pu = new ParametersUtils();

	private static Map<String, EventQueueProvider> providers = new HashMap<>();

	private EventQueues() {
		
	}

	public static void registerProvider(String type, EventQueueProvider provider) {
		providers.put(type, provider);
	}
	
	public static List<String> providers() {
		return providers.values().stream().map(p -> p.getClass().getName()).collect(Collectors.toList());
	}
	
	public static ObservableQueue getQueue(String eventt, boolean throwException) {
		String event = pu.replace(eventt).toString();
		String type = event.substring(0, event.indexOf(':'));
		String queueURI = event.substring(event.indexOf(':') + 1);
		EventQueueProvider provider = providers.get(type);
		if(provider != null) {
			try {
				return provider.getQueue(queueURI);
			} catch(Exception e) {
				logger.error(e.getMessage(), e);
				if(throwException) {
					throw e;
				}
			}
		} else {
			throw new IllegalArgumentException("Unknown queue type " + type);
		}
		return null;
		
	}
}
