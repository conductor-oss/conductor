/**
 * 
 */
package com.netflix.conductor.core.events;

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.core.events.queue.ObservableQueue;

/**
 * @author Viren
 * Static holders for internal event queues
 */
public class EventQueues {
	
	public enum QueueType {
		sqs, conductor
	}
	
	private static Map<QueueType, EventQueueProvider> providers = new HashMap<>();

	private EventQueues() {
		
	}

	public static void registerProvider(QueueType type, EventQueueProvider provider) {
		providers.put(type, provider);
	}
	
	public static ObservableQueue getQueue(String event) {
		String typeVal = event.substring(0, event.indexOf(':'));
		String queueURI = event.substring(event.indexOf(':') + 1);
		QueueType type = QueueType.valueOf(typeVal);
		EventQueueProvider provider = providers.get(type);
		return provider.getQueue(queueURI);
	}
}
