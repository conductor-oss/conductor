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
package org.conductoross.conductor.ai.agentspan;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import org.springframework.web.filter.OncePerRequestFilter;

import dev.agentspan.runtime.context.RequestContext;
import dev.agentspan.runtime.context.RequestContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Populates AgentSpan's {@link RequestContextHolder} with a default principal id for the duration
 * of each request, so the AgentSpan controllers and services (which call {@link
 * RequestContextHolder#getRequiredUserId()}) work on an OSS server that has no per-user
 * authentication.
 *
 * <p>If a context is already present on the thread (e.g. a host security adapter set the real
 * principal), this filter leaves it untouched. Otherwise it sets the configured default id and
 * clears it in a finally block to avoid leaking across pooled threads.
 */
public class AgentSpanPrincipalFilter extends OncePerRequestFilter {

    private final String defaultUserId;

    public AgentSpanPrincipalFilter(String defaultUserId) {
        this.defaultUserId = defaultUserId;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean contextSetHere = false;
        if (RequestContextHolder.get().isEmpty()) {
            RequestContextHolder.set(
                    RequestContext.builder()
                            .requestId(UUID.randomUUID().toString())
                            .userId(defaultUserId)
                            .createdAt(Instant.now())
                            .build());
            contextSetHere = true;
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (contextSetHere) {
                RequestContextHolder.clear();
            }
        }
    }
}
