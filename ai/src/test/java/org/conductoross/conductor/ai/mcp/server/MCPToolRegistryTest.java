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

class MCPToolRegistryTest {

    private final ObjectMapper mapper = new ObjectMapperProvider().getObjectMapper();

    private MCPTool tool(String name, MCPTool.Kind kind) {
        return new MCPTool(name, kind + " " + name, mapper.createObjectNode(), null, kind);
    }

    private MCPToolSource source(MCPTool... tools) {
        return new MCPToolSource() {
            @Override
            public List<MCPTool> listTools() {
                return List.of(tools);
            }

            @Override
            public Optional<MCPTool> findTool(String name) {
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
        MCPToolRegistry registry =
                new MCPToolRegistry(
                        List.of(
                                source(tool("x", MCPTool.Kind.WORKFLOW)),
                                source(tool("x", MCPTool.Kind.AGENT))));
        MCPToolRegistry.Resolved resolved = registry.resolve("x").orElseThrow();
        assertThat(resolved.tool().kind()).isEqualTo(MCPTool.Kind.AGENT);
        assertThat(resolved.metadata()).containsEntry("from", "AGENT");
        assertThat(resolved.start(mapper.createObjectNode())).isEqualTo("exec-x");
        assertThat(registry.resolve("unknown")).isEmpty();
    }

    @Test
    void mergesSources_sortedByName() {
        MCPToolRegistry registry =
                new MCPToolRegistry(
                        List.of(
                                source(
                                        tool("zeta", MCPTool.Kind.WORKFLOW),
                                        tool("alpha", MCPTool.Kind.WORKFLOW)),
                                source(tool("mid", MCPTool.Kind.AGENT))));
        assertThat(registry.listTools())
                .extracting(MCPTool::name)
                .containsExactly("alpha", "mid", "zeta");
    }

    @Test
    void nameClash_listsTheAgentOnce() {
        MCPToolRegistry registry =
                new MCPToolRegistry(
                        List.of(
                                source(tool("support_bot", MCPTool.Kind.WORKFLOW)),
                                source(tool("support_bot", MCPTool.Kind.AGENT))));
        assertThat(registry.listTools())
                .singleElement()
                .extracting(MCPTool::kind)
                .isEqualTo(MCPTool.Kind.AGENT);
        assertThat(registry.findTool("support_bot"))
                .get()
                .extracting(MCPTool::kind)
                .isEqualTo(MCPTool.Kind.AGENT);
    }

    @Test
    void agentWinsWhateverTheSourceOrder() {
        MCPToolRegistry registry =
                new MCPToolRegistry(
                        List.of(
                                source(tool("x", MCPTool.Kind.AGENT)),
                                source(tool("x", MCPTool.Kind.WORKFLOW))));
        assertThat(registry.findTool("x"))
                .get()
                .extracting(MCPTool::kind)
                .isEqualTo(MCPTool.Kind.AGENT);
    }

    @Test
    void unknownTool_isEmpty() {
        assertThat(new MCPToolRegistry(List.of()).findTool("nope")).isEmpty();
    }

    @Test
    void namesThatAreNotMcpToolNames_areNotOffered() {
        String tooLong = "a".repeat(129);
        MCPToolRegistry registry =
                new MCPToolRegistry(
                        List.of(
                                source(
                                        tool("nightly report", MCPTool.Kind.WORKFLOW),
                                        tool("x{1}", MCPTool.Kind.WORKFLOW),
                                        tool(tooLong, MCPTool.Kind.WORKFLOW),
                                        tool("orders.v2_lookup-1", MCPTool.Kind.WORKFLOW)),
                                source(tool("support bot", MCPTool.Kind.AGENT))));

        assertThat(registry.listTools())
                .extracting(MCPTool::name)
                .containsExactly("orders.v2_lookup-1");
        for (String name : List.of("nightly report", "x{1}", tooLong, "support bot")) {
            assertThat(registry.findTool(name)).as(name).isEmpty();
            assertThat(registry.exposes(name)).as(name).isFalse();
        }
        assertThat(registry.findTool("a".repeat(128))).isEmpty();
        assertThat(MCPToolRegistry.isToolName("a".repeat(128))).isTrue();
    }
}
