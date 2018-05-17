package com.netflix.conductor.server;

import com.netflix.conductor.core.config.Configuration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

public class ConfigurationHostSupplierProvider implements Provider<HostSupplier> {
    private static Logger logger = LoggerFactory.getLogger(ConfigurationHostSupplierProvider.class);

    private final Configuration configuration;

    @Inject
    public ConfigurationHostSupplierProvider(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HostSupplier get() {
        return () -> parseHostsFromConfig(configuration);
    }

    private List<Host> parseHostsFromConfig(Configuration configuration) {
        String hosts = configuration.getProperty("workflow.dynomite.cluster.hosts", null);
        if(hosts == null) {
            System.err.println("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
            logger.error("Missing dynomite/redis hosts.  Ensure 'workflow.dynomite.cluster.hosts' has been set in the supplied configuration.");
            System.exit(1);
        }
        return parseHostsFrom(hosts);
    }

    private List<Host> parseHostsFrom(String hostConfig){
        List<String> hostConfigs = Arrays.asList(hostConfig.split(";"));

        List<Host> hosts = hostConfigs.stream().map(hc -> {
            String[] hostConfigValues = hc.split(":");
            String host = hostConfigValues[0];
            int port = Integer.parseInt(hostConfigValues[1]);
            String rack = hostConfigValues[2];
            return new Host(host, port, rack, Host.Status.Up);
        }).collect(Collectors.toList());

        return hosts;
    }
}
