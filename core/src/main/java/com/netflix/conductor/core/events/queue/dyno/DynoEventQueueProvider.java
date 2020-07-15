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

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.dao.QueueDAO;
import rx.Scheduler;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Viren
 *
 */
@Singleton
public class DynoEventQueueProvider implements EventQueueProvider {

	private final Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
	private final QueueDAO queueDAO;
	private final Configuration config;
	private final  Scheduler scheduler;
	
	@Inject
	public DynoEventQueueProvider(QueueDAO queueDAO, Configuration config, Scheduler scheduler) {
		this.queueDAO = queueDAO;
		this.config = config;
		this.scheduler = scheduler;
	}
	
	@Override
	public ObservableQueue getQueue(String queueURI) {
		return queues.computeIfAbsent(queueURI, q -> new DynoObservableQueue(queueURI, queueDAO, config, scheduler));
	}
}
