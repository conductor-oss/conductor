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

import org.conductoross.conductor.ai.mcp.McpMethod;
import org.conductoross.conductor.service.SchemaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class McpServerConfigurationTest {

    @Configuration
    @EnableConfigurationProperties(McpServerProperties.class)
    @Import({McpServerConfiguration.class, McpServerResource.class})
    static class TestConfig {
        @Bean
        MetadataService metadataService() {
            return mock(MetadataService.class);
        }

        @Bean
        SchemaService schemaService() {
            return mock(SchemaService.class);
        }

        @Bean
        WorkflowService workflowService() {
            return mock(WorkflowService.class);
        }
    }

    @Test
    void serverOn_wiresToolsListWithTheWorkflowSource() {
        runner.withPropertyValues("conductor.mcp.server.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(WorkflowToolSource.class);
                            assertThat(ctx).hasSingleBean(McpToolRegistry.class);
                            assertThat(ctx.getBean(McpServerMethods.class).handlers())
                                    .containsKeys(
                                            McpMethod.TOOLS_LIST,
                                            McpMethod.TOOLS_CALL,
                                            McpMethod.TASKS_GET,
                                            McpMethod.TASKS_CANCEL);
                        });
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void serverOffByDefault() {
        runner.run(
                ctx -> {
                    assertThat(ctx).doesNotHaveBean(McpServerResource.class);
                    assertThat(ctx).doesNotHaveBean(McpServerProtocol.class);
                });
    }

    @Test
    void serverOn() {
        runner.withPropertyValues("conductor.mcp.server.enabled=true")
                .run(
                        ctx -> {
                            assertThat(ctx).hasSingleBean(McpServerResource.class);
                            assertThat(ctx).hasSingleBean(McpServerProtocol.class);
                        });
    }

    @Test
    void propertyDefaults() {
        McpServerProperties properties = new McpServerProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getExposedWorkflows()).isEmpty();
        assertThat(properties.isExposeAll()).isFalse();
        assertThat(properties.getSyncWaitSeconds()).isEqualTo(10);
        assertThat(properties.getMaxSyncWaitSeconds()).isEqualTo(25);
        assertThat(properties.getBlockingCapSeconds()).isEqualTo(25);
        assertThat(properties.getPollIntervalMs()).isEqualTo(5000);
    }

    @Test
    void propertiesBind() {
        runner.withPropertyValues(
                        "conductor.mcp.server.enabled=true",
                        "conductor.mcp.server.exposedWorkflows=a,b",
                        "conductor.mcp.server.syncWaitSeconds=3")
                .run(
                        ctx -> {
                            McpServerProperties properties = ctx.getBean(McpServerProperties.class);
                            assertThat(properties.getExposedWorkflows()).containsExactly("a", "b");
                            assertThat(properties.getSyncWaitSeconds()).isEqualTo(3);
                        });
    }
}
