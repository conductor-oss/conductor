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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.hc.client5.http.DnsResolver;
import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BlockingDnsResolverTest {

    private BlockingDnsResolver resolver(HttpWorkerBlockConfig config, DnsResolver delegate) {
        return new BlockingDnsResolver(
                new HostCheckerImpl(config), new IPCheckerImpl(config), delegate);
    }

    /** Delegate that returns a fixed answer, mimicking a DNS reply the client would connect to. */
    private DnsResolver answering(String... ips) {
        return new DnsResolver() {
            @Override
            public InetAddress[] resolve(String host) throws UnknownHostException {
                InetAddress[] addresses = new InetAddress[ips.length];
                for (int i = 0; i < ips.length; i++) {
                    // Literal IPs — getByName does no network I/O.
                    addresses[i] = InetAddress.getByName(ips[i]);
                }
                return addresses;
            }

            @Override
            public String resolveCanonicalHostname(String host) {
                return host;
            }
        };
    }

    @Test
    public void blockedAddressesAreFilteredOut() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));

        // A rebinding answer: one public, one metadata address. Only the public one survives.
        InetAddress[] resolved =
                resolver(config, answering("8.8.8.8", "169.254.169.254"))
                        .resolve("evil.example.com");

        assertArrayEquals(new InetAddress[] {InetAddress.getByName("8.8.8.8")}, resolved);
    }

    @Test
    public void allBlockedResolutionFails() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16", "127.0.0.0/8"));

        try {
            resolver(config, answering("169.254.169.254", "127.0.0.1"))
                    .resolve("metadata.example.com");
            fail("expected UnknownHostException when every resolved address is blocked");
        } catch (UnknownHostException expected) {
            // fail closed
        }
    }

    @Test
    public void allowedAddressesPassThroughUnchanged() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("169.254.0.0/16"));

        InetAddress[] resolved =
                resolver(config, answering("8.8.8.8", "1.1.1.1")).resolve("ok.example.com");

        assertArrayEquals(
                new InetAddress[] {
                    InetAddress.getByName("8.8.8.8"), InetAddress.getByName("1.1.1.1")
                },
                resolved);
    }

    @Test
    public void explicitlyAllowedHostBypassesIpFilter() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("10.0.0.0/8"));
        config.setAllowedHosts(List.of("api.internal.corp"));

        InetAddress[] answer = {InetAddress.getByName("10.1.1.5")};
        InetAddress[] resolved =
                resolver(config, answering("10.1.1.5")).resolve("api.internal.corp");

        assertArrayEquals(answer, resolved);
    }

    @Test
    public void allowedIpOverridesBlockRange() throws Exception {
        HttpWorkerBlockConfig config = new HttpWorkerBlockConfig();
        config.setIps(List.of("10.0.0.0/8"));
        config.setAllowedIps(List.of("10.1.1.5"));

        InetAddress[] resolved = resolver(config, answering("10.1.1.5")).resolve("db.example.com");

        assertEquals(1, resolved.length);
        assertEquals(InetAddress.getByName("10.1.1.5"), resolved[0]);
    }

    @Test
    public void noRulesLeaveEveryAddressUntouched() throws Exception {
        InetAddress[] resolved =
                resolver(new HttpWorkerBlockConfig(), answering("169.254.169.254")).resolve("x");
        assertArrayEquals(new InetAddress[] {InetAddress.getByName("169.254.169.254")}, resolved);
    }
}
