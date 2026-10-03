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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UriHostResolverTest {

    @Test
    public void plainHostIsReturnedLowercased() {
        assertEquals(
                "example.com", UriHostResolver.resolveHost(URI.create("http://Example.COM/x")));
    }

    @Test
    public void literalIpIsReturned() {
        assertEquals("8.8.8.8", UriHostResolver.resolveHost(URI.create("http://8.8.8.8/")));
    }

    @Test
    public void trailingDotIsStrippedWhenHostParses() {
        // java.net.URI returns "example.com." here; we still normalize away the dot.
        assertEquals(
                "example.com", UriHostResolver.resolveHost(URI.create("http://example.com./")));
    }

    @Test
    public void trailingDotIpFallsBackToAuthorityAndIsNormalized() {
        // getHost() is null for a trailing-dot IP; recover it from the raw authority.
        assertEquals(
                "127.0.0.1",
                UriHostResolver.resolveHost(URI.create("http://127.0.0.1.:8080/secret")));
    }

    @Test
    public void underscoreHostFallsBackToAuthority() {
        assertEquals(
                "foo_bar.attacker.tld",
                UriHostResolver.resolveHost(URI.create("http://foo_bar.attacker.tld/x")));
    }

    @Test
    public void userInfoAndPortAreStrippedFromAuthorityFallback() {
        assertEquals(
                "foo_bar.attacker.tld",
                UriHostResolver.resolveHost(URI.create("http://user@foo_bar.attacker.tld:9000/x")));
    }

    @Test
    public void metadataIpWithTrailingDotIsNormalized() {
        assertEquals(
                "169.254.169.254",
                UriHostResolver.resolveHost(URI.create("http://169.254.169.254./latest")));
    }

    @Test
    public void nullWhenNoHostCanBeDerived() {
        assertNull(UriHostResolver.resolveHost(URI.create("file:///etc/passwd")));
        assertNull(UriHostResolver.resolveHost(null));
    }
}
