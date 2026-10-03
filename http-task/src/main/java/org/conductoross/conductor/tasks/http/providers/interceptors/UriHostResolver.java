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

import java.net.URI;
import java.util.Locale;

/**
 * Derives the host that the HTTP client will actually connect to from a request URI, so the SSRF
 * block rules match against the same host.
 *
 * <p>{@link URI#getHost()} returns {@code null} for authorities the RFC 3986 grammar rejects but
 * the underlying resolver still accepts — e.g. a trailing dot ({@code 127.0.0.1.}) or an underscore
 * ({@code foo_bar.internal}). Callers that only checked {@code getHost()} would skip the block
 * check and let the request through. This helper falls back to the raw authority in that case and
 * strips a single trailing dot so {@code 127.0.0.1.} normalizes to {@code 127.0.0.1} before
 * matching.
 *
 * <p>Returns {@code null} only when no host can be derived at all; callers must treat that as
 * "blocked" (fail closed) rather than skipping the check.
 */
final class UriHostResolver {

    private UriHostResolver() {}

    static String resolveHost(URI uri) {
        if (uri == null) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            host = hostFromAuthority(uri.getRawAuthority());
        }
        return normalize(host);
    }

    private static String hostFromAuthority(String authority) {
        if (authority == null || authority.isEmpty()) {
            return null;
        }
        int at = authority.indexOf('@');
        if (at >= 0) {
            authority = authority.substring(at + 1);
        }
        // Authorities reach this fallback only when getHost() failed, which never happens for IPv6
        // literals (they parse cleanly), so a trailing ":port" is unambiguous.
        int colon = authority.lastIndexOf(':');
        if (colon >= 0) {
            authority = authority.substring(0, colon);
        }
        return authority.isEmpty() ? null : authority;
    }

    private static String normalize(String host) {
        if (host == null) {
            return null;
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.isEmpty()) {
            return null;
        }
        return host.toLowerCase(Locale.ROOT);
    }
}
