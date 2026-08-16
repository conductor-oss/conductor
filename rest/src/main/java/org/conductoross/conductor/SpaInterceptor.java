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
package org.conductoross.conductor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(
        value = "conductor.enable.ui.serving",
        havingValue = "true",
        matchIfMissing = true)
public class SpaInterceptor implements HandlerInterceptor {

    /**
     * A2A server base path — backend JSON-RPC, never an SPA route (see {@code
     * A2AServerProperties}).
     */
    @Value("${conductor.a2a.server.basePath:/a2a}")
    private String a2aBasePath;

    public SpaInterceptor() {
        log.info("Serving UI on /");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // The SPA fallback exists for browser navigation, which is always GET. Forwarding any
        // other method to index.html turns a backend POST/PUT/etc. on an unmatched path into a
        // misleading "method not supported" on the static page — so only GET is ever rewritten.
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        log.debug("Service SPA page {}", path);

        // Skip backend APIs, OpenAPI docs, health endpoints, A2A server, and static resources.
        if (path.startsWith("/api/")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.equals(a2aBasePath)
                || path.startsWith(a2aBasePath + "/")
                || path.equals("/error")
                || path.contains(".")) {
            return true;
        }

        // Forward to index.html
        request.getRequestDispatcher("/index.html").forward(request, response);
        return false;
    }
}
