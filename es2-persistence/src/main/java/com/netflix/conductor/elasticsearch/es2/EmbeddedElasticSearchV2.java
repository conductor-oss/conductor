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
package com.netflix.conductor.elasticsearch.es2;

import com.netflix.conductor.elasticsearch.EmbeddedElasticSearch;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class EmbeddedElasticSearchV2 implements EmbeddedElasticSearch {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddedElasticSearchV2.class);

    private Node instance;
    private Client client;
    private File dataDir;

    @Override
    public void start() throws Exception {
        start(DEFAULT_CLUSTER_NAME, DEFAULT_HOST, DEFAULT_PORT, true);
    }

    public synchronized void start(String clusterName, String host, int port, boolean enableTransportClient) throws Exception {

        if (instance != null && !instance.isClosed()) {
            logger.info("Elastic Search is already running on port {}", getPort());
            return;
        }

        final Settings settings = getSettings(clusterName, host, port, enableTransportClient);
        setupDataDir(settings.get(ES_PATH_DATA));

        logger.info("Starting ElasticSearch for cluster {} ", settings.get("cluster.name"));
        instance = NodeBuilder.nodeBuilder().data(true).local(enableTransportClient ? false : true).settings(settings).client(false).node();
        instance.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                instance.close();
            }
        });
        logger.info("ElasticSearch cluster {} started in local mode on port {}", instance.settings().get("cluster.name"), getPort());
        client = instance.client();
    }

    private Settings getSettings(String clusterName, String host, int port, boolean enableTransportClient) throws IOException {
        dataDir = Files.createTempDirectory(clusterName + "_" + System.currentTimeMillis() + "data").toFile();
        File homeDir = Files.createTempDirectory(clusterName + "_" + System.currentTimeMillis() + "-home").toFile();
        return Settings.builder()
                .put("cluster.name", clusterName)
                .put("http.host", host)
                .put("http.port", port)
                .put(ES_PATH_DATA, dataDir.getAbsolutePath())
                .put(ES_PATH_HOME, homeDir.getAbsolutePath())
                .put("http.enabled", true)
                .put("script.inline", "on")
                .put("script.indexed", "on")
                .build();
    }

    public Client getClient() {
        if (instance == null || instance.isClosed()) {
            logger.error("Embedded ElasticSearch is not Initialized and started, please call start() method first");
            return null;
        }
        return client;
    }

    private String getPort() {
        return instance.settings().get("http.port");
    }

    @Override
    public synchronized void stop() {

        if (instance != null && !instance.isClosed()) {
            String port = getPort();
            logger.info("Stopping Elastic Search");
            instance.close();
            logger.info("Elastic Search on port {} stopped", port);
        }

    }
}
