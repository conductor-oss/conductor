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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.events.queue.dyno.DynoEventQueueProvider;
import com.netflix.conductor.service.MetadataService;

/**
 * @author Viren
 * Event Processor is used to dispatch actions based on the incoming events to execution queue.
 */
@Singleton
public class EventProcessor {

	private static Logger logger = LoggerFactory.getLogger(EventProcessor.class);
	
	private MetadataService ms;
	
	private ObservableQueue actionQueue;
	
	private ObjectMapper om;
	
	private Map<String, ObservableQueue> queuesMap = new ConcurrentHashMap<>();
	
	@Inject
	public EventProcessor(DynoEventQueueProvider queueProvider, MetadataService ms, ObjectMapper om) {
		this.actionQueue = queueProvider.getQueue(ActionProcessor.queueName);
		this.ms = ms;
		this.om = om;
		refresh();
		Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> refresh(), 60, 60, TimeUnit.SECONDS);
	}
	
	/**
	 * 
	 * @return Returns a map of queues which are active.  Key is event name and value is queue URI
	 */
	public Map<String, String> getQueues() {
		Map<String, String> queues = new HashMap<>();
		queuesMap.entrySet().stream().forEach(q -> queues.put(q.getKey(), q.getValue().getURI()));
		return queues;
	}
	
	public Map<String, Map<String, Long>> getQueueSizes() {
		Map<String, Map<String, Long>> queues = new HashMap<>();
		queuesMap.entrySet().stream().forEach(q -> {
			Map<String, Long> size = new HashMap<>();
			size.put(q.getValue().getURI(), q.getValue().size());
			queues.put(q.getKey(), size);
		});
		return queues;
	}
	
	private void refresh() {
		Set<String> events = ms.getEventHandlers().stream().map(eh -> eh.getEvent()).collect(Collectors.toSet());
		logger.info("Got events: {}", events);
		List<ObservableQueue> created = new LinkedList<>();
		events.stream().forEach(event -> queuesMap.computeIfAbsent(event, s -> {
			ObservableQueue q = EventQueues.getQueue(event);
			logger.info("Got the Queue {} for event {}", q, event);
			created.add(q);
			return q;
		}));
		if(!created.isEmpty()) {
			created.forEach(queue -> listen(queue));	
		}
	}
	
	private void listen(ObservableQueue queue) {
		queue.observe().subscribe((Message msg) -> handle(queue, msg));
	}
	
	private void handle(ObservableQueue queue, Message msg) {
		try {
			
			logger.info("Got Message: " + msg.getPayload());
			List<Message> messages = new LinkedList<>();
			int i = 0;
			String event = queue.getType() + ":" + queue.getURI();
			List<EventHandler> handlers = ms.getEventHandlersForEvent(event, true);
			for(EventHandler handler : handlers) {
				List<Action> actions = handler.getActions();
				for(Action action : actions) {
					String id = msg.getId() + ":" + i++;
					action.setEvent(handler.getEvent());
					action.setHandlerName(handler.getName());
					String payload = om.writeValueAsString(action);
					String receipt = id;
					Message message = new Message(id, payload, receipt);
					messages.add(message);
				}
			}
			logger.info("Publishing Actions: {}", messages);
			actionQueue.publish(messages);
			queue.ack(Arrays.asList(msg));
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}
	
}
