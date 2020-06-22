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

package com.netflix.conductor.contribs;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoMap;
import com.google.inject.multibindings.StringMapKey;
import com.google.inject.name.Named;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.contribs.queue.amqp.AMQPEventQueueProvider;
import com.netflix.conductor.contribs.queue.amqp.AMQPObservableQueue;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import static com.netflix.conductor.core.events.EventQueues.EVENT_QUEUE_PROVIDERS_QUALIFIER;

/**
 * configuration file:
 * 
 * <pre>
 * conductor.additional.modules = com.netflix.conductor.contribs.AMQPModule
 * </pre>
 * 
 *
 * @author Ritu Parathody
 */
public class AMQPModule extends AbstractModule {
	private static final boolean USE_EXCHANGE_BY_DEFAULT = true;

	private static Logger logger = LoggerFactory.getLogger(AMQPModule.class);

	@Override
	protected void configure() {
		// TODO Auto-generated method stub

	}

	@ProvidesIntoMap
	@StringMapKey("amqp_queue")
	@Singleton
	@Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
	public EventQueueProvider getAMQQueueEventQueueProvider(Configuration config) {
		return new AMQPEventQueueProvider(config, false);
	}

	@ProvidesIntoMap
	@StringMapKey("amqp_exchange")
	@Singleton
	@Named(EVENT_QUEUE_PROVIDERS_QUALIFIER)
	public EventQueueProvider getAMQExchangeEventQueueProvider(Configuration config) {
		return new AMQPEventQueueProvider(config, true);
	}

	@Provides
	public Map<Task.Status, ObservableQueue> getQueues(Configuration config) {
		String stack = "";
		if (config.getStack() != null && config.getStack().length() > 0) {
			stack = config.getStack() + "_";
		}
		final boolean useExchange = config.getBooleanProperty("workflow.listener.queue.useExchange",
				USE_EXCHANGE_BY_DEFAULT);
		Task.Status[] statuses = new Task.Status[] { Task.Status.COMPLETED, Task.Status.FAILED };
		Map<Task.Status, ObservableQueue> queues = new HashMap<>();
		for (Task.Status status : statuses) {
			String queueName = config.getProperty("workflow.listener.queue.prefix",
					config.getAppId() + "_amqp_notify_" + stack + status.name());
			final ObservableQueue queue = new AMQPObservableQueue.Builder(config).build(useExchange, queueName);
			queues.put(status, queue);
		}

		return queues;
	}
}
