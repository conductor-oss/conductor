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
package com.netflix.conductor.server;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.MediaType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.redis.utils.JedisMock;
import com.netflix.conductor.server.es.EmbeddedElasticSearch;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.Host.Status;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.sun.jersey.api.client.Client;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;

/**
 * @author Viren
 *
 */
public class ConductorServer {

	private static Logger logger = LoggerFactory.getLogger(ConductorServer.class);
	
	private enum DB {
		redis, dynomite, memory
	}
	
	private ServerModule sm;
	
	private Server server;
	
	private ConductorConfig cc;
	
	private DB db;
	
	public ConductorServer(ConductorConfig cc) {
		this.cc = cc;
		String dynoClusterName = cc.getProperty("workflow.dynomite.cluster.name", "");
		
		List<Host> dynoHosts = new LinkedList<>();
		String dbstring = cc.getProperty("db", "memory");
		try {
			db = DB.valueOf(dbstring);
		}catch(IllegalArgumentException ie) {
			logger.error("Invalid db name: " + dbstring + ", supported values are: redis, dynomite, memory");
			System.exit(1);
		}
		
		if(!db.equals(DB.memory)) {
			String hosts = cc.getProperty("workflow.dynomite.cluster.hosts", null);
			if(hosts == null) {
				System.err.println("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
				logger.error("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
				System.exit(1);
			}
			String[] hostConfigs = hosts.split(";");
			
			for(String hostConfig : hostConfigs) {
				String[] hostConfigValues = hostConfig.split(":");
				String host = hostConfigValues[0];
				int port = Integer.parseInt(hostConfigValues[1]);
				String rack = hostConfigValues[2];
				Host dynoHost = new Host(host, port, rack, Status.Up);
				dynoHosts.add(dynoHost);
			}
				
		}else {
			//Create a single shard host supplier
			Host dynoHost = new Host("localhost", 0, cc.getAvailabilityZone(), Status.Up);
			dynoHosts.add(dynoHost);
		}
		init(dynoClusterName, dynoHosts);
	}
	
	private void init(String dynoClusterName, List<Host> dynoHosts) {
		HostSupplier hs = new HostSupplier() {
			
			@Override
			public Collection<Host> getHosts() {
				return dynoHosts;
			}
		};
		
		JedisCommands jedis = null;
		switch(db) {
		case redis:
			
			String host = dynoHosts.get(0).getHostName();
			int port = dynoHosts.get(0).getPort();
			jedis = new Jedis(host, port);
			logger.info("Starting conductor server using standalone redis on " + host + ":" + port);
			break;
			
		case dynomite:
			
			ConnectionPoolConfigurationImpl cp = new ConnectionPoolConfigurationImpl(dynoClusterName).withTokenSupplier(new TokenMapSupplier() {
				
				HostToken token = new HostToken(1L, dynoHosts.get(0));
				
				@Override
				public List<HostToken> getTokens(Set<Host> activeHosts) {
					return Arrays.asList(token);
				}
				
				@Override
				public HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
					return token;
				}
			}).setLocalRack(cc.getAvailabilityZone()).setLocalDataCenter(cc.getRegion());
			
			jedis = new DynoJedisClient.Builder()
				.withHostSupplier(hs)
				.withApplicationName(cc.getAppId())
				.withDynomiteClusterName(dynoClusterName)
				.withCPConfig(cp)
				.build();
			
			logger.info("Starting conductor server using dynomite cluster " + dynoClusterName);
			
			break;
			
		case memory:
			
			jedis = new JedisMock();
			try {
				EmbeddedElasticSearch.start();
				if(System.getProperty("workflow.elasticsearch.url") == null) {
					System.setProperty("workflow.elasticsearch.url", "localhost:9300");
				}
				if(System.getProperty("workflow.elasticsearch.index.name") == null) {
					System.setProperty("workflow.elasticsearch.index.name", "conductor");
				}
			} catch (Exception e) {
				logger.error("Error starting embedded elasticsearch.  Search functionality will be impacted: " + e.getMessage(), e);
			}
			logger.info("Starting conductor server using in memory data store");
			break;
		}
		
		this.sm = new ServerModule(jedis, hs, cc);
	}
	
	public synchronized void start(int port, boolean join) throws Exception {
		
		if(server != null) {
			throw new IllegalStateException("Server is already running");
		}
		
		Guice.createInjector(sm);

		//Swagger
		String resourceBasePath = Main.class.getResource("/swagger-ui").toExternalForm();
		this.server = new Server(port);
		
		ServletContextHandler context = new ServletContextHandler();
		context.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
		context.setResourceBase(resourceBasePath);
		context.setWelcomeFiles(new String[] { "index.html" });
		
		server.setHandler(context);


		DefaultServlet staticServlet = new DefaultServlet();
		context.addServlet(new ServletHolder(staticServlet), "/*");
		
		server.start();
		System.out.println("Started server on http://localhost:" + port + "/");
		try {
			boolean create = Boolean.getBoolean("loadSample");
			if(create) {
				System.out.println("Creating kitchensink workflow");
				createKitchenSink(port);
			}
		}catch(Exception e) {
			logger.error(e.getMessage(), e);
		}
		
		if(join) {
			server.join();
		}

	}
	
	public synchronized void stop() throws Exception {
		if(server == null) {
			throw new IllegalStateException("Server is not running.  call #start() method to start the server");
		}
		server.stop();
		server = null;
	}
	
	private static void createKitchenSink(int port) throws Exception {
		
		List<TaskDef> taskDefs = new LinkedList<>();
		for(int i = 0; i < 40; i++) {
			taskDefs.add(new TaskDef("task_" + i, "task_" + i, 1, 0));
		}
		taskDefs.add(new TaskDef("search_elasticsearch", "search_elasticsearch", 1, 0));
		
		Client client = Client.create();
		ObjectMapper om = new ObjectMapper();
		client.resource("http://localhost:" + port + "/api/metadata/taskdefs").type(MediaType.APPLICATION_JSON).post(om.writeValueAsString(taskDefs));
		
		InputStream stream = Main.class.getResourceAsStream("/kitchensink.json");
		client.resource("http://localhost:" + port + "/api/metadata/workflow").type(MediaType.APPLICATION_JSON).post(stream);
		
		stream = Main.class.getResourceAsStream("/sub_flow_1.json");
		client.resource("http://localhost:" + port + "/api/metadata/workflow").type(MediaType.APPLICATION_JSON).post(stream);
		
		logger.info("Kitchen sink workflows are created!");
	}
}
