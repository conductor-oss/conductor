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

import org.springframework.stereotype.Component;

@Component
public class HostAndIPChecker {

    private final HostChecker hostChecker;
    private final IPChecker ipChecker;

    public HostAndIPChecker(HostChecker hostChecker, IPChecker ipChecker) {
        this.hostChecker = hostChecker;
        this.ipChecker = ipChecker;
    }

    public boolean isAllowed(String host) throws UnknownHostException {
        if (hostChecker.isBlocked(host)) {
            return false;
        }

        if (hostChecker.isExplicitlyAllowed(host)) {
            return true;
        }

        // The InetAddress class has a cache to store successful as well as
        // unsuccessful host name resolutions.
        final String ip = InetAddress.getByName(host).getHostAddress();
        return !ipChecker.isBlocked(ip).isBlocked();
    }
}
