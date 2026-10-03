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
public class HostCheckerImpl implements HostChecker {

    private final List<String> blockedHosts;
    private final List<String> allowedHosts;

    public HostCheckerImpl(HttpWorkerBlockConfig config) {
        this.blockedHosts =
                config.getHosts().stream().map(String::toLowerCase).collect(Collectors.toList());
        this.allowedHosts =
                config.getAllowedHosts().stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
    }

    @Override
    public boolean isBlocked(String host) {
        return matchesAny(blockedHosts, host) && !matchesAny(allowedHosts, host);
    }

    @Override
    public boolean isExplicitlyAllowed(String host) {
        return matchesAny(allowedHosts, host);
    }

    // Supports exact matches and single-level wildcards (*.example.com matches foo.example.com
    // only).
    private static boolean matchesAny(List<String> patterns, String host) {
        String lowerHost = host.toLowerCase();
        for (String pattern : patterns) {
            if (pattern.startsWith("*.")) {
                if (matchesWildcard(pattern, lowerHost)) {
                    return true;
                }
            } else if (pattern.equals(lowerHost)) {
                return true;
            }
        }
        return false;
    }

    // *.example.com -> suffix=".example.com"; label must be non-empty and dot-free (one DNS label).
    private static boolean matchesWildcard(String pattern, String host) {
        String suffix = pattern.substring(1);
        if (!host.endsWith(suffix)) {
            return false;
        }
        String label = host.substring(0, host.length() - suffix.length());
        return !label.isEmpty() && !label.contains(".");
    }
}
