/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.http;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Allow-list policy for externally supplied AI integration targets.
 *
 * <p>MCP servers and OpenAPI documents are commonly supplied by users or models. They must not be
 * able to turn the Conductor server into an SSRF client. The policy therefore defaults to deny and
 * requires an exact configured HTTP(S) origin. Redirects must be validated by callers with this
 * same policy before they are followed.
 */
@Component
@ConfigurationProperties(prefix = "conductor.ai.outbound")
@Getter
@Setter
public class OutboundTargetPolicy {

    /** Exact HTTP(S) origins that MCP and AgentSpan API discovery may contact. */
    private List<String> allowedOrigins = List.of();

    /** Allows explicitly configured development/test origins that resolve to private addresses. */
    private boolean allowPrivateNetworks = false;

    /** Validates an externally supplied target before opening a connection. */
    public void validate(String target) {
        URI uri;
        try {
            uri = URI.create(target);
        } catch (IllegalArgumentException e) {
            throw new OutboundTargetDeniedException("Outbound target is not a valid URI");
        }

        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new OutboundTargetDeniedException(
                    "Only http and https outbound targets are allowed");
        }
        if (uri.getUserInfo() != null) {
            throw new OutboundTargetDeniedException("Outbound targets must not contain user-info");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new OutboundTargetDeniedException("Outbound target must include a host");
        }

        String origin = origin(uri);
        if (allowedOrigins == null
                || allowedOrigins.stream().map(this::configuredOrigin).noneMatch(origin::equals)) {
            throw new OutboundTargetDeniedException(
                    "Outbound target origin is not allow-listed: " + origin);
        }

        if (!allowPrivateNetworks) {
            validatePublicAddress(uri.getHost());
        }
    }

    /** Compares two targets by normalized origin for redirect credential handling. */
    public boolean isSameOrigin(String first, String second) {
        try {
            return origin(URI.create(first)).equals(origin(URI.create(second)));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String configuredOrigin(String value) {
        try {
            return origin(URI.create(value));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        return scheme + "://" + host + (port == -1 || port == defaultPort ? "" : ":" + port);
    }

    private void validatePublicAddress(String host) {
        try {
            List<String> denied = new ArrayList<>();
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    denied.add(address.getHostAddress());
                }
            }
            if (!denied.isEmpty()) {
                throw new OutboundTargetDeniedException(
                        "Outbound target resolves to a private or local address: " + denied);
            }
        } catch (OutboundTargetDeniedException e) {
            throw e;
        } catch (Exception e) {
            throw new OutboundTargetDeniedException("Unable to resolve outbound target host");
        }
    }
}
