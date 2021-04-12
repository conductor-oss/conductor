package com.netflix.conductor.jetty.server;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Provider;

public class JettyServerProvider implements Provider<Optional<JettyServer>> {
    private final JettyServerConfiguration configuration;

    @Inject
    public JettyServerProvider(JettyServerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Optional<JettyServer> get() {
        return configuration.isEnabled() ?
                Optional.of(
                        new JettyServer(
                                configuration.getPort(),
                                configuration.isJoin(),
                                configuration.getThreadPoolMaxThreads(),
                                configuration.getThreadPoolMinThreads()
                        ))
                : Optional.empty();
    }
}
