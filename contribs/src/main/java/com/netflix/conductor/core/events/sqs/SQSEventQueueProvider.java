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
package com.netflix.conductor.core.events.sqs;

import com.amazonaws.services.sqs.AmazonSQSClient;
import com.netflix.conductor.contribs.queue.sqs.SQSObservableQueue;
import com.netflix.conductor.contribs.queue.sqs.SQSObservableQueue.Builder;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.EventQueueProvider;
import com.netflix.conductor.core.events.queue.ObservableQueue;
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
public class SQSEventQueueProvider implements EventQueueProvider {

	private final Map<String, ObservableQueue> queues = new ConcurrentHashMap<>();
	private final AmazonSQSClient client;
	private final int batchSize;
	private final int pollTimeInMS;
	private final int visibilityTimeoutInSeconds;
	private final Scheduler scheduler;
	
	@Inject
	public SQSEventQueueProvider(AmazonSQSClient client, Configuration config, Scheduler scheduler) {
		this.client = client;
		this.batchSize = config.getIntProperty("workflow.event.queues.sqs.batchSize", 1);
		this.pollTimeInMS = config.getIntProperty("workflow.event.queues.sqs.pollTimeInMS", 100);
		this.visibilityTimeoutInSeconds = config.getIntProperty("workflow.event.queues.sqs.visibilityTimeoutInSeconds", 60);
		this.scheduler = scheduler;
	}
	
	@Override
	public ObservableQueue getQueue(String queueURI) {
		return queues.computeIfAbsent(queueURI, q -> {
			Builder builder = new SQSObservableQueue.Builder();
			return builder.withBatchSize(this.batchSize)
					.withClient(client)
					.withPollTimeInMS(this.pollTimeInMS)
					.withQueueName(queueURI)
					.withVisibilityTimeout(this.visibilityTimeoutInSeconds)
					.withScheduler(scheduler)
					.build();
		});
	}
}
