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
package com.netflix.conductor.redislock.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "workflow.decider", name = "locking.server", havingValue = "REDIS")
public class RedisLockProperties {

    @Value("${workflow.redis.locking.server.type:single}")
    private String serverType;

    @Value("${workflow.redis.locking.server.address:redis://127.0.0.1:6379}")
    private String serverAddress;

    @Value("${workflow.redis.locking.server.password:#{null}}")
    private String serverPassword;

    @Value("${workflow.redis.locking.server.master.name:master}")
    private String serverMasterName;

    @Value("${workflow.decider.locking.namespace:}")
    private String lockingNamespace;

    @Value("${workflow.decider.locking.exceptions.ignore:false}")
    private boolean ignoreLockingExceptions;

    public REDIS_SERVER_TYPE getRedisServerType() {
        return REDIS_SERVER_TYPE.valueOf(serverType.toUpperCase());
    }

    public String getRedisServerAddress() {
        return serverAddress;
    }

    public String getRedisServerPassword() {
        return serverPassword;
    }

    public String getRedisServerMasterName() {
        return serverMasterName;
    }

    public String getLockingNamespace() {
        return lockingNamespace;
    }

    public boolean isIgnoreLockingExceptions() {
        return ignoreLockingExceptions;
    }

    public enum REDIS_SERVER_TYPE {
        SINGLE, CLUSTER, SENTINEL
    }
}
