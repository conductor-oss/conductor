/*
 * Copyright (c) 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.locking.redis.config;

import com.netflix.conductor.core.config.Configuration;

public interface RedisLockConfiguration extends Configuration {

    String REDIS_SERVER_TYPE_PROP_NAME = "workflow.redis.locking.server.type";
    String REDIS_SERVER_TYPE_DEFAULT_VALUE = "single";
    String REDIS_SERVER_STRING_PROP_NAME = "workflow.redis.locking.server.address";
    String REDIS_SERVER_STRING_DEFAULT_VALUE = "redis://127.0.0.1:6379";
    String REDIS_SERVER_PASSWORD_PROP_NAME = "workflow.redis.locking.server.password";
    String REDIS_SERVER_PASSWORD_DEFAULT_VALUE = null;
    String REDIS_SERVER_MASTER_NAME_PROP_NAME = "workflow.redis.locking.server.master.name";
    String REDIS_SERVER_MASTER_NAME_DEFAULT_VALUE = "master";


    default REDIS_SERVER_TYPE getRedisServerType() {
        return REDIS_SERVER_TYPE.valueOf(getRedisServerStringValue());
    }

    default String getRedisServerStringValue() {
        return getProperty(REDIS_SERVER_TYPE_PROP_NAME, REDIS_SERVER_TYPE_DEFAULT_VALUE).toUpperCase();
    }

    default String getRedisServerAddress() {
        return getProperty(REDIS_SERVER_STRING_PROP_NAME, REDIS_SERVER_STRING_DEFAULT_VALUE);
    }

    default String getRedisServerPassword() {
        return getProperty(REDIS_SERVER_PASSWORD_PROP_NAME, REDIS_SERVER_PASSWORD_DEFAULT_VALUE);
    }

    default String getRedisServerMasterName() {
        return getProperty(REDIS_SERVER_MASTER_NAME_PROP_NAME, REDIS_SERVER_MASTER_NAME_DEFAULT_VALUE);
    }

    enum REDIS_SERVER_TYPE {
        SINGLE, CLUSTER, SENTINEL
    }
}
