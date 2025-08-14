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

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SpaInterceptor implements HandlerInterceptor {

    public SpaInterceptor() {
        log.info("Serving UI on /");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();
        log.debug("Service SPA page {}", path);

        // Skip API, health checks, actuator, and static resources
        if (path.startsWith("/api/")
                || path.equals("/health")
                || path.equals("/api-docs")
                || path.equals("/error")
                || path.contains(".")) {
            return true;
        }

        // Forward to index.html
        request.getRequestDispatcher("/index.html").forward(request, response);
        return false;
    }
}
