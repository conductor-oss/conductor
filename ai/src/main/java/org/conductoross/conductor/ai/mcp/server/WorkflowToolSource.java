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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.conductoross.conductor.ai.mcp.McpCallException;
import org.conductoross.conductor.ai.mcp.McpHeaderParams;
import org.conductoross.conductor.service.SchemaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.WorkflowClassifier;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.service.MetadataService;
import com.netflix.conductor.service.WorkflowService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Opted-in workflows as MCP tools: the latest version of each workflow that is in {@code
 * exposedWorkflows}, or has metadata {@code mcp.enabled}, or every workflow when {@code exposeAll}
 * is set. Definitions are read on every call, as the A2A server does.
 */
public class WorkflowToolSource implements McpToolSource {

    private static final Logger log = LoggerFactory.getLogger(WorkflowToolSource.class);

    /** Tool name and problem of each invalid annotation already warned about. */
    private final Set<String> warnedAnnotations = ConcurrentHashMap.newKeySet();

    static final String METADATA_ENABLED = "mcp.enabled";

    private final MetadataService metadataService;
    private final WorkflowService workflowService;
    private final SchemaService schemaService;
    private final McpServerProperties properties;
    private final ObjectMapper mapper;

    public WorkflowToolSource(
            MetadataService metadataService,
            WorkflowService workflowService,
            SchemaService schemaService,
            McpServerProperties properties,
            ObjectMapper mapper) {
        this.metadataService = metadataService;
        this.workflowService = workflowService;
        this.schemaService = schemaService;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public List<McpTool> listTools() {
        return metadataService.getWorkflowDefsLatestVersions().stream()
                .filter(this::isExposed)
                .map(this::toTool)
                .filter(this::hasValidHeaderAnnotations)
                .toList();
    }

    /** An opted-in workflow, also when its schema has become unusable for new calls. */
    @Override
    public boolean exposes(String name) {
        WorkflowDef def = latest(name);
        return def != null && isExposed(def);
    }

    @Override
    public Optional<McpTool> findTool(String name) {
        return Optional.ofNullable(latest(name))
                .filter(this::isExposed)
                .map(this::toTool)
                .filter(this::hasValidHeaderAnnotations);
    }

    /** The latest definition's metadata. */
    @Override
    public Map<String, Object> metadata(String name) {
        WorkflowDef def = latest(name);
        return def == null ? null : def.getMetadata();
    }

    /** Starts the latest version with the arguments as the workflow input. */
    @Override
    public String start(String name, ObjectNode arguments) {
        StartWorkflowRequest request = new StartWorkflowRequest();
        request.setName(name);
        @SuppressWarnings("unchecked")
        Map<String, Object> input = mapper.convertValue(arguments, Map.class);
        request.setInput(input);
        return workflowService.startWorkflow(request);
    }

    /**
     * An opted-in workflow. An agent definition is never selected here, whatever the opt-in: the
     * agent source exposes agents (started with a prompt), and an agent's definition carries
     * compiler-written text that names the kind.
     */
    boolean isExposed(WorkflowDef def) {
        if (WorkflowClassifier.AGENT.equalsIgnoreCase(WorkflowClassifier.classifierOf(def))) {
            return false;
        }
        if (properties.isExposeAll() || properties.getExposedWorkflows().contains(def.getName())) {
            return true;
        }
        Object flag = def.getMetadata() == null ? null : def.getMetadata().get(METADATA_ENABLED);
        return flag instanceof Boolean b
                ? b
                : flag != null && Boolean.parseBoolean(flag.toString());
    }

    private WorkflowDef latest(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return metadataService.getWorkflowDef(name, null);
        } catch (NotFoundException e) {
            // Unknown workflow. Any other error (a store outage) propagates, so a client polling a
            // running task gets a server error to retry, not "Unknown task".
            return null;
        }
    }

    /**
     * A tool whose input schema has an invalid {@code x-mcp-header} annotation (for example on a
     * {@code number} property, or a header name used twice) cannot be called correctly: conforming
     * clients drop it, and its calls could not be checked. It is left out, with a warning.
     */
    boolean hasValidHeaderAnnotations(McpTool tool) {
        try {
            McpHeaderParams.headersFor(tool.name(), tool.inputSchema(), Map.of());
            return true;
        } catch (McpCallException e) {
            // Checked on every lookup; warn once per tool and problem, not on every poll.
            if (warnedAnnotations.add(tool.name() + "\n" + e.getMessage())) {
                log.warn("MCP server: tool {} is not listed: {}", tool.name(), e.getMessage());
            } else {
                log.debug("MCP server: tool {} is not listed: {}", tool.name(), e.getMessage());
            }
            return false;
        }
    }

    /** The (tool, problem) pairs already warned about; for tests. */
    Set<String> warnedAnnotations() {
        return Set.copyOf(warnedAnnotations);
    }

    private McpTool toTool(WorkflowDef def) {
        String description =
                def.getDescription() != null && !def.getDescription().isBlank()
                        ? def.getDescription()
                        : McpTool.defaultDescription(def.getName());
        return new McpTool(def.getName(), description, inputSchema(def), McpTool.Kind.WORKFLOW);
    }

    /**
     * The JSON schema in {@code inputSchema} (inline, or the registered schema it names); else an
     * object with one property per {@code inputParameters} entry; else {@code {"type":"object"}}.
     */
    private JsonNode inputSchema(WorkflowDef def) {
        JsonNode registered = registeredSchema(def);
        if (registered != null) {
            return registered;
        }
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        List<String> parameters = def.getInputParameters();
        if (parameters != null && !parameters.isEmpty()) {
            ObjectNode properties = schema.putObject("properties");
            parameters.forEach(properties::putObject);
        }
        return schema;
    }

    /**
     * The schema the engine validates the input against: an inline schema (one with {@code data})
     * as it is, else the registered schema it names, as in {@code SchemaService}.
     */
    private JsonNode registeredSchema(WorkflowDef def) {
        SchemaDef ref = def.getInputSchema();
        if (ref == null) {
            return null;
        }
        try {
            SchemaDef schemaDef;
            if (ref.getData() != null) {
                schemaDef = ref;
            } else if (ref.getName() == null || ref.getName().isBlank()) {
                return null;
            } else {
                schemaDef =
                        ref.getVersion() > 0
                                ? schemaService.getSchemaByNameAndVersion(
                                        ref.getName(), ref.getVersion())
                                : schemaService.getSchemaByNameWithLatestVersion(ref.getName());
            }
            if (schemaDef == null
                    || schemaDef.getType() != SchemaDef.Type.JSON
                    || schemaDef.getData() == null) {
                return null;
            }
            JsonNode schema = mapper.valueToTree(schemaDef.getData());
            if (!(schema instanceof ObjectNode object)) {
                return null;
            }
            // An MCP inputSchema is an object schema. Add the type when the schema leaves it out;
            // a schema of any other type is not usable, so the inputParameters fallback applies.
            JsonNode type = object.get("type");
            if (type == null) {
                object.put("type", "object");
                return object;
            }
            return type.isString() && "object".equals(type.asString()) ? object : null;
        } catch (RuntimeException e) {
            log.debug(
                    "MCP server: schema {} for workflow {} not usable: {}",
                    ref.getName(),
                    def.getName(),
                    e.getMessage());
            return null;
        }
    }
}
