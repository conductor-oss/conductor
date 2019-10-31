/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.conductor.jedis;

import com.netflix.conductor.dyno.SystemPropertiesDynomiteConfiguration;
import com.netflix.dyno.connectionpool.Host;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ConfigurationHostSupplierProviderTest {

    private TestPropertiesDynomiteConfiguration configuration;
    private ConfigurationHostSupplierProvider provider;

    @Before
    public void setUp() {
        configuration = new TestPropertiesDynomiteConfiguration();
        provider = new ConfigurationHostSupplierProvider(configuration);
    }

    @Test
    public void getHost() {
        configuration.setProperty("workflow.dynomite.cluster.hosts", "dyno1:8102:us-east-1c");

        List<Host> hosts = provider.get().getHosts();

        Assert.assertEquals(1, hosts.size());
        Host firstHost = hosts.get(0);
        Assert.assertEquals("dyno1", firstHost.getHostName());
        Assert.assertEquals(8102, firstHost.getPort());
        Assert.assertEquals("us-east-1c", firstHost.getRack());
        Assert.assertTrue(firstHost.isUp());
    }

    @Test
    public void getMultipleHosts() {
        configuration.setProperty("workflow.dynomite.cluster.hosts",
            "dyno1:8102:us-east-1c;dyno2:8103:us-east-1c");

        List<Host> hosts = provider.get().getHosts();

        Assert.assertEquals(2, hosts.size());
        Host firstHost = hosts.get(0);
        Assert.assertEquals("dyno1", firstHost.getHostName());
        Assert.assertEquals(8102, firstHost.getPort());
        Assert.assertEquals("us-east-1c", firstHost.getRack());
        Assert.assertTrue(firstHost.isUp());
        Host secondHost = hosts.get(1);
        Assert.assertEquals("dyno2", secondHost.getHostName());
        Assert.assertEquals(8103, secondHost.getPort());
        Assert.assertEquals("us-east-1c", secondHost.getRack());
        Assert.assertTrue(secondHost.isUp());
    }

    @Test
    public void getAuthenticatedHost() {
        configuration
            .setProperty("workflow.dynomite.cluster.hosts", "redis1:6432:us-east-1c:password");

        List<Host> hosts = provider.get().getHosts();

        Assert.assertEquals(1, hosts.size());
        Host firstHost = hosts.get(0);
        Assert.assertEquals("redis1", firstHost.getHostName());
        Assert.assertEquals(6432, firstHost.getPort());
        Assert.assertEquals("us-east-1c", firstHost.getRack());
        Assert.assertEquals("password", firstHost.getPassword());
        Assert.assertTrue(firstHost.isUp());
    }

    private static class TestPropertiesDynomiteConfiguration extends
        SystemPropertiesDynomiteConfiguration {

        private Properties prop;

        TestPropertiesDynomiteConfiguration() {
            prop = new Properties();
        }

        @Override
        public String getProperty(String key, String defaultValue) {
            return prop.getOrDefault(key, defaultValue).toString();
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, Object> getAll() {
            return (Map) prop;
        }

        public void setProperty(String key, String value) {
            prop.setProperty(key, value);
        }
    }
}
