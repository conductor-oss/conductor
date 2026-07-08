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
package com.netflix.conductor.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Serves the UI runtime config {@code /context.js} so the embedded AgentSpan agent pages are gated
 * by the server's {@code conductor.integrations.ai.enabled} flag.
 *
 * <p>Mirrors orkes-conductor's {@code UIContextGenerator}: a controller mapping takes precedence
 * over the bundled static {@code /static/context.js}, so we serve the bundled file (preserving all
 * other feature flags) and append a runtime override of {@code AGENTSPAN_ENABLED}. The UI reads
 * {@code window.conductor.AGENTSPAN_ENABLED} to show/hide the Agents, Executions, Skills and
 * Secrets pages. Only active when UI serving is enabled.
 *
 * <p>Writes directly to the response to avoid content-negotiation on {@code
 * application/javascript}.
 */
@RestController
@ConditionalOnProperty(
        name = "conductor.enable.ui.serving",
        havingValue = "true",
        matchIfMissing = true)
public class AgentSpanUiContextController {

    private final boolean agentSpanEnabled;

    public AgentSpanUiContextController(
            @Value("${conductor.integrations.ai.enabled:false}") boolean agentSpanEnabled) {
        this.agentSpanEnabled = agentSpanEnabled;
    }

    @GetMapping("/context.js")
    public void contextJs(HttpServletResponse response) throws IOException {
        response.setContentType("application/javascript");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

        StringBuilder js = new StringBuilder();

        // Start from the bundled UI context.js so all other feature flags are preserved.
        Resource bundled = new ClassPathResource("static/context.js");
        if (bundled.exists()) {
            try (InputStream in = bundled.getInputStream()) {
                js.append(StreamUtils.copyToString(in, StandardCharsets.UTF_8));
            }
        } else {
            js.append("window.conductor = window.conductor || {};\n");
        }

        // Runtime override driven by the server configuration.
        js.append("\n// Injected by Conductor server (conductor.integrations.ai.enabled)\n");
        js.append("window.conductor = window.conductor || {};\n");
        js.append("window.conductor.AGENTSPAN_ENABLED = ").append(agentSpanEnabled).append(";\n");

        response.getWriter().write(js.toString());
    }
}
