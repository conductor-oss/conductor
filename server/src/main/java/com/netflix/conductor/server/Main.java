/**
 * 
 */
package com.netflix.conductor.server;

import java.io.FileInputStream;
import java.util.Properties;

/**
 * @author Viren
 * Entry point for the server
 */
public class Main {
	
	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Usage: " + Main.class.getSimpleName() + " <property file path>");
			System.exit(1);
		}
		String propertyFile = args[0];
		System.out.println("Using " + propertyFile);
		
		FileInputStream propFile = new FileInputStream(propertyFile);
		Properties props = new Properties(System.getProperties());
		props.load(propFile);
		System.setProperties(props);
		
		String hosts = props.getProperty("workflow.dynomite.cluster.hosts");
		if(hosts == null) {
			System.err.println("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied properties file " + propertyFile);
		}
		
		System.out.println("Starting conductor server...");
		
		ConductorConfig config = new ConductorConfig();
		ConductorServer server = new ConductorServer(config);
		server.start(config.getIntProperty("port", 8081));
	}

}
