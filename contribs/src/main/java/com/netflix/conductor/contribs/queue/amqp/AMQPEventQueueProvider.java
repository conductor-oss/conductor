
/**
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.contribs.queue.amqp;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.netflix.conductor.contribs.queue.amqp.AMQPObservableQueue.Builder;

/**
 * @author Ritu Parathody
 * 
 */
@Singleton
public class AMQPEventQueueProvider implements EventQueueProvider {

	private static Logger logger = LoggerFactory.getLogger(AMQPEventQueueProvider.class);

	protected Map<String, AMQPObservableQueue> queues = new ConcurrentHashMap<>();

	private final boolean useExchange;

	private final Configuration config;

	@Inject
	public AMQPEventQueueProvider(Configuration config, boolean useExchange) {
		this.config = config;
		this.useExchange = useExchange;
	}

	@Override
	public ObservableQueue getQueue(String queueURI) {
		if (logger.isInfoEnabled()) {
			logger.info("Retrieve queue with URI {}", queueURI);
		}
		// Build the queue with the inner Builder class of AMQPObservableQueue
		final AMQPObservableQueue queue = queues.computeIfAbsent(queueURI,
				q -> new Builder(config).build(useExchange, q));
		return queue;
	}
}