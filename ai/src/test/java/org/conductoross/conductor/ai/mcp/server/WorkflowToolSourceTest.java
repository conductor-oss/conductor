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

import org.conductoross.conductor.service.SchemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowService;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowToolSourceTest {

    private MetadataService metadataService;
    private WorkflowService workflowService = mock(WorkflowService.class);
    private SchemaService schemaService;
    private McpServerProperties properties;
    private WorkflowToolSource source;

    @BeforeEach
    void setUp() {
        metadataService = mock(MetadataService.class);
        schemaService = mock(SchemaService.class);
        properties = new McpServerProperties();
        source =
                new WorkflowToolSource(
                        metadataService,
                        workflowService,
                        schemaService,
                        properties,
                        new ObjectMapperProvider().getObjectMapper());
    }

    private static WorkflowDef def(String name, Map<String, Object> metadata) {
        WorkflowDef def = new WorkflowDef();
        def.setName(name);
        def.setVersion(1);
        if (metadata != null) {
            def.setMetadata(metadata);
        }
        return def;
    }

    private void latest(WorkflowDef... defs) {
        when(metadataService.getWorkflowDefsLatestVersions()).thenReturn(List.of(defs));
    }

    @Test
    void workflowOptedInByMetadata_isListed() {
        latest(def("order_lookup", Map.of("mcp.enabled", true)));
        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("order_lookup");
    }

    @Test
    void metadataFlagAsString_isAccepted() {
        latest(def("order_lookup", Map.of("mcp.enabled", "true")));
        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("order_lookup");
    }

    @Test
    void workflowNotOptedIn_isNotListed() {
        latest(def("internal_cleanup", null), def("off", Map.of("mcp.enabled", false)));
        assertThat(source.listTools()).isEmpty();
    }

    @Test
    void configListAndExposeAll() {
        latest(def("a", null), def("b", null));
        properties.setExposedWorkflows(List.of("b"));
        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("b");
        properties.setExposeAll(true);
        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("a", "b");
    }

    @Test
    void agentDefinitions_areNeverWorkflowTools_whateverTheOptIn() {
        WorkflowDef compiled =
                def("support_bot", Map.of("agent_sdk", "conductor", "mcp.enabled", true));
        compiled.setDescription("Agent workflow for support_bot");
        WorkflowDef tagged = def("tagged_bot", Map.of("classifier", "agent"));
        WorkflowDef plain = def("order_lookup", null);
        latest(compiled, tagged, plain);
        properties.setExposeAll(true);
        properties.setExposedWorkflows(List.of("support_bot", "tagged_bot"));

        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("order_lookup");
        when(metadataService.getWorkflowDef("support_bot", null)).thenReturn(compiled);
        assertThat(source.findTool("support_bot")).isEmpty();
        // Nor are their executions readable through this source.
        assertThat(source.exposes("support_bot")).isFalse();
    }

    @Test
    void toolsAreWorkflowKind_withDefaultDescription() {
        latest(def("order_lookup", Map.of("mcp.enabled", true)));
        McpTool tool = source.listTools().get(0);
        assertThat(tool.kind()).isEqualTo(McpTool.Kind.WORKFLOW);
        assertThat(tool.description()).isEqualTo("Conductor tool 'order_lookup'");
    }

    @Test
    void definitionDescription_isUsed() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        def.setDescription("Look up an order");
        latest(def);
        assertThat(source.listTools().get(0).description()).isEqualTo("Look up an order");
    }

    private WorkflowDef withSchema(String name, Map<String, Object> properties) {
        WorkflowDef def = def(name, Map.of("mcp.enabled", true));
        SchemaDef ref = new SchemaDef();
        ref.setName(name + "_schema");
        ref.setVersion(1);
        def.setInputSchema(ref);
        SchemaDef stored = new SchemaDef();
        stored.setName(name + "_schema");
        stored.setVersion(1);
        stored.setType(SchemaDef.Type.JSON);
        stored.setData(Map.of("type", "object", "properties", properties));
        when(schemaService.getSchemaByNameAndVersion(name + "_schema", 1)).thenReturn(stored);
        when(metadataService.getWorkflowDef(name, null)).thenReturn(def);
        return def;
    }

    @Test
    void invalidHeaderAnnotation_leavesTheToolOut_validOneIsKept() {
        WorkflowDef numberHeader =
                withSchema(
                        "bad_type",
                        Map.of("amount", Map.of("type", "number", "x-mcp-header", "Amount")));
        WorkflowDef twice =
                withSchema(
                        "bad_twice",
                        Map.of(
                                "a", Map.of("type", "string", "x-mcp-header", "Region"),
                                "b", Map.of("type", "string", "x-mcp-header", "region")));
        WorkflowDef good =
                withSchema(
                        "good",
                        Map.of("region", Map.of("type", "string", "x-mcp-header", "Region")));
        latest(numberHeader, twice, good);

        assertThat(source.listTools()).extracting(McpTool::name).containsExactly("good");
        assertThat(source.findTool("bad_type")).isEmpty();
        assertThat(source.findTool("bad_twice")).isEmpty();
        assertThat(source.findTool("good")).isPresent();
        // Running executions of a tool that is not offered stay readable and cancellable.
        assertThat(source.exposes("bad_type")).isTrue();
        assertThat(source.exposes("bad_twice")).isTrue();
    }

    @Test
    void invalidAnnotation_isWarnedOncePerToolAndProblem() {
        withSchema(
                "bad_type", Map.of("amount", Map.of("type", "number", "x-mcp-header", "Amount")));
        source.findTool("bad_type");
        source.findTool("bad_type");
        source.exposes("bad_type");
        assertThat(source.warnedAnnotations()).hasSize(1);
        assertThat(source.warnedAnnotations().iterator().next()).startsWith("bad_type\n");

        // A different problem with the same tool is reported again.
        withSchema(
                "bad_type", Map.of("amount", Map.of("type", "object", "x-mcp-header", "Amount")));
        source.findTool("bad_type");
        assertThat(source.warnedAnnotations()).hasSize(2);
    }

    @Test
    void storeErrorIsNotUnknown_soAPollGetsAServerErrorToRetry() {
        when(metadataService.getWorkflowDef("order_lookup", null))
                .thenThrow(new IllegalStateException("store unavailable"));
        assertThatThrownBy(() -> source.exposes("order_lookup"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> source.findTool("order_lookup"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registeredJsonSchema_isTheInputSchema() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        SchemaDef ref = new SchemaDef();
        ref.setName("OrderLookup");
        ref.setVersion(2);
        def.setInputSchema(ref);
        latest(def);
        SchemaDef stored = new SchemaDef();
        stored.setName("OrderLookup");
        stored.setVersion(2);
        stored.setType(SchemaDef.Type.JSON);
        stored.setData(
                Map.of(
                        "type",
                        "object",
                        "properties",
                        Map.of("orderId", Map.of("type", "string")),
                        "required",
                        List.of("orderId")));
        when(schemaService.getSchemaByNameAndVersion("OrderLookup", 2)).thenReturn(stored);

        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("required").get(0).asString()).isEqualTo("orderId");
        assertThat(schema.get("properties").get("orderId").get("type").asString())
                .isEqualTo("string");
    }

    @Test
    void registeredSchemaWithoutAType_getsTypeObject_otherTypesAreSkipped() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        def.setInputParameters(List.of("orderId"));
        SchemaDef ref = new SchemaDef();
        ref.setName("OrderLookup");
        ref.setVersion(2);
        def.setInputSchema(ref);
        latest(def);
        SchemaDef stored = new SchemaDef();
        stored.setName("OrderLookup");
        stored.setVersion(2);
        stored.setType(SchemaDef.Type.JSON);
        stored.setData(Map.of("properties", Map.of("orderId", Map.of("type", "string"))));
        when(schemaService.getSchemaByNameAndVersion("OrderLookup", 2)).thenReturn(stored);

        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").has("orderId")).isTrue();

        // A schema for something other than an object is not an MCP inputSchema: the
        // inputParameters fallback applies.
        stored.setData(Map.of("type", "array", "items", Map.of("type", "string")));
        JsonNode fallback = source.listTools().get(0).inputSchema();
        assertThat(fallback.get("type").asString()).isEqualTo("object");
        assertThat(fallback.get("properties").has("orderId")).isTrue();
        assertThat(fallback.has("items")).isFalse();
    }

    @Test
    void inlineSchema_isTheToolSchema_withoutARegistryLookup() {
        WorkflowDef def = def("customer_lookup", Map.of("mcp.enabled", true));
        SchemaDef inline = new SchemaDef();
        inline.setName("customerInput");
        inline.setType(SchemaDef.Type.JSON);
        inline.setData(
                Map.of(
                        "properties",
                        Map.of("customerId", Map.of("type", "string")),
                        "required",
                        List.of("customerId")));
        def.setInputSchema(inline);
        latest(def);
        // A different registered schema with the same name: the inline one still wins, as in the
        // engine's validation.
        SchemaDef stored = new SchemaDef();
        stored.setType(SchemaDef.Type.JSON);
        stored.setData(Map.of("type", "object", "properties", Map.of("other", Map.of())));
        when(schemaService.getSchemaByNameWithLatestVersion("customerInput")).thenReturn(stored);

        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").has("customerId")).isTrue();
        assertThat(schema.get("properties").has("other")).isFalse();
        assertThat(schema.get("required").get(0).asString()).isEqualTo("customerId");
        verify(schemaService, never()).getSchemaByNameWithLatestVersion(any());
    }

    @Test
    void schemaReferenceWithoutVersion_usesLatest() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        SchemaDef ref = new SchemaDef();
        ref.setName("OrderLookup");
        def.setInputSchema(ref);
        latest(def);
        SchemaDef stored = new SchemaDef();
        stored.setType(SchemaDef.Type.JSON);
        stored.setData(Map.of("type", "object"));
        when(schemaService.getSchemaByNameWithLatestVersion("OrderLookup")).thenReturn(stored);
        assertThat(source.listTools().get(0).inputSchema().get("type").asString())
                .isEqualTo("object");
    }

    @Test
    void inputParametersOnly_giveAnObjectWithThoseProperties() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        def.setInputParameters(List.of("orderId", "region"));
        latest(def);
        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.get("type").asString()).isEqualTo("object");
        assertThat(schema.get("properties").has("orderId")).isTrue();
        assertThat(schema.get("properties").has("region")).isTrue();
        assertThat(schema.has("required")).isFalse();
    }

    @Test
    void noSchemaAndNoParameters_giveAnEmptyObjectSchema() {
        latest(def("order_lookup", Map.of("mcp.enabled", true)));
        JsonNode schema = source.listTools().get(0).inputSchema();
        assertThat(schema.size()).isEqualTo(1);
        assertThat(schema.get("type").asString()).isEqualTo("object");
    }

    @Test
    void unresolvableOrNonJsonSchema_fallsBackToInputParameters() {
        WorkflowDef def = def("order_lookup", Map.of("mcp.enabled", true));
        SchemaDef ref = new SchemaDef();
        ref.setName("Missing");
        ref.setVersion(1);
        def.setInputSchema(ref);
        def.setInputParameters(List.of("orderId"));
        latest(def);
        when(schemaService.getSchemaByNameAndVersion("Missing", 1)).thenReturn(null);
        assertThat(source.listTools().get(0).inputSchema().get("properties").has("orderId"))
                .isTrue();

        SchemaDef avro = new SchemaDef();
        avro.setType(SchemaDef.Type.AVRO);
        avro.setData(Map.of("type", "record"));
        when(schemaService.getSchemaByNameAndVersion("Missing", 1)).thenReturn(avro);
        assertThat(source.listTools().get(0).inputSchema().get("type").asString())
                .isEqualTo("object");
    }

    @Test
    void findTool_onlyFindsExposedWorkflows() {
        when(metadataService.getWorkflowDef("order_lookup", null))
                .thenReturn(def("order_lookup", Map.of("mcp.enabled", true)));
        when(metadataService.getWorkflowDef("hidden", null)).thenReturn(def("hidden", null));
        when(metadataService.getWorkflowDef("absent", null))
                .thenThrow(new com.netflix.conductor.core.exception.NotFoundException("absent"));
        assertThat(source.findTool("order_lookup")).isPresent();
        assertThat(source.findTool("hidden")).isEmpty();
        assertThat(source.findTool("absent")).isEmpty();
        assertThat(source.exposes("order_lookup")).isTrue();
        assertThat(source.exposes("hidden")).isFalse();
        assertThat(source.exposes("absent")).isFalse();
    }

    @Test
    void start_runsTheLatestVersionWithTheArgumentsAsInput() {
        when(workflowService.startWorkflow(any(StartWorkflowRequest.class))).thenReturn("exec-7");
        tools.jackson.databind.node.ObjectNode arguments =
                new ObjectMapperProvider()
                        .getObjectMapper()
                        .createObjectNode()
                        .put("orderId", "o-1");

        assertThat(source.start("order_lookup", arguments)).isEqualTo("exec-7");

        ArgumentCaptor<StartWorkflowRequest> request =
                ArgumentCaptor.forClass(StartWorkflowRequest.class);
        verify(workflowService).startWorkflow(request.capture());
        assertThat(request.getValue().getName()).isEqualTo("order_lookup");
        assertThat(request.getValue().getVersion()).isNull();
        assertThat(request.getValue().getInput()).containsEntry("orderId", "o-1");
    }

    @Test
    void metadataIsTheLatestDefinitions_deletedIsTheDefault_storeErrorPropagates() {
        WorkflowDef def = new WorkflowDef();
        def.setName("slow");
        def.setMetadata(Map.of("mcp.syncWaitSeconds", 0));
        when(metadataService.getWorkflowDef("slow", null)).thenReturn(def);
        when(metadataService.getWorkflowDef("gone", null)).thenThrow(new NotFoundException("gone"));
        when(metadataService.getWorkflowDef("broken", null))
                .thenThrow(new IllegalStateException("store unavailable"));

        assertThat(source.metadata("slow")).containsEntry("mcp.syncWaitSeconds", 0);
        assertThat(source.metadata("gone")).isNull();
        assertThatThrownBy(() -> source.metadata("broken"))
                .isInstanceOf(IllegalStateException.class);
    }
}
