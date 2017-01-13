/**
 * 
 */
package com.netflix.conductor.server;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.DispatcherType;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.Host.Status;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.jedis.DynoJedisClient;

/**
 * @author Viren
 *
 */
public class ConductorServer {

	private ServerModule sm;
	
	private Server server;
	
	private ConductorConfig cc;
	
	public ConductorServer(ConductorConfig cc) {
		this.cc = cc;
		String hosts = cc.getProperty("workflow.dynomite.cluster.hosts", null);
		if(hosts == null) {
			System.out.println("?" + System.getProperty("workflow.dynomite.cluster.hosts"));
			System.err.println("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
		}
		String[] hostConfigs = hosts.split(";");
		List<Host> dynoHosts = new LinkedList<>();
		for(String hostConfig : hostConfigs) {
			String[] hostConfigValues = hostConfig.split(":");
			String host = hostConfigValues[0];
			int port = Integer.parseInt(hostConfigValues[1]);
			String rack = hostConfigValues[2];
			Host dynoHost = new Host(host, port, rack, Status.Up);
			dynoHosts.add(dynoHost);
		}
		String dynoClusterName = cc.getProperty("workflow.dynomite.cluster.name", "");
		init(dynoClusterName, dynoHosts);
	}
	
	public ConductorServer(String host, int port) {
		init(null, Arrays.asList(new Host(host, port, cc.getAvailabilityZone(), Status.Up)));
	}
	
	private void init(String dynoClusterName, List<Host> dynoHosts) {
		HostSupplier hs = new HostSupplier() {
			
			@Override
			public Collection<Host> getHosts() {
				return dynoHosts;
			}
		};
		DynoJedisClient jedis = new DynoJedisClient.Builder()
				.withHostSupplier(hs)
				.withApplicationName(cc.getAppId())
				.withDynomiteClusterName(dynoClusterName)
				.build();		
		
		this.sm = new ServerModule(jedis, hs, cc);
	}
	
	public synchronized void start(int port) throws Exception {
		
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
		System.out.println("Started server on http://localhost:8080/");
		
		server.join();

	}
	
	public synchronized void stop() throws Exception {
		if(server == null) {
			throw new IllegalStateException("Server is not running.  call #start() method to start the server");
		}
		server.stop();
		server = null;
	}
}
