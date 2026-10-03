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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IPCheckerImplTest {

    private IPCheckerImpl checker(List<String> ips, List<String> allowedIps) {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(ips);
        config.setAllowedIps(allowedIps);
        return new IPCheckerImpl(config);
    }

    @Test
    public void cidrRangeIsBlocked() {
        IPChecker checker = checker(List.of("169.254.0.0/16"), List.of());
        assertTrue(checker.isBlocked("169.254.169.254").isBlocked());
        assertEquals("169.254.0.0/16", checker.isBlocked("169.254.169.254").getBlockedBy());
        assertFalse(checker.isBlocked("8.8.8.8").isBlocked());
    }

    @Test
    public void loopbackAndRfc1918Ranges() {
        IPChecker checker =
                checker(List.of("127.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16"), List.of());
        assertTrue(checker.isBlocked("127.0.0.1").isBlocked());
        assertTrue(checker.isBlocked("10.1.2.3").isBlocked());
        assertTrue(checker.isBlocked("192.168.1.1").isBlocked());
        assertFalse(checker.isBlocked("172.32.0.1").isBlocked());
    }

    @Test
    public void allowedIpOverridesBlock() {
        IPChecker checker = checker(List.of("10.0.0.0/8"), List.of("10.1.1.1"));
        assertFalse(checker.isBlocked("10.1.1.1").isBlocked());
        assertTrue(checker.isBlocked("10.1.1.2").isBlocked());
    }

    @Test
    public void emptyConfigBlocksNothing() {
        IPChecker checker = checker(List.of(), List.of());
        assertFalse(checker.isBlocked("169.254.169.254").isBlocked());
    }
}
