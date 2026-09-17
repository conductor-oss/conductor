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
package org.conductoross.conductor.tasks.http.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSRF guard rails for the {@code HTTP} system task.
 *
 * <p>All lists default to empty, so no host or IP is blocked unless a deployment opts in via {@code
 * conductor.worker.http.block.*}. This mirrors the enterprise Conductor behavior: the HTTP task is
 * intentionally able to call external endpoints, and blocking is a deployment-level policy rather
 * than a forced default.
 *
 * <pre>
 * conductor:
 *   worker:
 *     http:
 *       block:
 *         ips:
 *           - "169.254.0.0/16"   # link-local / cloud metadata
 *           - "127.0.0.0/8"      # loopback
 *           - "10.0.0.0/8"       # RFC 1918
 *         hosts:
 *           - "*.internal.corp"
 *         allowed-hosts:
 *           - "api.trusted-partner.com"
 * </pre>
 *
 * @see org.conductoross.conductor.tasks.http.providers.interceptors.RestTemplateInterceptor
 */
@Component
@ConfigurationProperties("conductor.worker.http.block")
public class HttpWorkerBlockConfig {

    private List<String> ips = List.of();
    private List<String> hosts = List.of();
    private List<String> allowedIps = List.of();
    private List<String> allowedHosts = List.of();

    public List<String> getIps() {
        return ips;
    }

    public void setIps(List<String> ips) {
        this.ips = ips;
    }

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public List<String> getAllowedHosts() {
        return allowedHosts;
    }

    public void setAllowedHosts(List<String> allowedHosts) {
        this.allowedHosts = allowedHosts;
    }
}
