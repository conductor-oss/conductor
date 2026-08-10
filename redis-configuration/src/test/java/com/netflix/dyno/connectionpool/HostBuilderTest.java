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
package com.netflix.dyno.connectionpool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostBuilderTest {

    @Test
    void createHost_basicFields() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setPort(6379)
                        .setRack("us-east-1a")
                        .setStatus(Host.Status.Up)
                        .createHost();

        assertEquals("redis1", host.getHostName());
        assertEquals(6379, host.getPort());
        assertEquals("us-east-1a", host.getRack());
        assertTrue(host.isUp());
    }

    @Test
    void createHost_withPassword() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setPort(6379)
                        .setRack("rack1")
                        .setPassword("secret")
                        .createHost();

        assertEquals("secret", host.getPassword());
    }

    @Test
    void createHost_emptyPasswordBecomesNull() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setPort(6379)
                        .setRack("rack1")
                        .setPassword("")
                        .createHost();

        assertNull(host.getPassword());
    }

    @Test
    void createHost_nullPasswordStaysNull() {
        Host host =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();

        assertNull(host.getPassword());
    }

    @Test
    void createHost_allFields() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setIpAddress("10.0.0.1")
                        .setPort(6379)
                        .setSecurePort(6380)
                        .setDatastorePort(22122)
                        .setRack("us-east-1a")
                        .setDatacenter("us-east-1")
                        .setStatus(Host.Status.Up)
                        .setHashtag("{app1}")
                        .setPassword("pass")
                        .createHost();

        assertEquals("redis1", host.getHostName());
        assertEquals("10.0.0.1", host.getIpAddress());
        assertEquals(6379, host.getPort());
        assertEquals(6380, host.getSecurePort());
        assertEquals(22122, host.getDatastorePort());
        assertEquals("us-east-1a", host.getRack());
        assertEquals("us-east-1", host.getDatacenter());
        assertTrue(host.isUp());
        assertEquals("{app1}", host.getHashtag());
        assertEquals("pass", host.getPassword());
    }

    @Test
    void createHost_defaultPort() {
        Host host = new HostBuilder().setHostname("redis1").setRack("rack1").createHost();

        assertEquals(Host.DEFAULT_PORT, host.getPort());
    }

    @Test
    void createHost_defaultStatus_isDown() {
        Host host = new HostBuilder().setHostname("redis1").setRack("rack1").createHost();

        assertFalse(host.isUp());
        assertEquals(Host.Status.Down, host.getStatus());
    }

    // --- Host behavior ---

    @Test
    void host_getHostAddress_prefersIpAddress() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setIpAddress("10.0.0.1")
                        .setPort(6379)
                        .setRack("rack1")
                        .createHost();

        assertEquals("10.0.0.1", host.getHostAddress());
    }

    @Test
    void host_getHostAddress_fallsBackToHostname() {
        Host host =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();

        assertEquals("redis1", host.getHostAddress());
    }

    @Test
    void host_setStatus() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setPort(6379)
                        .setRack("rack1")
                        .setStatus(Host.Status.Down)
                        .createHost();

        assertFalse(host.isUp());
        host.setStatus(Host.Status.Up);
        assertTrue(host.isUp());
    }

    @Test
    void host_setHashtag() {
        Host host =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();

        assertNull(host.getHashtag());
        host.setHashtag("{tag}");
        assertEquals("{tag}", host.getHashtag());
    }

    // --- Equality ---

    @Test
    void host_equals_sameHostAndRack() {
        Host a =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();
        Host b =
                new HostBuilder().setHostname("redis1").setPort(7000).setRack("rack1").createHost();
        // Port is deliberately excluded from equality
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void host_equals_differentHostname() {
        Host a =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();
        Host b =
                new HostBuilder().setHostname("redis2").setPort(6379).setRack("rack1").createHost();
        assertNotEquals(a, b);
    }

    @Test
    void host_equals_differentRack() {
        Host a =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();
        Host b =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack2").createHost();
        assertNotEquals(a, b);
    }

    @Test
    void host_equals_reflexive() {
        Host a =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();
        assertEquals(a, a);
    }

    @Test
    void host_equals_null() {
        Host a =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();
        assertNotEquals(a, null);
    }

    // --- Comparable ---

    @Test
    void host_compareTo_sortsByHostnameThenRack() {
        Host a = new HostBuilder().setHostname("a").setPort(6379).setRack("rack2").createHost();
        Host b = new HostBuilder().setHostname("b").setPort(6379).setRack("rack1").createHost();
        Host a2 = new HostBuilder().setHostname("a").setPort(6379).setRack("rack1").createHost();

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertTrue(a.compareTo(a2) > 0); // same hostname, rack2 > rack1
    }

    // --- Clone ---

    @Test
    void host_clone_copiesAllFields() {
        Host original =
                new HostBuilder()
                        .setHostname("redis1")
                        .setIpAddress("10.0.0.1")
                        .setPort(6379)
                        .setSecurePort(6380)
                        .setDatastorePort(22122)
                        .setRack("us-east-1a")
                        .setDatacenter("us-east-1")
                        .setStatus(Host.Status.Up)
                        .setHashtag("{app1}")
                        .setPassword("pass")
                        .createHost();

        Host clone = Host.clone(original);

        assertEquals(original.getHostName(), clone.getHostName());
        assertEquals(original.getIpAddress(), clone.getIpAddress());
        assertEquals(original.getPort(), clone.getPort());
        assertEquals(original.getSecurePort(), clone.getSecurePort());
        assertEquals(original.getDatastorePort(), clone.getDatastorePort());
        assertEquals(original.getRack(), clone.getRack());
        assertEquals(original.getDatacenter(), clone.getDatacenter());
        assertEquals(original.getStatus(), clone.getStatus());
        assertEquals(original.getHashtag(), clone.getHashtag());
        assertEquals(original.getPassword(), clone.getPassword());
        assertEquals(original, clone);
    }

    // --- toString ---

    @Test
    void host_toString_masksPassword() {
        Host host =
                new HostBuilder()
                        .setHostname("redis1")
                        .setPort(6379)
                        .setRack("rack1")
                        .setPassword("secret")
                        .createHost();

        String str = host.toString();
        assertTrue(str.contains("masked"));
        assertFalse(str.contains("secret"));
    }

    @Test
    void host_toString_showsNullWhenNoPassword() {
        Host host =
                new HostBuilder().setHostname("redis1").setPort(6379).setRack("rack1").createHost();

        String str = host.toString();
        assertTrue(str.contains("password=null"));
    }

    // --- NO_HOST constant ---

    @Test
    void noHost_constant() {
        assertNotNull(Host.NO_HOST);
        assertEquals("UNKNOWN", Host.NO_HOST.getHostName());
        assertEquals(0, Host.NO_HOST.getPort());
        assertEquals("UNKNOWN", Host.NO_HOST.getRack());
    }
}
