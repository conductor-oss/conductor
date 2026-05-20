/*
 * Copyright 2011 Conductor Authors.
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

import java.net.InetSocketAddress;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

/**
 * Class encapsulating information about a host.
 *
 * <p>This is immutable except for the host status. Note that the HostSupplier may not know the
 * Dynomite port, whereas the Host object created by the load balancer may receive the port the
 * cluster_describe REST call. Hence, we must not use the port in the equality and hashCode
 * calculations.
 *
 * @author poberai
 * @author ipapapa
 */
public class Host implements Comparable<Host> {

    public static final int DEFAULT_PORT = 8102;
    public static final int DEFAULT_DATASTORE_PORT = 22122;
    public static final Host NO_HOST =
            new HostBuilder()
                    .setHostname("UNKNOWN")
                    .setIpAddress("UNKNOWN")
                    .setPort(0)
                    .setRack("UNKNOWN")
                    .createHost();

    private final String hostname;
    private final String ipAddress;
    private final int port;
    private final int securePort;
    private final int datastorePort;
    private final InetSocketAddress socketAddress;
    private final String rack;
    private final String datacenter;
    private String hashtag;
    private Status status = Status.Down;
    private final String password;

    public enum Status {
        Up,
        Down;
    }

    public Host(
            String hostname,
            String ipAddress,
            int port,
            int securePort,
            int datastorePort,
            String rack,
            String datacenter,
            Status status,
            String hashtag,
            String password) {
        this.hostname = hostname;
        this.ipAddress = ipAddress;
        this.port = port;
        this.securePort = securePort;
        this.datastorePort = datastorePort;
        this.rack = rack;
        this.status = status;
        this.datacenter = datacenter;
        this.hashtag = hashtag;
        this.password = StringUtils.isEmpty(password) ? null : password;

        // Used for the unit tests to prevent host name resolution
        if (port != -1) {
            this.socketAddress = new InetSocketAddress(hostname, port);
        } else {
            this.socketAddress = null;
        }
    }

    public String getHostAddress() {
        if (this.ipAddress != null) {
            return ipAddress;
        }
        return hostname;
    }

    public String getHostName() {
        return hostname;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public int getSecurePort() {
        return securePort;
    }

    public int getDatastorePort() {
        return datastorePort;
    }

    public String getDatacenter() {
        return datacenter;
    }

    public String getRack() {
        return rack;
    }

    public String getHashtag() {
        return hashtag;
    }

    public void setHashtag(String hashtag) {
        this.hashtag = hashtag;
    }

    public Host setStatus(Status condition) {
        status = condition;
        return this;
    }

    public boolean isUp() {
        return status == Status.Up;
    }

    public String getPassword() {
        return password;
    }

    public Status getStatus() {
        return status;
    }

    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }

    /**
     * Equality checks will fail in collections between Host objects created from the HostSupplier,
     * which may not know the Dynomite port, and the Host objects created by the token map supplier.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((hostname == null) ? 0 : hostname.hashCode());
        result = prime * result + ((rack == null) ? 0 : rack.hashCode());

        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;

        if (getClass() != obj.getClass()) return false;

        Host other = (Host) obj;

        boolean equals = true;

        equals &= (hostname != null) ? hostname.equals(other.hostname) : other.hostname == null;
        equals &= (rack != null) ? rack.equals(other.rack) : other.rack == null;

        return equals;
    }

    @Override
    public int compareTo(Host o) {
        int compared = this.hostname.compareTo(o.hostname);
        if (compared != 0) {
            return compared;
        }
        return this.rack.compareTo(o.rack);
    }

    @Override
    public String toString() {

        return "Host [hostname="
                + hostname
                + ", ipAddress="
                + ipAddress
                + ", port="
                + port
                + ", rack: "
                + rack
                + ", datacenter: "
                + datacenter
                + ", status: "
                + status.name()
                + ", hashtag="
                + hashtag
                + ", password="
                + (Objects.nonNull(password) ? "masked" : "null")
                + "]";
    }

    public static Host clone(Host host) {
        return new HostBuilder()
                .setHostname(host.getHostName())
                .setIpAddress(host.getIpAddress())
                .setPort(host.getPort())
                .setSecurePort(host.getSecurePort())
                .setRack(host.getRack())
                .setDatastorePort(host.getDatastorePort())
                .setDatacenter(host.getDatacenter())
                .setStatus(host.getStatus())
                .setHashtag(host.getHashtag())
                .setPassword(host.getPassword())
                .createHost();
    }
}
