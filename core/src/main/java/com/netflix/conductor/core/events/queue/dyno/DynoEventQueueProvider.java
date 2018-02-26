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
package com.netflix.conductor.core.events.queue.dyno;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.EventQueues;
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
	}
	
	@Override
	public ObservableQueue getQueue(String queueURI) {
		return queues.computeIfAbsent(queueURI, q -> {
			DynoObservableQueue queue = new DynoObservableQueue(queueURI, dao, config);
			return queue;
		});
	}

}
