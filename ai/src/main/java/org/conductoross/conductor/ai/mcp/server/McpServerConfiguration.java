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

import org.conductoross.conductor.config.McpServerEnabledCondition;
import org.conductoross.conductor.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowService;

import tools.jackson.databind.ObjectMapper;

/** Wires the MCP server when {@code conductor.mcp.server.enabled=true}. */
@Configuration(proxyBeanMethods = false)
@Conditional(McpServerEnabledCondition.class)
public class McpServerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

    static final String ASYNC_TIMEOUT_PROPERTY = "spring.mvc.async.request-timeout";

    /**
     * The Tomcat async request timeout that applies when {@code spring.mvc.async.request-timeout}
     * is unset (measured on the OSS server: a 40 s wait was cut at 30 s).
     */
    static final Duration CONTAINER_DEFAULT_ASYNC_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    @Bean
    public WorkflowToolSource mcpWorkflowToolSource(
            MetadataService metadataService,
            WorkflowService workflowService,
            SchemaService schemaService,
            McpServerProperties properties) {
        return new WorkflowToolSource(
                metadataService, workflowService, schemaService, properties, mapper);
    }

    @Bean
    public McpExecutions mcpExecutions(WorkflowService workflowService) {
        return new McpExecutions() {
            @Override
            public com.netflix.conductor.common.run.Workflow get(String executionId) {
                return workflowService.getExecutionStatus(executionId, false);
            }

            @Override
            public void terminate(String executionId, String reason) {
                workflowService.terminateWorkflow(executionId, reason);
            }
        };
    }

    @Bean
    public McpResults mcpResults(McpServerProperties properties) {
        return new McpResults(mapper, properties);
    }

    /** Merges every {@link McpToolSource} bean, such as the AgentSpan agent source. */
    @Bean
    public McpToolRegistry mcpToolRegistry(List<McpToolSource> sources) {
        return new McpToolRegistry(sources);
    }

    @Bean
    public McpServerMethods mcpServerMethods(
            McpToolRegistry registry,
            McpServerProperties properties,
            McpResults results,
            McpExecutions executions) {
        return new McpServerMethods(registry, mapper, properties, results, executions);
    }

    @Bean
    public McpServerProtocol mcpServerProtocol(
            McpServerMethods methods, McpServerProperties properties, Environment environment) {
        Duration asyncTimeout = environment.getProperty(ASYNC_TIMEOUT_PROPERTY, Duration.class);
        timeoutWarnings(properties, asyncTimeout).forEach(log::warn);
        return new McpServerProtocol(mapper, methods.handlers());
    }

    /**
     * Returns a warning for each wait cap that is not below the async request timeout. Past that
     * timeout the client gets a bare HTTP 500 instead of the server's own result.
     *
     * @param asyncTimeout {@code spring.mvc.async.request-timeout}, or {@code null} when unset
     */
    static List<String> timeoutWarnings(McpServerProperties properties, Duration asyncTimeout) {
        Duration limit = asyncTimeout != null ? asyncTimeout : CONTAINER_DEFAULT_ASYNC_TIMEOUT;
        String source =
                asyncTimeout != null
                        ? ASYNC_TIMEOUT_PROPERTY + " (" + limit.toSeconds() + " s)"
                        : "the container default async request timeout ("
                                + limit.toSeconds()
                                + " s; "
                                + ASYNC_TIMEOUT_PROPERTY
                                + " is unset)";
        List<String> warnings = new ArrayList<>();
        check(warnings, "maxSyncWaitSeconds", properties.getMaxSyncWaitSeconds(), limit, source);
        check(warnings, "blockingCapSeconds", properties.getBlockingCapSeconds(), limit, source);
        return warnings;
    }

    private static void check(
            List<String> warnings, String name, int seconds, Duration limit, String source) {
        if (Duration.ofSeconds(seconds).compareTo(limit) >= 0) {
            warnings.add(
                    "MCP server: conductor.mcp.server."
                            + name
                            + " ("
                            + seconds
                            + " s) is not below "
                            + source
                            + ". Calls that wait that long fail with HTTP 500; lower it or raise "
                            + ASYNC_TIMEOUT_PROPERTY
                            + ".");
        }
    }
}
