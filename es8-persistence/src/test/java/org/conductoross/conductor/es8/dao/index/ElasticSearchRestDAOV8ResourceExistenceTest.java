/*
 * Copyright 2025 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.es8.dao.index;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.conductoross.conductor.es8.config.ElasticSearchProperties;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Test;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ElasticSearchRestDAOV8ResourceExistenceTest {

    private HttpServer server;
    private ElasticSearchRestDAOV8 dao;

    @After
    public void tearDown() throws Exception {
        if (dao != null) {
            var shutdown = ElasticSearchRestDAOV8.class.getDeclaredMethod("shutdown");
            shutdown.setAccessible(true);
            shutdown.invoke(dao);
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void headMissingReturnsFalse() throws Exception {
        List<String> methods = new ArrayList<>();
        startServer(
                exchange -> {
                    methods.add(exchange.getRequestMethod());
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });

        dao = newDao(server.getAddress().getPort());

        assertFalse(dao.doesResourceExist("/_alias/conductor"));
        assertEquals(List.of("HEAD"), methods);
    }

    @Test
    public void headMethodNotAllowedFallsBackToGetAndTreats404AsMissing() throws Exception {
        List<String> methods = new ArrayList<>();
        startServer(
                exchange -> {
                    methods.add(exchange.getRequestMethod());
                    if ("HEAD".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(405, -1);
                        exchange.close();
                        return;
                    }
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });

        dao = newDao(server.getAddress().getPort());

        assertFalse(dao.doesResourceExist("/_alias/conductor"));
        assertEquals(List.of("HEAD", "GET"), methods);
    }

    @Test
    public void headSuccessReturnsTrue() throws Exception {
        List<String> methods = new ArrayList<>();
        startServer(
                exchange -> {
                    methods.add(exchange.getRequestMethod());
                    exchange.sendResponseHeaders(200, -1);
                    exchange.close();
                });

        dao = newDao(server.getAddress().getPort());

        assertTrue(dao.doesResourceExist("/_alias/conductor"));
        assertEquals(List.of("HEAD"), methods);
    }

    private void startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
    }

    private ElasticSearchRestDAOV8 newDao(int port) {
        ElasticSearchProperties properties = new ElasticSearchProperties();
        properties.setUrl("http://127.0.0.1:" + port);
        properties.setIndexPrefix("conductor");

        return new ElasticSearchRestDAOV8(
                RestClient.builder(new HttpHost("127.0.0.1", port, "http")),
                new RetryTemplate(),
                properties,
                new ObjectMapper());
    }
}
