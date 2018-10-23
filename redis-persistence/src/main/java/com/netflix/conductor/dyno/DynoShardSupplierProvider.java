package com.netflix.conductor.dyno;

import com.google.inject.ProvisionException;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.shard.DynoShardSupplier;

import javax.inject.Inject;
import javax.inject.Provider;

public class DynoShardSupplierProvider implements Provider<ShardSupplier> {

    private final HostSupplier hostSupplier;
    private final DynomiteConfiguration configuration;

    @Inject
    public DynoShardSupplierProvider(HostSupplier hostSupplier, DynomiteConfiguration dynomiteConfiguration) {
        this.hostSupplier = hostSupplier;
        this.configuration = dynomiteConfiguration;
    }

    @Override
    public ShardSupplier get() {
        if(configuration.getAvailabilityZone() == null) {
            throw new ProvisionException(
                    "Availability zone is not defined.  Ensure Configuration.getAvailabilityZone() returns a non-null " +
                            "and non-empty value."
            );
        }

        String localDC = configuration.getAvailabilityZone().replaceAll(configuration.getRegion(), "");

        return new DynoShardSupplier(hostSupplier, configuration.getRegion(), localDC);
    }
}
