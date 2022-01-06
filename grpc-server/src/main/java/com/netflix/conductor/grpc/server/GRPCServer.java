/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.grpc.server;

import java.io.IOException;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class GRPCServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GRPCServer.class);

    private final Server server;

    public GRPCServer(int port, List<BindableService> services) {
        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        services.forEach(builder::addService);
        server = builder.build();
    }

    @PostConstruct
    public void start() throws IOException {
        server.start();
        LOGGER.info("grpc: Server started, listening on " + server.getPort());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            LOGGER.info("grpc: server shutting down");
            server.shutdown();
        }
    }
}
