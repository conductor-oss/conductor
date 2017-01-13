/**
 * 
 */
package com.netflix.conductor.server;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.conductor.redis.utils.JedisMock;
import com.netflix.conductor.server.es.EmbeddedElasticSearch;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.Host.Status;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.jedis.DynoJedisClient;

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
		String dbstring = cc.getProperty("db", "dynomite");
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
			jedis = new DynoJedisClient.Builder()
			.withHostSupplier(hs)
			.withApplicationName(cc.getAppId())
			.withDynomiteClusterName(dynoClusterName)
			.build();
			logger.info("Starting conductor server using dynomite cluster " + dynoClusterName);
			break;
		case memory:
			jedis = new JedisMock();
			try {
				EmbeddedElasticSearch.start();
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
		
		Guice.createInjector(sm, new JerseyModule());
		
		ClassLoader loader = Main.class.getClassLoader();
		File indexLoc = new File(loader.getResource("webapp/swagger-ui/index.html").getFile());
		String htmlLoc = indexLoc.getParentFile().getParentFile().getAbsolutePath();
		
		this.server = new Server(port);
		
		ServletContextHandler context = new ServletContextHandler();
		context.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
		context.setResourceBase(htmlLoc);
		context.setWelcomeFiles(new String[] { "index.html" });
		
		server.setHandler(context);


		DefaultServlet staticServlet = new DefaultServlet();
		context.addServlet(new ServletHolder(staticServlet), "/*");
		
		server.start();
		System.out.println("Started server on http://localhost:" + port + "/");
		
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
}
