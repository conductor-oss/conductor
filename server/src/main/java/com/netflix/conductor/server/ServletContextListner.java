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

import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.servlet.DefaultServlet;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

/**
 * @author Viren
 *
 */
public class ServletContextListner extends GuiceServletContextListener {

	@Override
	protected Injector getInjector() {
		
		loadProperties();
		
		ConductorConfig config = new ConductorConfig();
		ConductorServer server = new ConductorServer(config);
		
		return Guice.createInjector(server.getGuiceModule(), getSwagger());
	}
	
	private ServletModule getSwagger() {
		
		String resourceBasePath = ServletContextListner.class.getResource("/swagger-ui").toExternalForm();
		DefaultServlet ds = new DefaultServlet();
		
		ServletModule sm = new ServletModule() {
			@Override
			protected void configureServlets() {
				Map<String, String> params = new HashMap<>();
				params.put("resourceBase", resourceBasePath);
				params.put("redirectWelcome", "true");
				serve("/*").with(ds, params);
			}
		};
		
		return sm;
		
	}
	
	private void loadProperties() {
		try {
			
			String key = "conductor_properties";
			String propertyFile = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
			if(propertyFile != null) {
				System.out.println("Using " + propertyFile);
				FileInputStream propFile = new FileInputStream(propertyFile);
				Properties props = new Properties(System.getProperties());
				props.load(propFile);
				System.setProperties(props);
			}
			
			key = "log4j_properties";
			String log4jConfig = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
			if(log4jConfig != null) {
				PropertyConfigurator.configure(new FileInputStream(log4jConfig));
			}
			
		} catch (Exception e) {
			System.err.println("Error loading properties " + e.getMessage());
			e.printStackTrace();
		}
	}
	
}
