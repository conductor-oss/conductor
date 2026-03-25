/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.tasks.webhook;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;

/**
 * Computes routing hashes for webhook event matching.
 *
 * <p>There are two hash computation modes:
 *
 * <ul>
 *   <li><b>Task registration hash</b> ({@link #computeTaskRegistrationHash}) — computed at task
 *       start from the task's already-resolved {@code matches} input. Stored in {@link
 *       WebhookTaskDAO} as the lookup key.
 *   <li><b>Inbound event hash</b> ({@link #computeInboundHash}) — computed when an inbound webhook
 *       arrives by extracting values from the payload at the paths declared in the stored workflow
 *       definition matchers. If the extracted values match, the hashes are equal and the waiting
 *       task is found.
 * </ul>
 *
 * <p>Hash format: {@code workflowName;version;taskRefName;value1;value2;...} where values are
 * appended in sorted-path order (TreeSet on the JSONPath keys). Ported from Orkes Enterprise; no
 * org-specific concerns.
 */
@Service
public class WebhookHashingService {

    static final String DELIMITER = ";";

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookHashingService.class);

    private final ObjectMapper objectMapper;

    public WebhookHashingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Computes the hash stored in {@link WebhookTaskDAO} when a {@code WAIT_FOR_WEBHOOK} task
     * starts. Uses the task's already-resolved {@code matches} input (variables substituted by the
     * workflow engine before task start).
     *
     * @param workflowName the workflow definition name
     * @param workflowVersion the workflow definition version
     * @param taskRefName the task reference name (DO_WHILE iteration suffix stripped)
     * @param resolvedMatches the fully-resolved {@code matches} map from task input
     * @return the registration hash
     */
    public static String computeTaskRegistrationHash(
            String workflowName,
            int workflowVersion,
            String taskRefName,
            Map<String, Object> resolvedMatches) {
        String cleanRef = stripIterationSuffix(taskRefName);
        TreeSet<String> sortedPaths = new TreeSet<>(resolvedMatches.keySet());
        StringBuilder hash =
                new StringBuilder(
                        workflowName + DELIMITER + workflowVersion + DELIMITER + cleanRef);
        for (String path : sortedPaths) {
            hash.append(DELIMITER).append(resolvedMatches.get(path));
        }
        return hash.toString();
    }

    /**
     * Computes a hash from an inbound webhook payload against a set of match criteria. Used at
     * event-processing time to find waiting tasks.
     *
     * <p>The {@code baseKey} is the {@code workflowName;version;taskRefName} prefix stored as the
     * matcher key in {@link WebhookConfigDAO}. The method appends extracted values (in sorted-path
     * order) to produce the full hash.
     *
     * <p>A criterion with an expected value starting with {@code "$"} is treated as a wildcard —
     * any non-null extracted value is accepted and appended to the hash. A literal expected value
     * must match the extracted value exactly (case-insensitive trim comparison).
     *
     * @param baseKey the hash prefix ({@code workflowName;version;taskRefName})
     * @param matchCriteria map of JSONPath expression to expected value
     * @param body the inbound request body (JSON string)
     * @param requestParams query parameters merged into the JSON document
     * @return the computed hash, or {@code null} if the payload does not match all criteria
     */
    public String computeInboundHash(
            String baseKey,
            Map<String, Object> matchCriteria,
            String body,
            Map<String, Object> requestParams) {
        Map<String, Object> document = parseBody(body);
        if (requestParams != null) {
            document.putAll(requestParams);
        }
        DocumentContext ctx = JsonPath.parse(document);

        TreeSet<String> sortedPaths = new TreeSet<>(matchCriteria.keySet());
        StringBuilder hash = new StringBuilder(baseKey);

        for (String path : sortedPaths) {
            Object extracted = extract(ctx, path);
            if (extracted == null) {
                return null;
            }
            if (extracted instanceof JSONArray array) {
                if (array.isEmpty()) return null;
                extracted = array.get(0).toString();
            }

            String expectedValue = Objects.toString(matchCriteria.get(path));
            String extractedStr = extracted.toString();

            // "$" prefix = wildcard (any extracted value is accepted)
            if (expectedValue.startsWith("$")
                    || expectedValue.trim().equalsIgnoreCase(extractedStr.trim())) {
                hash.append(DELIMITER).append(extractedStr);
            } else {
                return null; // mismatch — payload does not match this criterion
            }
        }

        return hash.toString();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseBody(String body) {
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map) {
                return new HashMap<>((Map<String, Object>) parsed);
            }
            Map<String, Object> wrapper = new HashMap<>();
            wrapper.put("request", parsed);
            return wrapper;
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private Object extract(DocumentContext ctx, String path) {
        try {
            return ctx.read(path);
        } catch (Exception e) {
            LOGGER.warn("Failed to read JSONPath '{}': {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * Strips DO_WHILE iteration suffixes from task reference names (e.g. {@code "myTask__1"} →
     * {@code "myTask"}). Only strips a numeric suffix; a non-numeric {@code "__"} segment is left
     * intact. This ensures the matcher key stored on the webhook config matches the task ref name
     * used at registration time regardless of which iteration the task is in.
     */
    static String stripIterationSuffix(String taskRefName) {
        int idx = taskRefName.lastIndexOf("__");
        if (idx > 0) {
            String suffix = taskRefName.substring(idx + 2);
            if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                return taskRefName.substring(0, idx);
            }
        }
        return taskRefName;
    }
}
