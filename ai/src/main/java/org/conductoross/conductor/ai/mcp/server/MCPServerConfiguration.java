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
package org.conductoross.conductor.ai.mcp.server;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.conductoross.conductor.config.MCPServerEnabledCondition;
import org.conductoross.conductor.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowService;

import tools.jackson.databind.ObjectMapper;

/** Wires the MCP server when {@code conductor.mcp.server.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@Conditional(MCPServerEnabledCondition.class)
public class MCPServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MCPServerConfiguration.class);

    static final String ASYNC_TIMEOUT_PROPERTY = "spring.mvc.async.request-timeout";

    /**
     * The Tomcat async request timeout that applies when {@code spring.mvc.async.request-timeout}
     * is unset (measured on the OSS server: a 40 s wait was cut at 30 s).
     */
    static final Duration CONTAINER_DEFAULT_ASYNC_TIMEOUT = Duration.ofSeconds(30);

    /** Time a request needs besides its wait cap: starting the execution and the final read. */
    static final Duration TIMEOUT_MARGIN = Duration.ofSeconds(5);

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    @Bean
    public WorkflowToolSource mcpWorkflowToolSource(
            MetadataService metadataService,
            WorkflowService workflowService,
            SchemaService schemaService,
            MCPServerProperties properties) {
        return new WorkflowToolSource(
                metadataService, workflowService, schemaService, properties, mapper);
    }

    @Bean
    public MCPExecutions mcpExecutions(WorkflowService workflowService) {
        return new MCPExecutions() {
            @Override
            public Workflow get(String executionId) {
                return workflowService.getExecutionStatus(executionId, false);
            }

            @Override
            public void terminate(String executionId, String reason) {
                workflowService.terminateWorkflow(executionId, reason);
            }
        };
    }

    @Bean
    public MCPResults mcpResults(MCPServerProperties properties) {
        return new MCPResults(mapper, properties);
    }

    /** Merges every {@link MCPToolSource} bean, such as the AgentSpan agent source. */
    @Bean
    public MCPToolRegistry mcpToolRegistry(List<MCPToolSource> sources) {
        return new MCPToolRegistry(sources);
    }

    @Bean
    public MCPServerMethods mcpServerMethods(
            MCPToolRegistry registry,
            MCPServerProperties properties,
            MCPResults results,
            MCPExecutions executions) {
        return new MCPServerMethods(registry, mapper, properties, results, executions);
    }

    @Bean
    public MCPServerProtocol mcpServerProtocol(
            MCPServerMethods methods, MCPServerProperties properties, Environment environment) {
        Duration asyncTimeout = environment.getProperty(ASYNC_TIMEOUT_PROPERTY, Duration.class);
        timeoutWarnings(properties, asyncTimeout).forEach(log::warn);
        return new MCPServerProtocol(mapper, methods.handlers());
    }

    /**
     * Returns a warning for each wait cap that leaves less than {@link #TIMEOUT_MARGIN} before the
     * async request timeout. Past that timeout the client gets a bare HTTP 500 instead of the
     * server's own result. A timeout of zero or less turns the timeout off, so nothing is warned.
     *
     * @param asyncTimeout {@code spring.mvc.async.request-timeout}, or {@code null} when unset
     */
    static List<String> timeoutWarnings(MCPServerProperties properties, Duration asyncTimeout) {
        if (asyncTimeout != null && !asyncTimeout.isPositive()) {
            return List.of();
        }
        Duration limit = asyncTimeout != null ? asyncTimeout : CONTAINER_DEFAULT_ASYNC_TIMEOUT;
        String source =
                asyncTimeout != null
                        ? "%s (%d s)".formatted(ASYNC_TIMEOUT_PROPERTY, limit.toSeconds())
                        : "the container default async request timeout (%d s; %s is unset)"
                                .formatted(limit.toSeconds(), ASYNC_TIMEOUT_PROPERTY);
        List<String> warnings = new ArrayList<>();
        check(
                warnings,
                MCPServerProperties.MAX_SYNC_WAIT_SECONDS,
                properties.getMaxSyncWaitSeconds(),
                limit,
                source);
        check(
                warnings,
                MCPServerProperties.BLOCKING_CAP_SECONDS,
                properties.getBlockingCapSeconds(),
                limit,
                source);
        return warnings;
    }

    private static void check(
            List<String> warnings, String name, int seconds, Duration limit, String source) {
        if (Duration.ofSeconds(seconds).plus(TIMEOUT_MARGIN).compareTo(limit) > 0) {
            warnings.add(
                    ("MCP server: %s.%s (%d s) leaves less than %d s before %s. Calls that wait"
                                    + " that long can fail with HTTP 500; lower it or raise %s.")
                            .formatted(
                                    MCPServerProperties.PREFIX,
                                    name,
                                    seconds,
                                    TIMEOUT_MARGIN.toSeconds(),
                                    source,
                                    ASYNC_TIMEOUT_PROPERTY));
        }
    }
}
