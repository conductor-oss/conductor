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
package com.netflix.dyno.connectionpool;

import static com.netflix.dyno.connectionpool.Host.DEFAULT_DATASTORE_PORT;
import static com.netflix.dyno.connectionpool.Host.DEFAULT_PORT;

public class HostBuilder {
    private String hostname;
    private int port = DEFAULT_PORT;
    private String rack;
    private String ipAddress = null;
    private int securePort = DEFAULT_PORT;
    private int datastorePort = DEFAULT_DATASTORE_PORT;
    private String datacenter = null;
    private Host.Status status = Host.Status.Down;
    private String hashtag = null;
    private String password = null;

    public HostBuilder setPort(int port) {
        this.port = port;
        return this;
    }

    public HostBuilder setRack(String rack) {
        this.rack = rack;
        return this;
    }

    public HostBuilder setHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public HostBuilder setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public HostBuilder setSecurePort(int securePort) {
        this.securePort = securePort;
        return this;
    }

    public HostBuilder setDatacenter(String datacenter) {
        this.datacenter = datacenter;
        return this;
    }

    public HostBuilder setStatus(Host.Status status) {
        this.status = status;
        return this;
    }

    public HostBuilder setHashtag(String hashtag) {
        this.hashtag = hashtag;
        return this;
    }

    public HostBuilder setPassword(String password) {
        this.password = password;
        return this;
    }

    public HostBuilder setDatastorePort(int datastorePort) {
        this.datastorePort = datastorePort;
        return this;
    }

    public Host createHost() {
        return new Host(
                hostname,
                ipAddress,
                port,
                securePort,
                datastorePort,
                rack,
                datacenter,
                status,
                hashtag,
                password);
    }
}
