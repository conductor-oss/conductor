package com.netflix.conductor.jedis;

import com.netflix.conductor.dyno.DynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;
import com.netflix.dyno.connectionpool.HostSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigurationHostSupplierProvider implements Provider<HostSupplier> {
    private static Logger logger = LoggerFactory.getLogger(ConfigurationHostSupplierProvider.class);

    private final DynomiteConfiguration configuration;

    @Inject
    public ConfigurationHostSupplierProvider(DynomiteConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public HostSupplier get() {
        return () -> parseHostsFromConfig(configuration);
    }

    private List<Host> parseHostsFromConfig(DynomiteConfiguration configuration) {
        String hosts = configuration.getHosts();
        if (hosts == null) {
            // FIXME This type of validation probably doesn't belong here.
            String message = String.format(
                    "Missing dynomite/redis hosts.  Ensure '%s' has been set in the supplied configuration.",
                    DynomiteConfiguration.HOSTS_PROPERTY_NAME
            );
            logger.error(message);
            throw new RuntimeException(message);
        }
        return parseHostsFrom(hosts);
    }

    private List<Host> parseHostsFrom(String hostConfig) {
        List<String> hostConfigs = Arrays.asList(hostConfig.split(";"));

        List<Host> hosts = hostConfigs.stream().map(hc -> {
            String[] hostConfigValues = hc.split(":");
            String host = hostConfigValues[0];
            int port = Integer.parseInt(hostConfigValues[1]);
            String rack = hostConfigValues[2];

            if (hostConfigValues.length >= 4) {
                String password = hostConfigValues[3];
                return new HostBuilder()
                        .setHostname(host)
                        .setPort(port)
                        .setRack(rack)
                        .setStatus(Host.Status.Up)
                        .setPassword(password)
                        .createHost();
            }
            return new HostBuilder()
                    .setHostname(host)
                    .setPort(port)
                    .setRack(rack)
                    .setStatus(Host.Status.Up)
                    .createHost();
        }).collect(Collectors.toList());

        return hosts;
    }
}
