/*
 * Copyright 2025 Conductor Authors.
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
package org.conductoross.conductor.tasks.http.providers.interceptors;

import java.util.List;

import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HostAndIPCheckerTest {

    private HostAndIPChecker checker(HttpWorkerBlockConfig config) {
        return new HostAndIPChecker(new HostCheckerImpl(config), new IPCheckerImpl(config));
    }

    @Test
    public void blockedHostShortCircuitsBeforeResolution() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setHosts(List.of("*.internal.corp"));
        assertFalse(checker(config).isAllowed("db.internal.corp"));
    }

    @Test
    public void explicitlyAllowedHostWins() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setHosts(List.of("*.internal.corp"));
        config.setAllowedHosts(List.of("api.internal.corp"));
        assertTrue(checker(config).isAllowed("api.internal.corp"));
    }

    @Test
    public void literalIpHostBlockedByIpRange() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        // Cloud metadata endpoint — a literal IP resolves to itself, no DNS needed.
        config.setIps(List.of("169.254.0.0/16"));
        assertFalse(checker(config).isAllowed("169.254.169.254"));
    }

    @Test
    public void loopbackLiteralIpBlocked() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("127.0.0.0/8"));
        assertFalse(checker(config).isAllowed("127.0.0.1"));
    }

    @Test
    public void emptyConfigAllowsEverything() throws Exception {
        assertTrue(checker(new HttpWorkerBlockConfig()).isAllowed("8.8.8.8"));
    }
}
