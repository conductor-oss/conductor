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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    private McpTool tool(String name, McpTool.Kind kind) {
        return new McpTool(name, kind + " " + name, mapper.createObjectNode(), kind);
    }

    private McpToolSource source(McpTool... tools) {
        return new McpToolSource() {
            @Override
            public List<McpTool> listTools() {
                return List.of(tools);
            }

            @Override
            public Optional<McpTool> findTool(String name) {
                return listTools().stream().filter(t -> t.name().equals(name)).findFirst();
            }

            @Override
            public Map<String, Object> metadata(String name) {
                return findTool(name)
                        .<Map<String, Object>>map(t -> Map.of("from", t.kind().name()))
                        .orElse(null);
            }

            @Override
            public String start(String name, tools.jackson.databind.node.ObjectNode a) {
                return "exec-" + name;
            }
        };
    }

    @Test
    void metadataComesFromTheSourceOfTheToolsKind() {
        McpToolRegistry registry =
                new McpToolRegistry(
                        List.of(
                                source(tool("x", McpTool.Kind.WORKFLOW)),
                                source(tool("x", McpTool.Kind.AGENT))));
        McpToolRegistry.Resolved resolved = registry.resolve("x").orElseThrow();
        assertThat(resolved.tool().kind()).isEqualTo(McpTool.Kind.AGENT);
        assertThat(resolved.metadata()).containsEntry("from", "AGENT");
        assertThat(resolved.start(mapper.createObjectNode())).isEqualTo("exec-x");
        assertThat(registry.resolve("unknown")).isEmpty();
    }

    @Test
    void mergesSources_sortedByName() {
        McpToolRegistry registry =
                new McpToolRegistry(
                        List.of(
                                source(
                                        tool("zeta", McpTool.Kind.WORKFLOW),
                                        tool("alpha", McpTool.Kind.WORKFLOW)),
                                source(tool("mid", McpTool.Kind.AGENT))));
        assertThat(registry.listTools())
                .extracting(McpTool::name)
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void nameClash_listsTheAgentOnce() {
        McpToolRegistry registry =
                new McpToolRegistry(
                        List.of(
                                source(tool("support_bot", McpTool.Kind.WORKFLOW)),
                                source(tool("support_bot", McpTool.Kind.AGENT))));
        assertThat(registry.listTools())
                .singleElement()
                .extracting(McpTool::kind)
                .isEqualTo(McpTool.Kind.AGENT);
        assertThat(registry.findTool("support_bot"))
                .get()
                .extracting(McpTool::kind)
                .isEqualTo(McpTool.Kind.AGENT);
    }

    @Test
    void agentWinsWhateverTheSourceOrder() {
        McpToolRegistry registry =
                new McpToolRegistry(
                        List.of(
                                source(tool("x", McpTool.Kind.AGENT)),
                                source(tool("x", McpTool.Kind.WORKFLOW))));
        assertThat(registry.findTool("x"))
                .get()
                .extracting(McpTool::kind)
                .isEqualTo(McpTool.Kind.AGENT);
    }

    @Test
    void unknownTool_isEmpty() {
        assertThat(new McpToolRegistry(List.of()).findTool("nope")).isEmpty();
    }

    @Test
    void namesThatAreNotMcpToolNames_areNotOffered() {
        String tooLong = "a".repeat(129);
        McpToolRegistry registry =
                new McpToolRegistry(
                        List.of(
                                source(
                                        tool("nightly report", McpTool.Kind.WORKFLOW),
                                        tool("x{1}", McpTool.Kind.WORKFLOW),
                                        tool(tooLong, McpTool.Kind.WORKFLOW),
                                        tool("orders.v2_lookup-1", McpTool.Kind.WORKFLOW)),
                                source(tool("support bot", McpTool.Kind.AGENT))));

        assertThat(registry.listTools())
                .extracting(McpTool::name)
                .containsExactly("orders.v2_lookup-1");
        for (String name : List.of("nightly report", "x{1}", tooLong, "support bot")) {
            assertThat(registry.findTool(name)).as(name).isEmpty();
            assertThat(registry.exposes(name)).as(name).isFalse();
        }
        assertThat(registry.findTool("a".repeat(128))).isEmpty();
        assertThat(McpToolRegistry.isToolName("a".repeat(128))).isTrue();
    }
}
