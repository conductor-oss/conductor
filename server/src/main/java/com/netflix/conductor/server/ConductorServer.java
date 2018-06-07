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

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.servlet.GuiceFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.config.SystemPropertiesConfiguration;
import com.netflix.conductor.dao.RedisWorkflowModule;
import com.netflix.conductor.dao.es.EmbeddedElasticSearch;
import com.netflix.conductor.dao.es.index.ElasticSearchModule;
import com.netflix.conductor.dao.es5.EmbeddedElasticSearchV5;
import com.netflix.conductor.dao.es5.index.ElasticSearchModuleV5;
import com.netflix.conductor.dao.mysql.MySQLWorkflowModule;
import com.sun.jersey.api.client.Client;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.DispatcherType;
import javax.ws.rs.core.MediaType;

/**
 * @author Viren
 */
public class ConductorServer {

    private static Logger logger = LoggerFactory.getLogger(ConductorServer.class);

    private Server server;

    private SystemPropertiesConfiguration systemPropertiesConfiguration;

    public ConductorServer(SystemPropertiesConfiguration systemPropertiesConfiguration) {
        this.systemPropertiesConfiguration = systemPropertiesConfiguration;
    }

    private List<AbstractModule> selectModulesToLoad() {
        Configuration.DB database = null;
        List<AbstractModule> modules = new ArrayList<>();

        try {
            database = systemPropertiesConfiguration.getDB();
        } catch (IllegalArgumentException ie) {
            logger.error("Invalid db name: " + systemPropertiesConfiguration.getDBString()
                    + ", supported values are: " + Arrays.toString(Configuration.DB.values()));
            System.exit(1);
        }

        switch (database) {
            case REDIS:
            case DYNOMITE:
                modules.add(new DynomiteClusterModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using dynomite/redis cluster.");
                break;

            case MYSQL:
                modules.add(new MySQLWorkflowModule());
                modules.add(new MySQLWorkflowModule());
                logger.info("Starting conductor server using MySQL data store", database);
                break;
            case MEMORY:
                // TODO This ES logic should probably live elsewhere.
                try {
                    if (
                            systemPropertiesConfiguration.getIntProperty(
                                    "workflow.elasticsearch.version",
                                    2
                    ) == 5) {
                        EmbeddedElasticSearchV5.start();
                    } else {
                        // Use ES2 as default.
                        EmbeddedElasticSearch.start();
                    }
                    if (System.getProperty("workflow.elasticsearch.url") == null) {
                        System.setProperty("workflow.elasticsearch.url", "localhost:9300");
                    }
                    if (System.getProperty("workflow.elasticsearch.index.name") == null) {
                        System.setProperty("workflow.elasticsearch.index.name", "conductor");
                    }
                } catch (Exception e) {
                    logger.error("Error starting embedded elasticsearch.  Search functionality will be impacted: " + e.getMessage(), e);
                }

                modules.add(new LocalRedisModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using in memory data store");
                break;

            case REDIS_CLUSTER:
                modules.add(new RedisClusterModule());
                modules.add(new RedisWorkflowModule());
                logger.info("Starting conductor server using redis_cluster.");
                break;
        }

        if (systemPropertiesConfiguration.getIntProperty("workflow.elasticsearch.version", 2) == 5) {
            modules.add(new ElasticSearchModuleV5());
        } else {
            modules.add(new ElasticSearchModule());
        }
        
        if (systemPropertiesConfiguration.getJerseyEnabled()) {
            modules.add(new JerseyModule());
            modules.add(new SwaggerModule());
        }

        modules.add(new ServerModule());

        return modules;
    }

    public synchronized void start(int port, boolean join) throws Exception {

        if (server != null) {
            throw new IllegalStateException("Server is already running");
        }

        Guice.createInjector(getModulesToLoad());

        this.server = new Server(port);

        ServletContextHandler context = new ServletContextHandler();
        context.addFilter(GuiceFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        context.setWelcomeFiles(new String[]{"index.html"});

        server.setHandler(context);

        server.start();
        System.out.println("Started server on http://localhost:" + port + "/");
        try {
            boolean create = Boolean.getBoolean("loadSample");
            if (create) {
                System.out.println("Creating kitchensink workflow");
                createKitchenSink(port);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        if (join) {
            server.join();
        }

    }

    public synchronized void stop() throws Exception {
        if (server == null) {
            throw new IllegalStateException("Server is not running.  call #start() method to start the server");
        }
        server.stop();
        server = null;
    }

    private List<AbstractModule> getAdditionalModules() {
        return systemPropertiesConfiguration.getAdditionalModules();
    }

    public List<AbstractModule> getModulesToLoad() {
        List<AbstractModule> modulesToLoad = new ArrayList<>();

        modulesToLoad.addAll(selectModulesToLoad());
        modulesToLoad.addAll(getAdditionalModules());

        return modulesToLoad;
    }

    private static void createKitchenSink(int port) throws Exception {

        List<TaskDef> taskDefs = new LinkedList<>();
        for (int i = 0; i < 40; i++) {
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

        String input = "{\"task2Name\":\"task_5\"}";
        client.resource("http://localhost:" + port + "/api/workflow/kitchensink").type(MediaType.APPLICATION_JSON).post(input);

        logger.info("Kitchen sink workflows are created!");
    }
}
