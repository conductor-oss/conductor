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
package com.netflix.conductor.server;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.netflix.conductor.contribs.http.HttpTask;
import com.netflix.conductor.contribs.http.RestClientManager;
import com.netflix.conductor.contribs.json.JsonJqTransform;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.CoreModule;
import com.netflix.conductor.dao.RedisWorkflowModule;
import com.netflix.conductor.dao.es.index.ElasticSearchModule;
import com.netflix.conductor.dao.es5.index.ElasticSearchModuleV5;
import com.netflix.conductor.dao.mysql.MySQLWorkflowModule;
import com.netflix.dyno.connectionpool.HostSupplier;
import redis.clients.jedis.JedisCommands;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Viren
 *
 */
public class ServerModule extends AbstractModule {
	
	private int maxThreads = 50;
	
	private ExecutorService es;
	
	private JedisCommands dynoConn;
	
	private HostSupplier hostSupplier;
	
	private String region;
	
	private String localRack;
	
	private ConductorConfig conductorConfig;
	
	private ConductorServer.DB db;

	public ServerModule(JedisCommands jedis, HostSupplier hostSupplier, ConductorConfig conductorConfig, ConductorServer.DB db) {
		this.dynoConn = jedis;
		this.hostSupplier = hostSupplier;
		this.conductorConfig = conductorConfig;
		this.region = conductorConfig.getRegion();
		this.localRack = conductorConfig.getAvailabilityZone();
		this.db = db;
		
	}
	
	@Override
	protected void configure() {

		configureExecutorService();

		bind(Configuration.class).toInstance(conductorConfig);

		if (db == ConductorServer.DB.mysql) {
			install(new MySQLWorkflowModule());
		} else {
			install(new RedisWorkflowModule(conductorConfig, dynoConn, hostSupplier));
		}

		if (conductorConfig.getProperty("workflow.elasticsearch.version", "2").equals("5")){
			install(new ElasticSearchModuleV5());
		}
		else {
			// Use ES2 as default.
			install(new ElasticSearchModule());
		}
		
		install(new CoreModule());
		install(new JerseyModule());
		
		new HttpTask(new RestClientManager(), conductorConfig);
		new JsonJqTransform();
		
		List<AbstractModule> additionalModules = conductorConfig.getAdditionalModules();
		if(additionalModules != null) {
			for(AbstractModule additionalModule : additionalModules) {
				install(additionalModule);
			}
		}
	}
	
	@Provides
	public ExecutorService getExecutorService(){
		return this.es;
	}
	
	private void configureExecutorService(){
		AtomicInteger count = new AtomicInteger(0);
		this.es = java.util.concurrent.Executors.newFixedThreadPool(maxThreads, runnable -> {
            Thread conductorWorkerThread = new Thread(runnable);
            conductorWorkerThread.setName("conductor-worker-" + count.getAndIncrement());
            return conductorWorkerThread;
        });
	}
}
