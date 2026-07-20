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
package org.conductoross.conductor.ai.agentspan.runtime.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

import org.conductoross.conductor.ai.http.ExternalDataLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * System task that fetches an API spec (OpenAPI 3.x, Swagger 2.0, or Postman Collection) from a
 * URL, parses it, and returns normalized tool descriptors.
 *
 * <h3>Task Input</h3>
 *
 * <ul>
 *   <li>{@code specUrl} (required) — URL to fetch the API specification from
 *   <li>{@code headers} (optional) — map of HTTP headers to include in the fetch request
 * </ul>
 *
 * <h3>Task Output</h3>
 *
 * <pre>{@code
 * {
 *   "tools": [ { "name", "description", "method", "path", "inputSchema" }, ... ],
 *   "baseUrl": "https://api.example.com",
 *   "format": "openapi3|swagger2|postman"
 * }
 * }</pre>
 */
public class ListApiToolsTask extends WorkflowSystemTask {

    public static final String TASK_TYPE = "LIST_API_TOOLS";

    private static final Logger logger = LoggerFactory.getLogger(ListApiToolsTask.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /** Well-known paths to probe when the initial URL returns HTML or a 404. */
    private static final List<String> WELL_KNOWN_SPEC_PATHS =
            List.of(
                    "/openapi.json",
                    "/swagger.json",
                    "/v3/api-docs",
                    "/swagger/v1/swagger.json",
                    "/api-docs",
                    "/.well-known/openapi.json");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ListApiToolsTask() {
        this(
                new ObjectMapper(),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build());
    }

    /** Visible-for-testing constructor. */
    ListApiToolsTask(ObjectMapper objectMapper, HttpClient httpClient) {
        super(TASK_TYPE);
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        logger.debug("ListApiToolsTask registered (task type={})", TASK_TYPE);
    }

    // -----------------------------------------------------------------------
    //  WorkflowSystemTask entry point
    // -----------------------------------------------------------------------

    @Override
    public void start(WorkflowModel workflow, TaskModel task, WorkflowExecutor workflowExecutor) {
        Map<String, Object> input = task.getInputData();

        String specUrl = input == null ? null : Objects.toString(input.get("specUrl"), null);
        if (specUrl == null || specUrl.isBlank()) {
            fail(task, "Missing required input parameter 'specUrl'");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, String> headers =
                input.get("headers") instanceof Map
                        ? (Map<String, String>) input.get("headers")
                        : Map.of();

        try {
            FetchResult fetchResult = fetchSpec(specUrl, headers);
            if (fetchResult == null) {
                fail(
                        task,
                        "Could not retrieve a valid API spec from "
                                + specUrl
                                + " (also tried well-known paths)");
                return;
            }

            JsonNode root = fetchResult.body;
            String format = detectFormat(root);
            if (format == null) {
                fail(task, "Unrecognised API spec format from " + fetchResult.url);
                return;
            }

            String baseUrl;
            List<Map<String, Object>> tools;

            switch (format) {
                case "openapi3" -> {
                    baseUrl = extractOpenApi3BaseUrl(root, fetchResult.url);
                    tools = parseOpenApi3(root);
                }
                case "swagger2" -> {
                    baseUrl = extractSwagger2BaseUrl(root, fetchResult.url);
                    tools = parseSwagger2(root);
                }
                case "postman" -> {
                    baseUrl = inferPostmanBaseUrl(root);
                    tools = parsePostman(root, "");
                }
                default -> {
                    fail(task, "Unrecognised API spec format");
                    return;
                }
            }
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("tools", tools);
            output.put("baseUrl", baseUrl);
            output.put("format", format);
            task.setOutputData(output);
            task.setStatus(TaskModel.Status.COMPLETED);

            logger.debug(
                    "LIST_API_TOOLS completed for {} — {} tools extracted (format={})",
                    specUrl,
                    tools.size(),
                    format);

        } catch (Exception e) {
            logger.error("LIST_API_TOOLS failed for {}", specUrl, e);
            fail(task, "Error processing API spec: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    //  Fetching
    // -----------------------------------------------------------------------

    private record FetchResult(String url, JsonNode body) {}

    /**
     * Fetches the spec URL. If the response is HTML or a 404 the method probes well-known spec
     * paths relative to the origin of the given URL.
     */
    private FetchResult fetchSpec(String specUrl, Map<String, String> headers) throws Exception {
        // Try the provided URL first
        FetchResult result = attemptFetch(specUrl, headers);
        if (result != null) {
            return result;
        }

        // Derive origin from the URL and try well-known paths
        URI uri = URI.create(specUrl);
        String origin = uri.getScheme() + "://" + uri.getAuthority();

        for (String path : WELL_KNOWN_SPEC_PATHS) {
            String candidateUrl = origin + path;
            result = attemptFetch(candidateUrl, headers);
            if (result != null) {
                logger.debug("Found spec at well-known path: {}", candidateUrl);
                return result;
            }
        }

        return null;
    }

    /** Returns a FetchResult if the response is valid JSON, otherwise null. */
    private FetchResult attemptFetch(String url, Map<String, String> headers) {
        try {
            HttpResponse<byte[]> response = sendWithValidatedRedirects(url, headers);

            if (response.statusCode() >= 400) {
                return null;
            }

            if (response.body().length > ExternalDataLimits.MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException(
                        "OpenAPI document exceeds the 10 MiB payload limit");
            }
            String body = new String(response.body(), java.nio.charset.StandardCharsets.UTF_8);
            if (body == null || body.isBlank()) {
                return null;
            }

            // Quick check — if the response looks like HTML, skip JSON parsing
            String trimmed = body.stripLeading();
            if (trimmed.startsWith("<") || trimmed.startsWith("<!")) {
                return null;
            }

            JsonNode root = objectMapper.readTree(body);
            if (root == null || root.isMissingNode()) {
                return null;
            }
            return new FetchResult(url, root);

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.debug("Fetch attempt failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    /** Follows a bounded redirect chain only after each destination passes the outbound policy. */
    private HttpResponse<byte[]> sendWithValidatedRedirects(String url, Map<String, String> headers)
            throws Exception {
        String currentUrl = url;
        for (int redirects = 0; redirects <= 5; redirects++) {
            HttpRequest.Builder request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(currentUrl))
                            .timeout(REQUEST_TIMEOUT)
                            .GET();
            addHeaders(request, headers);
            HttpResponse<byte[]> response =
                    httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 300 || response.statusCode() >= 400) {
                return response;
            }
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                throw new IllegalArgumentException(
                        "API spec redirect is missing a Location header");
            }
            String target = URI.create(currentUrl).resolve(location).toString();
            if (hasSensitiveHeaders(headers) && !isSameOrigin(currentUrl, target)) {
                throw new IllegalArgumentException(
                        "Refusing to forward credentials across an API spec redirect");
            }
            currentUrl = target;
        }
        throw new IllegalArgumentException("API spec exceeded the redirect limit");
    }

    private void addHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null) {
            return;
        }
        headers.forEach(
                (name, value) -> {
                    if (name == null
                            || value == null
                            || name.indexOf('\r') >= 0
                            || name.indexOf('\n') >= 0
                            || value.indexOf('\r') >= 0
                            || value.indexOf('\n') >= 0) {
                        throw new IllegalArgumentException(
                                "API spec headers must not contain CR or LF characters");
                    }
                    builder.header(name, value);
                });
    }

    private boolean hasSensitiveHeaders(Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        return headers.keySet().stream()
                .anyMatch(
                        name ->
                                "authorization".equalsIgnoreCase(name)
                                        || "cookie".equalsIgnoreCase(name)
                                        || "proxy-authorization".equalsIgnoreCase(name));
    }

    // -----------------------------------------------------------------------
    //  Format detection
    // -----------------------------------------------------------------------

    /** Returns {@code "openapi3"}, {@code "swagger2"}, {@code "postman"}, or null. */
    private String detectFormat(JsonNode root) {
        if (root == null) return null;

        // OpenAPI 3.x
        JsonNode openapi = root.get("openapi");
        if (openapi != null && openapi.isTextual() && openapi.asText().startsWith("3.")) {
            return "openapi3";
        }

        // Swagger 2.0
        JsonNode swagger = root.get("swagger");
        if (swagger != null && "2.0".equals(swagger.asText())) {
            return "swagger2";
        }

        // Postman Collection — has info._postman_id or root-level "item" array
        JsonNode info = root.get("info");
        if (info != null && info.has("_postman_id")) {
            return "postman";
        }
        if (root.has("item") && root.get("item").isArray()) {
            return "postman";
        }

        return null;
    }

    // -----------------------------------------------------------------------
    //  OpenAPI 3.x parsing
    // -----------------------------------------------------------------------

    private String extractOpenApi3BaseUrl(JsonNode root, String fetchedUrl) {
        JsonNode servers = root.get("servers");
        if (servers != null && servers.isArray() && !servers.isEmpty()) {
            String url = servers.get(0).path("url").asText("");
            if (!url.isBlank()) {
                URI serverUrl = URI.create(url);
                if (serverUrl.isAbsolute()) {
                    return url;
                }
                URI fetchedUri = URI.create(fetchedUrl);
                URI origin = URI.create(fetchedUri.getScheme() + "://" + fetchedUri.getAuthority());
                return origin.resolve(serverUrl).toString();
            }
        }
        // Fallback: derive from the URL we fetched
        URI uri = URI.create(fetchedUrl);
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private List<Map<String, Object>> parseOpenApi3(JsonNode root) {
        List<Map<String, Object>> tools = new ArrayList<>();
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) return tools;

        paths.fields()
                .forEachRemaining(
                        pathEntry -> {
                            String path = pathEntry.getKey();
                            JsonNode methods = pathEntry.getValue();
                            methods.fields()
                                    .forEachRemaining(
                                            methodEntry -> {
                                                String method = methodEntry.getKey().toUpperCase();
                                                if (isHttpMethod(method)) {
                                                    JsonNode operation = methodEntry.getValue();
                                                    tools.add(
                                                            buildToolDescriptor(
                                                                    method, path, operation, true));
                                                }
                                            });
                        });
        return tools;
    }

    private Map<String, Object> buildToolDescriptor(
            String method, String path, JsonNode operation, boolean isOpenApi3) {
        Map<String, Object> tool = new LinkedHashMap<>();

        // Name
        String operationId = operation.path("operationId").asText(null);
        String name =
                (operationId != null && !operationId.isBlank())
                        ? operationId
                        : method.toLowerCase() + "_" + slugifyPath(path);
        tool.put("name", name);

        // Description
        String summary = operation.path("summary").asText(null);
        String description = operation.path("description").asText(null);
        tool.put(
                "description",
                summary != null ? summary : (description != null ? description : ""));

        // Method + path
        tool.put("method", method);
        tool.put("path", path);

        // Input schema — merge parameters + requestBody
        tool.put("inputSchema", buildInputSchema(operation, isOpenApi3));

        return tool;
    }

    // -----------------------------------------------------------------------
    //  Swagger 2.0 parsing
    // -----------------------------------------------------------------------

    private String extractSwagger2BaseUrl(JsonNode root, String fetchedUrl) {
        String host = root.path("host").asText("");
        String basePath = root.path("basePath").asText("");
        if (!host.isBlank()) {
            // Prefer the first scheme listed, default to https
            String scheme = "https";
            JsonNode schemes = root.get("schemes");
            if (schemes != null && schemes.isArray() && !schemes.isEmpty()) {
                scheme = schemes.get(0).asText("https");
            }
            return scheme + "://" + host + basePath;
        }
        URI uri = URI.create(fetchedUrl);
        return uri.getScheme() + "://" + uri.getAuthority() + basePath;
    }

    private List<Map<String, Object>> parseSwagger2(JsonNode root) {
        List<Map<String, Object>> tools = new ArrayList<>();
        JsonNode paths = root.get("paths");
        if (paths == null || !paths.isObject()) return tools;

        paths.fields()
                .forEachRemaining(
                        pathEntry -> {
                            String path = pathEntry.getKey();
                            JsonNode methods = pathEntry.getValue();
                            methods.fields()
                                    .forEachRemaining(
                                            methodEntry -> {
                                                String method = methodEntry.getKey().toUpperCase();
                                                if (isHttpMethod(method)) {
                                                    JsonNode operation = methodEntry.getValue();
                                                    tools.add(
                                                            buildToolDescriptor(
                                                                    method, path, operation,
                                                                    false));
                                                }
                                            });
                        });
        return tools;
    }

    // -----------------------------------------------------------------------
    //  Postman Collection parsing
    // -----------------------------------------------------------------------

    private String inferPostmanBaseUrl(JsonNode root) {
        // Walk into items to find the first concrete URL
        return findFirstPostmanUrl(root.get("item"));
    }

    private String findFirstPostmanUrl(JsonNode items) {
        if (items == null || !items.isArray()) return "";
        for (JsonNode item : items) {
            JsonNode request = item.get("request");
            if (request != null) {
                String url = extractPostmanUrl(request);
                if (url != null && !url.isBlank()) {
                    try {
                        URI uri = URI.create(url);
                        return uri.getScheme() + "://" + uri.getAuthority();
                    } catch (Exception ignored) {
                        // not a valid URI, continue
                    }
                }
            }
            // Recurse into sub-folders
            JsonNode subItems = item.get("item");
            if (subItems != null && subItems.isArray()) {
                String result = findFirstPostmanUrl(subItems);
                if (result != null && !result.isBlank()) return result;
            }
        }
        return "";
    }

    private List<Map<String, Object>> parsePostman(JsonNode root, String prefix) {
        List<Map<String, Object>> tools = new ArrayList<>();
        JsonNode items = root.get("item");
        if (items == null || !items.isArray()) return tools;

        for (JsonNode item : items) {
            String itemName = item.path("name").asText("unnamed");
            String currentName = prefix.isEmpty() ? itemName : prefix + "_" + itemName;

            // If this item has sub-items, it is a folder — recurse
            if (item.has("item") && item.get("item").isArray()) {
                tools.addAll(parsePostman(item, currentName));
                continue;
            }

            JsonNode request = item.get("request");
            if (request == null) continue;

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", slugifyName(currentName));

            String description =
                    item.path("description").asText(request.path("description").asText(""));
            tool.put("description", description);

            String method =
                    request.isTextual()
                            ? request.asText("GET").toUpperCase()
                            : request.path("method").asText("GET").toUpperCase();
            tool.put("method", method);

            String url = extractPostmanUrl(request);
            String path = "";
            if (url != null) {
                try {
                    URI uri = URI.create(url);
                    path = uri.getPath() != null ? uri.getPath() : "";
                } catch (Exception e) {
                    path = url; // keep raw if not parseable
                }
            }
            tool.put("path", path);

            // Infer input schema from request body
            tool.put("inputSchema", buildPostmanInputSchema(request));

            tools.add(tool);
        }
        return tools;
    }

    private String extractPostmanUrl(JsonNode request) {
        JsonNode urlNode = request.get("url");
        if (urlNode == null) return null;
        if (urlNode.isTextual()) return urlNode.asText();
        // Postman URL object — prefer "raw" field
        return urlNode.path("raw").asText(null);
    }

    private Map<String, Object> buildPostmanInputSchema(JsonNode request) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();

        JsonNode body = request.get("body");
        if (body != null) {
            String mode = body.path("mode").asText("");
            if ("raw".equals(mode)) {
                String raw = body.path("raw").asText("");
                if (!raw.isBlank()) {
                    try {
                        JsonNode parsed = objectMapper.readTree(raw);
                        if (parsed.isObject()) {
                            parsed.fields()
                                    .forEachRemaining(
                                            f -> {
                                                Map<String, Object> prop = new LinkedHashMap<>();
                                                prop.put("type", inferJsonType(f.getValue()));
                                                properties.put(f.getKey(), prop);
                                            });
                        }
                    } catch (Exception ignored) {
                        // Not valid JSON — treat as opaque string body
                        properties.put("body", Map.of("type", "string"));
                    }
                }
            } else if ("urlencoded".equals(mode)) {
                JsonNode urlencoded = body.get("urlencoded");
                if (urlencoded != null && urlencoded.isArray()) {
                    for (JsonNode param : urlencoded) {
                        properties.put(
                                param.path("key").asText("unknown"), Map.of("type", "string"));
                    }
                }
            } else if ("formdata".equals(mode)) {
                JsonNode formdata = body.get("formdata");
                if (formdata != null && formdata.isArray()) {
                    for (JsonNode param : formdata) {
                        String type =
                                "file".equals(param.path("type").asText("")) ? "string" : "string";
                        properties.put(param.path("key").asText("unknown"), Map.of("type", type));
                    }
                }
            }
        }

        // Also include query parameters from the URL
        JsonNode urlNode = request.get("url");
        if (urlNode != null && urlNode.isObject()) {
            JsonNode query = urlNode.get("query");
            if (query != null && query.isArray()) {
                for (JsonNode q : query) {
                    properties.put(
                            q.path("key").asText("unknown"),
                            Map.of("type", "string", "in", "query"));
                }
            }
        }

        schema.put("properties", properties);
        return schema;
    }

    // -----------------------------------------------------------------------
    //  Input schema building (OpenAPI / Swagger)
    // -----------------------------------------------------------------------

    private Map<String, Object> buildInputSchema(JsonNode operation, boolean isOpenApi3) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // Parameters (path, query, header)
        JsonNode parameters = operation.get("parameters");
        if (parameters != null && parameters.isArray()) {
            for (JsonNode param : parameters) {
                String in = param.path("in").asText("");

                if (!isOpenApi3 && "body".equals(in)) {
                    // Swagger 2.0 body parameter
                    JsonNode bodySchema = param.get("schema");
                    if (bodySchema != null) {
                        mergeSchemaProperties(bodySchema, properties, required);
                    }
                    continue;
                }

                String paramName = param.path("name").asText("");
                if (paramName.isBlank()) continue;

                Map<String, Object> prop = new LinkedHashMap<>();
                JsonNode paramSchema = param.get("schema");
                if (paramSchema != null) {
                    prop.put("type", paramSchema.path("type").asText("string"));
                    if (paramSchema.has("format")) {
                        prop.put("format", paramSchema.path("format").asText());
                    }
                    if (paramSchema.has("enum")) {
                        prop.put("enum", nodeToList(paramSchema.get("enum")));
                    }
                } else {
                    // Swagger 2.0 — type is on the parameter itself
                    prop.put("type", param.path("type").asText("string"));
                    if (param.has("format")) {
                        prop.put("format", param.path("format").asText());
                    }
                    if (param.has("enum")) {
                        prop.put("enum", nodeToList(param.get("enum")));
                    }
                }
                prop.put("in", in);

                String desc = param.path("description").asText(null);
                if (desc != null) prop.put("description", desc);

                properties.put(paramName, prop);
                if (param.path("required").asBoolean(false)) {
                    required.add(paramName);
                }
            }
        }

        // OpenAPI 3.x requestBody
        if (isOpenApi3) {
            JsonNode requestBody = operation.get("requestBody");
            if (requestBody != null) {
                JsonNode content = requestBody.get("content");
                if (content != null) {
                    // Prefer application/json, fall back to first content type
                    JsonNode mediaType = content.get("application/json");
                    if (mediaType == null) {
                        Iterator<JsonNode> it = content.elements();
                        if (it.hasNext()) mediaType = it.next();
                    }
                    if (mediaType != null) {
                        JsonNode bodySchema = mediaType.get("schema");
                        if (bodySchema != null) {
                            mergeSchemaProperties(bodySchema, properties, required);
                        }
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * Merges properties from a schema object (e.g. requestBody schema) into the accumulated
     * properties map.
     */
    private void mergeSchemaProperties(
            JsonNode schema, Map<String, Object> properties, List<String> required) {
        JsonNode props = schema.get("properties");
        if (props != null && props.isObject()) {
            props.fields()
                    .forEachRemaining(
                            f -> {
                                Map<String, Object> prop = new LinkedHashMap<>();
                                JsonNode fieldSchema = f.getValue();
                                prop.put("type", fieldSchema.path("type").asText("string"));
                                if (fieldSchema.has("format")) {
                                    prop.put("format", fieldSchema.path("format").asText());
                                }
                                if (fieldSchema.has("description")) {
                                    prop.put(
                                            "description",
                                            fieldSchema.path("description").asText());
                                }
                                if (fieldSchema.has("enum")) {
                                    prop.put("enum", nodeToList(fieldSchema.get("enum")));
                                }
                                prop.put("in", "body");
                                properties.put(f.getKey(), prop);
                            });

            JsonNode req = schema.get("required");
            if (req != null && req.isArray()) {
                for (JsonNode r : req) {
                    required.add(r.asText());
                }
            }
        } else {
            // Schema without properties — expose as a single "body" property
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", schema.path("type").asText("object"));
            prop.put("in", "body");
            properties.put("body", prop);
        }
    }

    private boolean isSameOrigin(String firstUrl, String secondUrl) {
        URI first = URI.create(firstUrl);
        URI second = URI.create(secondUrl);
        return first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    // -----------------------------------------------------------------------
    //  Helpers
    // -----------------------------------------------------------------------

    private void fail(TaskModel task, String reason) {
        task.setStatus(TaskModel.Status.FAILED);
        task.setReasonForIncompletion(reason);
        task.getOutputData().put("error", reason);
        logger.warn("LIST_API_TOOLS failed: {}", reason);
    }

    private static boolean isHttpMethod(String method) {
        return switch (method) {
            case "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "TRACE" -> true;
            default -> false;
        };
    }

    private static String slugifyPath(String path) {
        return path.replaceAll("[{}]", "")
                .replaceAll("[^a-zA-Z0-9/]", "_")
                .replaceAll("/+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
    }

    private static String slugifyName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
    }

    private static String inferJsonType(JsonNode node) {
        if (node.isTextual()) return "string";
        if (node.isInt() || node.isLong()) return "integer";
        if (node.isFloat() || node.isDouble() || node.isBigDecimal()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isArray()) return "array";
        if (node.isObject()) return "object";
        return "string";
    }

    private static List<String> nodeToList(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode n : arrayNode) {
            list.add(n.asText());
        }
        return list;
    }
}
