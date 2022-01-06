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
package com.netflix.conductor.redis.jedis;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.redis.config.RedisProperties;
import com.netflix.conductor.redis.dynoqueue.ConfigurationHostSupplier;
import com.netflix.dyno.connectionpool.Host;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConfigurationHostSupplierTest {

    private RedisProperties properties;

    private ConfigurationHostSupplier configurationHostSupplier;

    @Before
    public void setUp() {
        properties = mock(RedisProperties.class);
        configurationHostSupplier = new ConfigurationHostSupplier(properties);
    }

    @Test
    public void getHost() {
        when(properties.getHosts()).thenReturn("dyno1:8102:us-east-1c");

        List<Host> hosts = configurationHostSupplier.getHosts();
        assertEquals(1, hosts.size());

        Host firstHost = hosts.get(0);
        assertEquals("dyno1", firstHost.getHostName());
        assertEquals(8102, firstHost.getPort());
        assertEquals("us-east-1c", firstHost.getRack());
        assertTrue(firstHost.isUp());
    }

    @Test
    public void getMultipleHosts() {
        when(properties.getHosts()).thenReturn("dyno1:8102:us-east-1c;dyno2:8103:us-east-1c");

        List<Host> hosts = configurationHostSupplier.getHosts();
        assertEquals(2, hosts.size());

        Host firstHost = hosts.get(0);
        assertEquals("dyno1", firstHost.getHostName());
        assertEquals(8102, firstHost.getPort());
        assertEquals("us-east-1c", firstHost.getRack());
        assertTrue(firstHost.isUp());

        Host secondHost = hosts.get(1);
        assertEquals("dyno2", secondHost.getHostName());
        assertEquals(8103, secondHost.getPort());
        assertEquals("us-east-1c", secondHost.getRack());
        assertTrue(secondHost.isUp());
    }

    @Test
    public void getAuthenticatedHost() {
        when(properties.getHosts()).thenReturn("redis1:6432:us-east-1c:password");

        List<Host> hosts = configurationHostSupplier.getHosts();
        assertEquals(1, hosts.size());

        Host firstHost = hosts.get(0);
        assertEquals("redis1", firstHost.getHostName());
        assertEquals(6432, firstHost.getPort());
        assertEquals("us-east-1c", firstHost.getRack());
        assertEquals("password", firstHost.getPassword());
        assertTrue(firstHost.isUp());
    }
}
