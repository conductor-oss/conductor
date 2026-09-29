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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Enforces the {@code HTTP} system task IP block rules at the moment the connection is resolved, so
 * the address that passes the check is the same one the client dials.
 *
 * <p>{@link RestTemplateInterceptor} and {@link BlockingRedirectStrategy} check only the first
 * address {@link InetAddress#getByName(String)} returns. The Apache HttpClient resolves the host
 * again independently and may connect to a later address in the list (or a different answer
 * entirely, for a rebinding DNS server). A DNS answer of {@code [<public ip>, 169.254.169.254]}
 * would pass the pre-flight check and then connect to the metadata endpoint.
 *
 * <p>Plugging this resolver into the client's connection manager closes that gap: every resolved
 * address is checked against {@link IPChecker}, and only allowed addresses are handed back. If a
 * host resolves solely to blocked addresses the resolution fails, so the client never connects.
 * Hosts explicitly allow-listed by name ({@code conductor.worker.http.block.allowed-hosts}) bypass
 * the filter, matching {@link HostAndIPChecker}'s precedence.
 *
 * <p>OSS-side addition — Orkes' variant relies on the pre-flight check alone; this hardens against
 * the resolve-then-connect gap Miguel flagged on conductor-oss/conductor#1644.
 */
@Component
public class BlockingDnsResolver implements DnsResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockingDnsResolver.class);

    private final HostChecker hostChecker;
    private final IPChecker ipChecker;
    private final DnsResolver delegate;

    public BlockingDnsResolver(HostChecker hostChecker, IPChecker ipChecker) {
        this(hostChecker, ipChecker, SystemDefaultDnsResolver.INSTANCE);
    }

    BlockingDnsResolver(HostChecker hostChecker, IPChecker ipChecker, DnsResolver delegate) {
        this.hostChecker = hostChecker;
        this.ipChecker = ipChecker;
        this.delegate = delegate;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        final InetAddress[] resolved = delegate.resolve(host);

        // An operator that explicitly trusts a host by name opts out of the IP filter, matching the
        // allowed-hosts precedence enforced by HostAndIPChecker.
        if (hostChecker.isExplicitlyAllowed(stripTrailingDot(host))) {
            return resolved;
        }

        final List<InetAddress> allowed = new ArrayList<>(resolved.length);
        for (InetAddress address : resolved) {
            if (ipChecker.isBlocked(address.getHostAddress()).isBlocked()) {
                LOGGER.warn(
                        "Blocked resolved address {} for host {} (SSRF IP block rules)",
                        address.getHostAddress(),
                        host);
            } else {
                allowed.add(address);
            }
        }

        if (allowed.isEmpty()) {
            throw new UnknownHostException(
                    "All resolved addresses for host " + host + " are blocked by SSRF rules");
        }
        return allowed.toArray(new InetAddress[0]);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }

    private static String stripTrailingDot(String host) {
        if (host == null) {
            return null;
        }
        String normalized = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        return normalized.toLowerCase(Locale.ROOT);
    }
}
