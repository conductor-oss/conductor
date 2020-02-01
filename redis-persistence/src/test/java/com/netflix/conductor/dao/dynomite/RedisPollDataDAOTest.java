/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.dao.dynomite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.config.TestConfiguration;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.dao.PollDataDAO;
import com.netflix.conductor.dao.PollDataDAOTest;
import com.netflix.conductor.dao.redis.JedisMock;
import com.netflix.conductor.dyno.DynoProxy;
import org.junit.Before;
import redis.clients.jedis.commands.JedisCommands;

public class RedisPollDataDAOTest extends PollDataDAOTest {

    private PollDataDAO pollDataDAO;
    private static ObjectMapper objectMapper = new JsonMapperProvider().get();

    @Before
    public void init() {
        Configuration config = new TestConfiguration();
        JedisCommands jedisMock = new JedisMock();
        DynoProxy dynoClient = new DynoProxy(jedisMock);

        pollDataDAO = new RedisPollDataDAO(dynoClient, objectMapper, config);
    }

    @Override
    protected PollDataDAO getPollDataDAO() {
        return pollDataDAO;
    }
}
