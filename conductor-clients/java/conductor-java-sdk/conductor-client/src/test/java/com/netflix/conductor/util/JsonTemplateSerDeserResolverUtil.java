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
package com.netflix.conductor.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for resolving JSON templates from a predefined resource file.
 */
public class JsonTemplateSerDeserResolverUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TEMPLATE_RESOURCE_PATH = "ser_deser_json_string.json";
    private static JsonNode templatesRoot;

    // Initialize the templates root node
    static {
        try {
            loadTemplates();
        } catch (IOException e) {
            System.err.println("Failed to load templates: " + e.getMessage());
        }
    }

    /**
     * Loads the templates from the predefined resource file.
     */
    private static void loadTemplates() throws IOException {
        try (InputStream is = JsonTemplateSerDeserResolverUtil.class.getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE_PATH)) {
            if (is == null) {
                throw new IOException("Resource not found: " + TEMPLATE_RESOURCE_PATH);
            }

            JsonNode root = objectMapper.readTree(is);
            templatesRoot = root.get("templates");

            if (templatesRoot == null) {
                throw new IllegalArgumentException("JSON template does not contain 'templates' root element");
            }
        }
    }

    /**
     * Gets the JSON string for a specified template.
     *
     * @param templateName The name of the template to resolve
     * @return The resolved template as a JSON string
     * @throws IOException If there's an error processing the JSON
     */
    public static String getJsonString(String templateName) throws IOException {
        if (templatesRoot == null) {
            loadTemplates();
        }

        // Get the template with inheritance handling
        JsonNode resolvedNode = resolveTemplateWithInheritance(templateName, new HashSet<>());

        // Resolve references in the node
        resolveReferences(resolvedNode, new HashSet<>());

        // Convert to string and return
        return objectMapper.writeValueAsString(resolvedNode);
    }

    /**
     * Resolves a template including all inherited fields from parent templates.
     *
     * @param templateName The name of the template to resolve
     * @param processedTemplates Set of already processed templates to avoid circular inheritance
     * @return The resolved template content node with all inherited fields
     * @throws IOException If there's an error processing the JSON
     */
    private static JsonNode resolveTemplateWithInheritance(String templateName, Set<String> processedTemplates) throws IOException {
        if (processedTemplates.contains(templateName)) {
            System.out.println("Warning: Circular inheritance detected for " + templateName);
            return objectMapper.createObjectNode();
        }

        processedTemplates.add(templateName);

        JsonNode template = templatesRoot.get(templateName);
        if (template == null) {
            throw new IllegalArgumentException("Template '" + templateName + "' not found");
        }

        JsonNode contentNode = template.get("content");
        if (contentNode == null) {
            throw new IllegalArgumentException("Template '" + templateName + "' does not contain 'content' node");
        }

        // Create a deep copy of the content node
        ObjectNode resultNode = objectMapper.readTree(contentNode.toString()).deepCopy();

        // Process inheritance if present
        JsonNode inheritsNode = template.get("inherits");
        if (inheritsNode != null && inheritsNode.isArray()) {
            for (JsonNode parentNameNode : inheritsNode) {
                String parentName = parentNameNode.asText();

                // Resolve parent template
                JsonNode parentNode = resolveTemplateWithInheritance(parentName, new HashSet<>(processedTemplates));

                // Merge parent fields into result (parent fields will be overridden by child fields)
                mergeNodes(resultNode, parentNode);
            }
        }

        return resultNode;
    }

    /**
     * Merges fields from the source node into the target node.
     * Fields in the target node are not overwritten if they already exist.
     */
    private static void mergeNodes(ObjectNode target, JsonNode source) {
        if (source.isObject()) {
            Iterator<String> fieldNames = source.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode sourceValue = source.get(fieldName);

                // Only add the field if it doesn't exist in the target
                if (!target.has(fieldName)) {
                    if (sourceValue.isObject() && target.has(fieldName) && target.get(fieldName).isObject()) {
                        // Recursively merge objects
                        mergeNodes((ObjectNode) target.get(fieldName), sourceValue);
                    } else {
                        // Add the field
                        target.set(fieldName, sourceValue.deepCopy());
                    }
                }
            }
        }
    }

    /**
     * Resolves references in a JSON node.
     */
    private static void resolveReferences(JsonNode node, Set<String> processedDependencies) throws IOException {
        if (node.isObject()) {
            resolveObjectReferences((ObjectNode) node, processedDependencies);
        } else if (node.isArray()) {
            resolveArrayReferences((ArrayNode) node, processedDependencies);
        }
    }

    /**
     * Resolves references in an object node.
     */
    private static void resolveObjectReferences(ObjectNode objectNode, Set<String> processedDependencies) throws IOException {
        Iterator<String> fieldNames = objectNode.fieldNames();
        Set<String> fieldsToProcess = new HashSet<>();

        // Collect field names to avoid ConcurrentModificationException
        while (fieldNames.hasNext()) {
            fieldsToProcess.add(fieldNames.next());
        }

        for (String fieldName : fieldsToProcess) {
            JsonNode fieldValue = objectNode.get(fieldName);

            // Check if the field value is a string reference
            if (fieldValue.isTextual()) {
                String textValue = fieldValue.asText();
                if (isReference(textValue)) {
                    String referenceName = extractReferenceName(textValue);

                    // Use a clone of the processed dependencies for each field
                    Set<String> fieldDependencies = new HashSet<>(processedDependencies);

                    if (fieldDependencies.contains(referenceName)) {
                        // Circular reference detected
                        System.out.println("Warning: Circular reference detected for " + referenceName);
                        continue;
                    }

                    fieldDependencies.add(referenceName);

                    JsonNode referencedTemplate = templatesRoot.get(referenceName);
                    if (referencedTemplate != null) {
                        JsonNode referencedContent = referencedTemplate.get("content");
                        if (referencedContent != null) {
                            try {
                                JsonNode resolvedReference = objectMapper.readTree(referencedContent.toString());
                                // Resolve references with the updated dependencies
                                resolveReferences(resolvedReference, fieldDependencies);
                                objectNode.set(fieldName, resolvedReference);
                            } catch (IOException e) {
                                System.out.println("Warning: Failed to resolve reference " + referenceName + ": " + e.getMessage());
                            }
                        }
                    } else {
                        System.out.println("Warning: Referenced template not found: " + referenceName);
                    }
                }
            } else if (fieldValue.isObject() || fieldValue.isArray()) {
                // Use a clone of processed dependencies for nested structures
                resolveReferences(fieldValue, new HashSet<>(processedDependencies));
            }
        }
    }

    /**
     * Resolves references in an array node.
     */
    private static void resolveArrayReferences(ArrayNode arrayNode, Set<String> processedDependencies) throws IOException {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode element = arrayNode.get(i);

            if (element.isTextual()) {
                String textValue = element.asText();
                if (isReference(textValue)) {
                    String referenceName = extractReferenceName(textValue);

                    // Clone the dependencies for each array element
                    Set<String> elementDependencies = new HashSet<>(processedDependencies);

                    if (elementDependencies.contains(referenceName)) {
                        // Circular reference detected
                        System.out.println("Warning: Circular reference detected for " + referenceName);
                        continue;
                    }

                    elementDependencies.add(referenceName);

                    JsonNode referencedTemplate = templatesRoot.get(referenceName);
                    if (referencedTemplate != null) {
                        JsonNode referencedContent = referencedTemplate.get("content");
                        if (referencedContent != null) {
                            try {
                                JsonNode resolvedReference = objectMapper.readTree(referencedContent.toString());
                                // Resolve any references in the referenced template
                                resolveReferences(resolvedReference, elementDependencies);
                                arrayNode.set(i, resolvedReference);
                            } catch (IOException e) {
                                System.out.println("Warning: Failed to resolve reference " + referenceName + ": " + e.getMessage());
                            }
                        }
                    } else {
                        System.out.println("Warning: Referenced template not found: " + referenceName);
                    }
                }
            } else if (element.isObject() || element.isArray()) {
                // Recursively process nested objects and arrays
                resolveReferences(element, new HashSet<>(processedDependencies));
            }
        }
    }

    /**
     * Checks if a string value is a template reference.
     */
    private static boolean isReference(String value) {
        return value.startsWith("${") && value.endsWith("}");
    }

    /**
     * Extracts the reference name from a reference string.
     */
    private static String extractReferenceName(String reference) {
        return reference.substring(2, reference.length() - 1);
    }
}