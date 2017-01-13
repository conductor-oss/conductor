/**
 * 
 */
package com.netflix.conductor.server;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;

/**
 * @author Viren
 * Entry point for the server
 */
public class Main {
	
	public static void main(String[] args) throws Exception {
		if(args.length < 1) {
			System.err.println("Usage: " + Main.class.getSimpleName() + " <property file path> <log4j.properties config file>");
			System.exit(1);
		}
		String propertyFile = args[0];
		System.out.println("Using " + propertyFile);
		
		if(args.length == 2) {
			System.out.println("Using log4j config " + args[1]);
			PropertyConfigurator.configure(new FileInputStream(new File(args[1])));
		}
		
		FileInputStream propFile = new FileInputStream(propertyFile);
		Properties props = new Properties(System.getProperties());
		props.load(propFile);
		System.setProperties(props);

		System.out.println("Starting conductor server using " + propertyFile);
		
		ConductorConfig config = new ConductorConfig();
		ConductorServer server = new ConductorServer(config);
		server.start(config.getIntProperty("port", 8081));
	}

}
