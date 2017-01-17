/**
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.tests.utils;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.DynoProxy;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.redis.utils.JedisMock;
import com.netflix.conductor.server.ConductorConfig;
import com.netflix.dyno.queues.ShardSupplier;

import redis.clients.jedis.JedisCommands;

/**
 * @author Viren
 *
 */
public class TestModule extends AbstractModule {
	
	private int maxThreads = 50;
	
	private ExecutorService es;
	
	@Override
	protected void configure() {
		
		configureExecutorService();
		ConductorConfig config = new ConductorConfig();
		bind(Configuration.class).toInstance(config);
		JedisCommands jedisMock = new JedisMock();
		

		DynoQueueDAO queueDao = new DynoQueueDAO(jedisMock, jedisMock, new ShardSupplier() {
			
			@Override
			public Set<String> getQueueShards() {
				return Arrays.asList("a").stream().collect(Collectors.toSet());
			}
			
			@Override
			public String getCurrentShard() {
				return "a";
			}
		}, config);
		
		bind(MetadataDAO.class).to(RedisMetadataDAO.class);
		bind(ExecutionDAO.class).to(RedisExecutionDAO.class);
		bind(DynoQueueDAO.class).toInstance(queueDao);
		bind(QueueDAO.class).to(DynoQueueDAO.class);
		bind(IndexDAO.class).to(MockIndexDAO.class);
		
		DynoProxy proxy = new DynoProxy(jedisMock);
		bind(DynoProxy.class).toInstance(proxy);
	}
	
	@Provides
	public ExecutorService getExecutorService(){
		return this.es;
	}
	
	private void configureExecutorService(){
		AtomicInteger count = new AtomicInteger(0);
		this.es = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setName("workflow-worker-" + count.getAndIncrement());
				return t;
			}
		});
	}
}
