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
package org.conductoross.conductor.ai.agentspan.runtime.normalizer;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.ai.agentspan.runtime.model.AgentConfig;
import org.conductoross.conductor.ai.agentspan.runtime.model.ToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class SkillNormalizerTest {

    private SkillNormalizer normalizer;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        normalizer = new SkillNormalizer();
        mapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadFixture(String name) throws Exception {
        InputStream is = getClass().getResourceAsStream("/skills/" + name + ".json");
        return mapper.readValue(is, Map.class);
    }

    @Test
    void frameworkIdIsSkill() {
        assertEquals("skill", normalizer.frameworkId());
    }

    // --- Simple skill tests ---

    @Test
    void simpleSkillSetsNameFromFrontmatter() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("simple-skill"));
        assertEquals("simple-skill", config.getName());
    }

    @Test
    void simpleSkillSetsModelFromConfig() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("simple-skill"));
        assertEquals("openai/gpt-4o", config.getModel());
    }

    @Test
    void simpleSkillSetsInstructionsFromBody() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("simple-skill"));
        String instructions = (String) config.getInstructions();
        assertTrue(instructions.contains("# Simple Skill"));
        assertTrue(instructions.contains("You are a helpful assistant"));
    }

    @Test
    void simpleSkillHasNoTools() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("simple-skill"));
        // No sub-agents, no scripts, no resource files -> no tools
        assertTrue(config.getTools() == null || config.getTools().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void workspaceConfigCreatesWorkspaceTools() throws Exception {
        Map<String, Object> raw = loadFixture("simple-skill");
        raw.put(
                "workspace",
                Map.of(
                        "enabled",
                        true,
                        "roots",
                        List.of(
                                Map.of("name", "workspace", "kind", "workspace"),
                                Map.of("name", "docs", "kind", "filesystem"))));

        AgentConfig config = normalizer.normalize(raw);
        List<ToolConfig> tools = config.getTools();
        assertNotNull(tools);

        List<String> names = tools.stream().map(ToolConfig::getName).toList();
        assertTrue(names.contains("simple-skill__list_workspace_files"));
        assertTrue(names.contains("simple-skill__read_workspace_file"));
        assertTrue(names.contains("simple-skill__search_workspace"));
        assertTrue(names.contains("simple-skill__git_status"));
        assertTrue(names.contains("simple-skill__git_diff"));

        String instructions = (String) config.getInstructions();
        assertTrue(instructions.contains("Workspace Context"));
        assertTrue(instructions.contains("Available root names: workspace, docs"));

        ToolConfig readFile =
                tools.stream()
                        .filter(t -> "simple-skill__read_workspace_file".equals(t.getName()))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> schema = readFile.getInputSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> root = (Map<String, Object>) props.get("root");
        List<String> enumValues = (List<String>) root.get("enum");
        assertEquals(List.of("workspace", "docs"), enumValues);
    }

    @Test
    void workspaceConfigPropagatesToSubAgents() throws Exception {
        Map<String, Object> raw = loadFixture("dg-skill");
        raw.put(
                "workspace",
                Map.of(
                        "enabled",
                        true,
                        "roots",
                        List.of(Map.of("name", "workspace", "kind", "workspace"))));

        AgentConfig config = normalizer.normalize(raw);
        ToolConfig gilfoyleTool =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("gilfoyle"))
                        .findFirst()
                        .orElseThrow();
        AgentConfig gilfoyle = (AgentConfig) gilfoyleTool.getConfig().get("agentConfig");

        assertTrue(((String) gilfoyle.getInstructions()).contains("Workspace Context"));
        assertNotNull(gilfoyle.getTools());
        List<String> toolNames = gilfoyle.getTools().stream().map(ToolConfig::getName).toList();
        assertTrue(toolNames.contains("dg-skill__read_workspace_file"));
        assertTrue(toolNames.contains("dg-skill__git_diff"));
    }

    // --- DG skill tests (sub-agents + resources) ---

    @Test
    void dgSkillCreatesSubAgentTools() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        List<ToolConfig> tools = config.getTools();
        assertNotNull(tools);

        List<String> toolNames = tools.stream().map(ToolConfig::getName).toList();
        assertTrue(toolNames.contains("dg-skill__gilfoyle"));
        assertTrue(toolNames.contains("dg-skill__dinesh"));
    }

    @Test
    void dgSkillSubAgentToolsAreAgentToolType() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig gilfoyle =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("gilfoyle"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("agent_tool", gilfoyle.getToolType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void dgSkillSubAgentToolHasInputSchemaWithRequestField() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig gilfoyle =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("gilfoyle"))
                        .findFirst()
                        .orElseThrow();

        Map<String, Object> schema = gilfoyle.getInputSchema();
        assertNotNull(schema, "agent_tool should have an inputSchema");
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props, "inputSchema should have properties");
        assertTrue(props.containsKey("request"), "inputSchema properties should include 'request'");

        Map<String, Object> requestProp = (Map<String, Object>) props.get("request");
        assertEquals("string", requestProp.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required, "inputSchema should have required list");
        assertTrue(required.contains("request"), "request should be required");
    }

    @SuppressWarnings("unchecked")
    @Test
    void dgSkillAllSubAgentToolsHaveInputSchema() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        List<ToolConfig> agentTools =
                config.getTools().stream()
                        .filter(t -> "agent_tool".equals(t.getToolType()))
                        .toList();

        assertFalse(agentTools.isEmpty(), "Should have agent_tool entries");
        for (ToolConfig tool : agentTools) {
            Map<String, Object> schema = tool.getInputSchema();
            assertNotNull(schema, "agent_tool '" + tool.getName() + "' should have inputSchema");
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertNotNull(
                    props,
                    "agent_tool '" + tool.getName() + "' inputSchema should have properties");
            assertTrue(
                    props.containsKey("request"),
                    "agent_tool '" + tool.getName() + "' should have 'request' in properties");
        }
    }

    @Test
    void dgSkillSubAgentHasInstructions() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig gilfoyle =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("gilfoyle"))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> toolConfig = gilfoyle.getConfig();
        assertNotNull(toolConfig);
        AgentConfig subAgent = (AgentConfig) toolConfig.get("agentConfig");
        String instructions = (String) subAgent.getInstructions();
        assertTrue(instructions.contains("You Are Gilfoyle"));
    }

    @Test
    void dgSkillSubAgentInheritsModel() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig dinesh =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("dinesh"))
                        .findFirst()
                        .orElseThrow();
        AgentConfig subAgent = (AgentConfig) dinesh.getConfig().get("agentConfig");
        // dinesh not in agentModels override -> inherits parent model
        assertEquals("anthropic/claude-sonnet-4-6", subAgent.getModel());
    }

    @Test
    void dgSkillSubAgentModelOverride() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig gilfoyle =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("gilfoyle"))
                        .findFirst()
                        .orElseThrow();
        AgentConfig subAgent = (AgentConfig) gilfoyle.getConfig().get("agentConfig");
        // gilfoyle has override in agentModels
        assertEquals("openai/gpt-4o", subAgent.getModel());
    }

    @Test
    void dgSkillCreatesReadSkillFileTool() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig readFile =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("read_skill_file"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("worker", readFile.getToolType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void dgSkillReadSkillFileConstrainsEnum() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("dg-skill"));
        ToolConfig readFile =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("read_skill_file"))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> schema = readFile.getInputSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> pathProp = (Map<String, Object>) props.get("path");
        List<String> enumValues = (List<String>) pathProp.get("enum");
        assertTrue(enumValues.contains("comic-template.html"));
    }

    // --- Conductor skill tests (scripts + resources) ---

    @Test
    void conductorSkillCreatesScriptTool() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("conductor-skill"));
        ToolConfig scriptTool =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("conductor_api"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("worker", scriptTool.getToolType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void conductorSkillReadFileHasMultipleResources() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("conductor-skill"));
        ToolConfig readFile =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("read_skill_file"))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> schema = readFile.getInputSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> pathProp = (Map<String, Object>) props.get("path");
        List<String> enumValues = (List<String>) pathProp.get("enum");
        assertEquals(2, enumValues.size());
        assertTrue(enumValues.contains("references/api-reference.md"));
        assertTrue(enumValues.contains("references/workflow-definition.md"));
    }

    // --- Large skill auto-splitting tests ---

    @Test
    void largeSkillInstructionsContainTableOfContents() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        String instructions = (String) config.getInstructions();
        // Instructions should NOT contain the full body — should have a TOC instead
        assertTrue(
                instructions.contains("Available sections (use read_skill_file to load)"),
                "Instructions should contain table of contents");
        assertTrue(
                instructions.contains("skill_section:workflow-definitions"),
                "TOC should list workflow-definitions section");
        assertTrue(
                instructions.contains("skill_section:running-workflows"),
                "TOC should list running-workflows section");
    }

    @Test
    void largeSkillInstructionsContainPreamble() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        String instructions = (String) config.getInstructions();
        // Preamble content (before first ## heading) should be preserved
        assertTrue(
                instructions.contains("You are the Conductor orchestrator"),
                "Instructions should contain preamble content");
        assertTrue(
                instructions.contains("Always validate inputs before processing"),
                "Instructions should contain preamble rules");
    }

    @Test
    void largeSkillInstructionsDoNotContainFullBody() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        String instructions = (String) config.getInstructions();
        // The full body has ~180K chars; instructions should be much shorter
        assertTrue(
                instructions.length() < 50000,
                "Instructions should be shorter than threshold after splitting, got: "
                        + instructions.length());
    }

    @SuppressWarnings("unchecked")
    @Test
    void largeSkillReadFileEnumIncludesSections() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        ToolConfig readFile =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("read_skill_file"))
                        .findFirst()
                        .orElseThrow();
        Map<String, Object> schema = readFile.getInputSchema();
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        Map<String, Object> pathProp = (Map<String, Object>) props.get("path");
        List<String> enumValues = (List<String>) pathProp.get("enum");

        // Should include virtual section entries
        assertTrue(
                enumValues.contains("skill_section:workflow-definitions"),
                "Enum should include workflow-definitions section");
        assertTrue(
                enumValues.contains("skill_section:running-workflows"),
                "Enum should include running-workflows section");
        assertTrue(
                enumValues.contains("skill_section:error-handling"),
                "Enum should include error-handling section");

        // Should ALSO include real resource files
        assertTrue(
                enumValues.contains("references/api-reference.md"),
                "Enum should still include real resource files");
    }

    @Test
    void largeSkillHasCorrectNumberOfSections() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        ToolConfig readFile =
                config.getTools().stream()
                        .filter(t -> t.getName().contains("read_skill_file"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = readFile.getInputSchema();
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> pathProp = (Map<String, Object>) props.get("path");
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) pathProp.get("enum");

        long sectionCount = enumValues.stream().filter(e -> e.startsWith("skill_section:")).count();
        assertEquals(8, sectionCount, "Should have 8 sections from the large skill");
    }

    @Test
    void smallSkillIsNotSplit() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("simple-skill"));
        String instructions = (String) config.getInstructions();
        // Small skill should keep full body as instructions
        assertTrue(
                instructions.contains("You are a helpful assistant"),
                "Small skill should retain full body");
        assertFalse(
                instructions.contains("Available sections"),
                "Small skill should NOT have a table of contents");
    }

    @Test
    void conductorSkillIsNotSplit() throws Exception {
        // The conductor-skill fixture has a small body — should not be split
        AgentConfig config = normalizer.normalize(loadFixture("conductor-skill"));
        String instructions = (String) config.getInstructions();
        assertFalse(
                instructions.contains("Available sections"),
                "Small conductor-skill fixture should NOT be split");
    }

    // --- Cross-skill reference tests ---

    @Test
    void crossSkillRefCreatesAgentTool() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("crossref-skill"));
        List<ToolConfig> tools = config.getTools();
        assertNotNull(tools);

        ToolConfig refTool =
                tools.stream()
                        .filter(t -> "writing-plans".equals(t.getName()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("agent_tool", refTool.getToolType());
    }

    @SuppressWarnings("unchecked")
    @Test
    void crossSkillRefToolHasInputSchemaWithRequestField() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("crossref-skill"));
        ToolConfig refTool =
                config.getTools().stream()
                        .filter(t -> "writing-plans".equals(t.getName()))
                        .findFirst()
                        .orElseThrow();

        Map<String, Object> schema = refTool.getInputSchema();
        assertNotNull(schema, "cross-skill agent_tool should have an inputSchema");
        assertEquals("object", schema.get("type"));

        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props, "inputSchema should have properties");
        assertTrue(props.containsKey("request"), "inputSchema properties should include 'request'");

        Map<String, Object> requestProp = (Map<String, Object>) props.get("request");
        assertEquals("string", requestProp.get("type"));

        List<String> required = (List<String>) schema.get("required");
        assertNotNull(required, "inputSchema should have required list");
        assertTrue(required.contains("request"), "request should be required");
    }

    @Test
    void largeSkillDescriptionInInstructions() throws Exception {
        AgentConfig config = normalizer.normalize(loadFixture("large-skill"));
        String instructions = (String) config.getInstructions();
        assertTrue(
                instructions.contains("A large skill for testing section splitting"),
                "Instructions should contain the skill description");
    }
}
