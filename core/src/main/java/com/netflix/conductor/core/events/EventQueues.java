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
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * @author Viren
 * Static holders for internal event queues
 */
public class EventQueues {
	
	private static Logger logger = LoggerFactory.getLogger(EventQueues.class);
	
	public enum QueueType {
		sqs, conductor
	}
	
	private static Map<QueueType, EventQueueProvider> providers = new HashMap<>();

	private EventQueues() {
		
	}

	public static void registerProvider(QueueType type, EventQueueProvider provider) {
		providers.put(type, provider);
	}
	
	public static Map<QueueType, EventQueueProvider> providers() {
		return providers;
	}
	
	public static ObservableQueue getQueue(String event) {
		String typeVal = event.substring(0, event.indexOf(':'));
		String queueURI = event.substring(event.indexOf(':') + 1);
		QueueType type = QueueType.valueOf(typeVal);
		EventQueueProvider provider = providers.get(type);
		if(provider != null) {
			try {
				return provider.getQueue(queueURI);
			} catch(Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		return null;
		
	}
}
