package com.netflix.conductor.grpc.server;

import com.netflix.conductor.core.config.Configuration;

import javax.inject.Inject;
import javax.inject.Provider;

public class GRPCServerProvider implements Provider<GRPCServer> {
    private final Configuration configuration;

    @Inject
    public GRPCServerProvider(Configuration conf) {
        this.configuration = conf;
    }

    @Override
    public GRPCServer get() {
        return new GRPCServer(configuration);
    }
}
