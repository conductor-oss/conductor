/**
 * 
 */
package com.netflix.conductor.core.events.queue.dyno;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.events.EventQueues.QueueType;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.dao.QueueDAO;

/**
 * @author Viren
 *
 */
@Singleton
public class DynoEventQueueProvider implements EventQueueProvider {

	private Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
	
	private QueueDAO dao;
	
	private Configuration config;
	
	@Inject
	public DynoEventQueueProvider(QueueDAO dao, Configuration config) {
		this.dao = dao;
		this.config = config;
		EventQueues.registerProvider(QueueType.conductor, this);
	}
	
	@Override
	public ObservableQueue getQueue(String queueURI) {
		System.out.println("queueURI: " + queueURI);
		return queues.computeIfAbsent(queueURI, q -> {
			DynoObservableQueue queue = new DynoObservableQueue(queueURI, dao, config);
			return queue;
		});
	}

}
