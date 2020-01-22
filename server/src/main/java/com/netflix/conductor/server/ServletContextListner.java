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
import com.netflix.conductor.bootstrap.BootstrapUtil;
import com.netflix.conductor.bootstrap.ModulesProvider;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;
import com.netflix.conductor.elasticsearch.EmbeddedElasticSearchProvider;

import javax.servlet.ServletContextEvent;
import java.util.Optional;

/**
 * @author Viren
 *
 */
public class ServletContextListner extends GuiceServletContextListener {
    private Injector serverInjector;

    @Override
    protected Injector getInjector() {

        loadProperties();

        SystemPropertiesConfiguration config = new SystemPropertiesConfiguration();

        serverInjector = Guice.createInjector(new ModulesProvider(config).get());

        return serverInjector;
    }

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        super.contextInitialized(servletContextEvent);

        Optional<EmbeddedElasticSearch> embeddedElasticSearch = serverInjector.getInstance(EmbeddedElasticSearchProvider.class).get();
        embeddedElasticSearch.ifPresent(BootstrapUtil::startEmbeddedElasticsearchServer);

        BootstrapUtil.setupIndex(serverInjector.getInstance(IndexDAO.class));
    }

    private void loadProperties() {
        try {
            String key = "conductor_properties";
            String propertyFile = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
            BootstrapUtil.loadConfigFile(propertyFile);

            key = "log4j_properties";
            String log4jConfig = Optional.ofNullable(System.getProperty(key)).orElse(System.getenv(key));
            BootstrapUtil.loadLog4jConfig(log4jConfig);

        } catch (Exception e) {
            System.err.println("Error loading properties " + e.getMessage());
            e.printStackTrace();
        }
    }
}
