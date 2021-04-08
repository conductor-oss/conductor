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
package com.netflix.conductor.jetty.server;

import org.junit.Test;

public class JettyServerTest {
    @Test
    public void testCreateJettyServerWithDefaultThreadPoolConfiguration() throws Exception {
        JettyServer jettyServer = new JettyServer(8083, false);
        jettyServer.start();
        jettyServer.stop();
    }

    @Test
    public void testCreateJettyServerWithValidThreadPoolConfiguration() throws Exception {
        JettyServer jettyServer = new JettyServer(8083, false,20,8);
        jettyServer.start();
        jettyServer.stop();

    }
    @Test(expected = IllegalArgumentException.class)
    public void testCreateJettyServerWithInvalidThreadPoolConfiguration() throws Exception {
        JettyServer jettyServer = new JettyServer(8083, false,8,20);
        jettyServer.start();
        jettyServer.stop();
    }
}
