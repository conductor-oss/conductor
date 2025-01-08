/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.redis.dynoqueue;

import org.junit.Test;

import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostBuilder;

public class RedisPingerTest {

    @Test
    public void testPingWithRetry() {
        RedisPinger pinger = new RedisPinger();
        long startTime = System.currentTimeMillis();
        Host host = new HostBuilder().setHostname("abcd").setPort(8080).createHost();
        boolean result = pinger.pingWithRetry(host);
        long duration = System.currentTimeMillis() - startTime;
        assert (!result);
        assert (duration > 3000);
    }
}
