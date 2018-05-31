package com.netflix.conductor.grpc.server;

import com.google.inject.Inject;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.WorkflowServiceGrpc;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class GRPCServer {
    private static final Logger logger = LoggerFactory.getLogger(GRPCServer.class);

    private final Server server;

    public final static String CONFIG_PORT = "grpc.port";
    public final static int CONFIG_PORT_DEFAULT = 8080;

    @Inject
    public GRPCServer(Configuration conf, BindableService... services) {
        final int port = conf.getIntProperty(CONFIG_PORT, CONFIG_PORT_DEFAULT);

        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        for (BindableService s : services) {
            builder.addService(s);
        }

        server = builder.build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("grpc: Server started, listening on " + server.getPort());
    }

    public void stop() {
        if (server != null) {
            logger.info("grpc: server shutting down");
            server.shutdown();
        }
    }
}
