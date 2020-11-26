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
package com.netflix.conductor.redis.config.utils;

import com.google.inject.ProvisionException;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.queues.ShardSupplier;
import com.netflix.dyno.queues.shard.DynoShardSupplier;

import javax.inject.Provider;

public class DynoShardSupplierProvider implements Provider<ShardSupplier> {

    private final HostSupplier hostSupplier;
    private final RedisProperties properties;

    public DynoShardSupplierProvider(HostSupplier hostSupplier, RedisProperties properties) {
        this.hostSupplier = hostSupplier;
        this.properties = properties;
    }

    @Override
    public ShardSupplier get() {
        if (properties.getAvailabilityZone() == null) {
            throw new ProvisionException(
                "Availability zone is not defined.  Ensure Configuration.getAvailabilityZone() returns a non-null " +
                    "and non-empty value."
            );
        }
        String localDC = properties.getAvailabilityZone().replaceAll(properties.getRegion(), "");
        return new DynoShardSupplier(hostSupplier, properties.getRegion(), localDC);
    }
}
