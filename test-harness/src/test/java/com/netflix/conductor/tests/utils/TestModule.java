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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.ExecutionDAO;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.dynomite.DynoProxy;
import com.netflix.conductor.dao.dynomite.RedisExecutionDAO;
import com.netflix.conductor.dao.dynomite.RedisMetadataDAO;
import com.netflix.conductor.dao.dynomite.queue.DynoQueueDAO;
import com.netflix.conductor.dao.index.ElasticSearchDAO;
import com.netflix.conductor.dao.index.ElasticsearchModule;
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
		
		bind(Configuration.class).to(TestConfiguration.class);
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
		}, new TestConfiguration(), 100);
		
		install(new ElasticsearchModule());
		bind(MetadataDAO.class).to(RedisMetadataDAO.class);
		bind(ExecutionDAO.class).to(RedisExecutionDAO.class);
		bind(DynoQueueDAO.class).toInstance(queueDao);
		bind(QueueDAO.class).to(DynoQueueDAO.class);
		bind(IndexDAO.class).to(ElasticSearchDAO.class);
		
		DynoProxy proxy = new DynoProxy(jedisMock);
		bind(DynoProxy.class).toInstance(proxy);
		
		bind(String.class).annotatedWith(Names.named("workflow.dyno.keyspace.domain")).toInstance(System.getProperty("user.name"));

		bindListener(Matchers.any(), new TypeListener() {
			
			@Override
			public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
				encounter.register(new InjectionListener<I>() {

					@Override
					public void afterInjection(I obj) {
						Method[] methods = obj.getClass().getMethods();
						for(Method method : methods){
							PostConstruct[] annotations = method.getAnnotationsByType(PostConstruct.class);
							if(annotations != null && annotations.length > 0){
								try {
									method.invoke(obj);
								} catch (Exception e) {
									e.printStackTrace();
								} 
							}
						}
					}
				});
				
			}
		});
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
