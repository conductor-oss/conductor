/**
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceServletContextListener;

import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;

import org.apache.log4j.PropertyConfigurator;

import java.io.FileInputStream;
import java.util.Optional;
import java.util.Properties;

/**
 * @author Viren
 *
 */
public class ServletContextListner extends GuiceServletContextListener {

    @Override
    protected Injector getInjector() {

        loadProperties();

        SystemPropertiesConfiguration config = new SystemPropertiesConfiguration();

        return Guice.createInjector(new ModulesProvider(config).get());
    }

    private void loadProperties() {
        try {

            String key = "conductor_properties";
            String propertyFile = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
            if (propertyFile != null) {
                System.out.println("Using " + propertyFile);
                FileInputStream propFile = new FileInputStream(propertyFile);
                Properties props = new Properties(System.getProperties());
                props.load(propFile);
                System.setProperties(props);
            }

            key = "log4j_properties";
            String log4jConfig = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
            if (log4jConfig != null) {
                PropertyConfigurator.configure(new FileInputStream(log4jConfig));
            }

        } catch (Exception e) {
            System.err.println("Error loading properties " + e.getMessage());
            e.printStackTrace();
        }
    }

}
