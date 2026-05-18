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

import org.conductoross.conductor.mcp.controller.WorkflowMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "conductor.mcp-server.enabled", havingValue = "true")
@ComponentScan(basePackageClasses = McpServerConfiguration.class)
public class McpServerConfiguration {
    @Bean
    ToolCallbackProvider workflowMcpToolsCallbackProvider(WorkflowMcpTools workflowMcpTools) {
        return MethodToolCallbackProvider.builder().toolObjects(workflowMcpTools).build();
    }
}
