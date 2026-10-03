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
import java.util.stream.Collectors;

import org.conductoross.conductor.tasks.http.config.HttpWorkerBlockConfig;
import org.springframework.stereotype.Component;

@Component
public class IPCheckerImpl implements IPChecker {

    private final List<String> blockedIps;
    private final List<String> allowedIps;

    public IPCheckerImpl(HttpWorkerBlockConfig config) {
        // Instantiate each matcher once at startup so a malformed pattern fails fast.
        this.blockedIps =
                config.getIps().stream().peek(IpAddressMatcher::new).collect(Collectors.toList());
        this.allowedIps =
                config.getAllowedIps().stream()
                        .peek(IpAddressMatcher::new)
                        .collect(Collectors.toList());
    }

    @Override
    public IPCheckResult isBlocked(String ipStr) {
        if (allowedIps.stream()
                .map(IpAddressMatcher::new)
                .anyMatch(matcher -> matcher.matches(ipStr))) {
            return IPCheckResult.allowed();
        }

        return blockedIps.stream()
                .map(IpAddressMatcher::new)
                .filter(matcher -> matcher.matches(ipStr))
                .findFirst()
                .map(matcher -> IPCheckResult.blockedBy(matcher.getIpAddress()))
                .orElseGet(IPCheckResult::allowed);
    }
}
