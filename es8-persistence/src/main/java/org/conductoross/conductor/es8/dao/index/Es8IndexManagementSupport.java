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
package org.conductoross.conductor.es8.dao.index;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.conductoross.conductor.es8.config.ElasticSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.core.exception.NonTransientException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.HealthStatus;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.ilm.IlmPolicy;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.get_index_template.IndexTemplateItem;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles ES8 bootstrap and index management concerns (cluster health, templates, ILM and aliases).
 */
class Es8IndexManagementSupport {

    private static final Logger logger = LoggerFactory.getLogger(Es8IndexManagementSupport.class);
    private static final String ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE = "50gb";

    private final ElasticsearchClient elasticSearchClient;
    private final RetryTemplate retryTemplate;
    private final ElasticSearchProperties properties;
    private final ObjectMapper objectMapper;
    private final String workflowIndexName;
    private final String taskIndexName;
    private final String logIndexName;
    private final String messageIndexName;
    private final String eventIndexName;
    private final String ilmPolicyName;
    private final String componentTemplateName;
    private final String clusterHealthColor;
    private final String resourcePrefix;

    Es8IndexManagementSupport(
            ElasticsearchClient elasticSearchClient,
            RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ObjectMapper objectMapper,
            String indexPrefix,
            String workflowIndexName,
            String taskIndexName,
            String logIndexName,
            String messageIndexName,
            String eventIndexName) {
        this.elasticSearchClient = elasticSearchClient;
        this.retryTemplate = retryTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.workflowIndexName = workflowIndexName;
        this.taskIndexName = taskIndexName;
        this.logIndexName = logIndexName;
        this.messageIndexName = messageIndexName;
        this.eventIndexName = eventIndexName;
        this.clusterHealthColor = properties.getClusterHealthColor();
        this.resourcePrefix = normalizeResourcePrefix(indexPrefix);
        this.ilmPolicyName = prefixedResourceName(this.resourcePrefix, "default-ilm-policy");
        this.componentTemplateName = prefixedResourceName(this.resourcePrefix, "common-settings");
    }

    void setup() throws IOException {
        waitForHealthyCluster();
        if (properties.isAutoIndexManagementEnabled()) {
            createIndexesTemplates();
            createWorkflowIndex();
            createTaskIndex();
            createTaskLogIndex();
            createMessageIndex();
            createEventIndex();
        }
    }

    private void createIndexesTemplates() throws IOException {
        ensureIlmPolicy();
        ensureComponentTemplate();
        initIndexesTemplates();
    }

    private void initIndexesTemplates() throws IOException {
        TemplateDefinition workflowDefinition = loadTemplateDefinition("/template_workflow.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_workflow"),
                "template_workflow",
                workflowIndexName + "-*",
                workflowDefinition.mappings,
                workflowDefinition.settings,
                workflowIndexName);

        TemplateDefinition taskDefinition = loadTemplateDefinition("/template_task.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_task"),
                "template_task",
                taskIndexName + "-*",
                taskDefinition.mappings,
                taskDefinition.settings,
                taskIndexName);

        TemplateDefinition logDefinition = loadTemplateDefinition("/template_task_log.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_task_log"),
                "template_task_log",
                logIndexName + "-*",
                logDefinition.mappings,
                logDefinition.settings,
                logIndexName);

        TemplateDefinition eventDefinition = loadTemplateDefinition("/template_event.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_event"),
                "template_event",
                eventIndexName + "-*",
                eventDefinition.mappings,
                eventDefinition.settings,
                eventIndexName);

        TemplateDefinition messageDefinition = loadTemplateDefinition("/template_message.json");
        initIndexAliasTemplate(
                prefixedResourceName(resourcePrefix, "template_message"),
                "template_message",
                messageIndexName + "-*",
                messageDefinition.mappings,
                messageDefinition.settings,
                messageIndexName);
    }

    private void initIndexAliasTemplate(
            String templateName,
            String legacyTemplateName,
            String indexPattern,
            JsonNode mappings,
            JsonNode additionalSettings,
            String aliasName)
            throws IOException {
        logger.info("Creating/updating the index template '{}'", templateName);
        deleteLegacyIndexTemplateIfOwned(legacyTemplateName, templateName, indexPattern, aliasName);
        IndexTemplateMapping.Builder template =
                new IndexTemplateMapping.Builder()
                        .settings(buildIndexTemplateSettings(additionalSettings, aliasName))
                        .aliases(aliasName, a -> a);
        if (mappings != null) {
            template.mappings(parseTypeMapping(mappings));
        }
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .indices()
                            .putIndexTemplate(
                                    r ->
                                            r.name(templateName)
                                                    .indexPatterns(indexPattern)
                                                    .priority(500L)
                                                    .composedOf(componentTemplateName)
                                                    .template(template.build()));
                    return null;
                });
    }

    private void deleteLegacyIndexTemplateIfOwned(
            String legacyTemplateName,
            String replacementTemplateName,
            String expectedIndexPattern,
            String expectedAliasName)
            throws IOException {
        if (StringUtils.isBlank(legacyTemplateName)
                || legacyTemplateName.equals(replacementTemplateName)) {
            return;
        }

        IndexTemplateItem legacyTemplate = getIndexTemplate(legacyTemplateName);
        if (legacyTemplate == null) {
            return;
        }

        List<String> indexPatterns = legacyTemplate.indexTemplate().indexPatterns();
        if (indexPatterns == null || !indexPatterns.contains(expectedIndexPattern)) {
            return;
        }

        var template = legacyTemplate.indexTemplate().template();
        if (template == null || template.aliases() == null) {
            return;
        }
        if (!template.aliases().containsKey(expectedAliasName)) {
            return;
        }

        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .indices()
                            .deleteIndexTemplate(r -> r.name(legacyTemplateName));
                    return null;
                });
        logger.info(
                "Deleted legacy index template '{}' for pattern '{}' (replaced by '{}')",
                legacyTemplateName,
                expectedIndexPattern,
                replacementTemplateName);
    }

    private IndexTemplateItem getIndexTemplate(String templateName) throws IOException {
        try {
            var response =
                    executeWithRetry(
                            () ->
                                    elasticSearchClient
                                            .indices()
                                            .getIndexTemplate(r -> r.name(templateName)));
            if (response.indexTemplates() == null || response.indexTemplates().isEmpty()) {
                return null;
            }
            return response.indexTemplates().getFirst();
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    private void createWorkflowIndex() throws IOException {
        ensureWriteIndex(workflowIndexName);
    }

    private void createTaskIndex() throws IOException {
        ensureWriteIndex(taskIndexName);
    }

    private void createTaskLogIndex() throws IOException {
        ensureWriteIndex(logIndexName);
    }

    private void createMessageIndex() throws IOException {
        ensureWriteIndex(messageIndexName);
    }

    private void createEventIndex() throws IOException {
        ensureWriteIndex(eventIndexName);
    }

    private void ensureIlmPolicy() throws IOException {
        if (ilmPolicyExists(ilmPolicyName)) {
            return;
        }
        IlmPolicy policy =
                IlmPolicy.of(
                        p ->
                                p.phases(
                                        ph ->
                                                ph.hot(
                                                        hot ->
                                                                hot.actions(
                                                                        a ->
                                                                                a.rollover(
                                                                                        r ->
                                                                                                r
                                                                                                        .maxPrimaryShardSize(
                                                                                                                ILM_ROLLOVER_MAX_PRIMARY_SHARD_SIZE))))));
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .ilm()
                            .putLifecycle(r -> r.name(ilmPolicyName).policy(policy));
                    return null;
                });
        logger.info("Created ILM policy '{}'", ilmPolicyName);
    }

    private void ensureComponentTemplate() throws IOException {
        IndexSettings settings = buildCommonIndexSettings();
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .cluster()
                            .putComponentTemplate(
                                    r ->
                                            r.name(componentTemplateName)
                                                    .template(t -> t.settings(settings)));
                    return null;
                });
        logger.info("Created/updated component template '{}'", componentTemplateName);
    }

    private static HealthStatus parseHealthStatus(String value) {
        if (value == null) {
            return HealthStatus.Green;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "green" -> HealthStatus.Green;
            case "yellow" -> HealthStatus.Yellow;
            case "red" -> HealthStatus.Red;
            default -> HealthStatus.Green;
        };
    }

    private boolean ilmPolicyExists(String policyName) throws IOException {
        try {
            elasticSearchClient.ilm().getLifecycle(r -> r.name(policyName));
            return true;
        } catch (ElasticsearchException e) {
            if (e.status() == HttpStatus.SC_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    private TypeMapping parseTypeMapping(JsonNode mappings) throws IOException {
        String json = objectMapper.writeValueAsString(mappings);
        return TypeMapping.of(b -> b.withJson(new StringReader(json)));
    }

    private IndexSettings buildIndexTemplateSettings(
            JsonNode additionalSettings, String rolloverAlias) throws IOException {
        IndexSettings.Builder builder = new IndexSettings.Builder();
        if (additionalSettings != null
                && additionalSettings.isObject()
                && additionalSettings.size() > 0) {
            builder.withJson(new StringReader(objectMapper.writeValueAsString(additionalSettings)));
        }
        builder.lifecycle(l -> l.rolloverAlias(rolloverAlias));
        return builder.build();
    }

    private IndexSettings buildCommonIndexSettings() {
        IndexSettings.Builder builder =
                new IndexSettings.Builder()
                        .numberOfShards(String.valueOf(properties.getIndexShardCount()))
                        .numberOfReplicas(String.valueOf(properties.getIndexReplicasCount()))
                        .lifecycle(l -> l.name(ilmPolicyName));
        String refreshInterval = formatRefreshInterval(properties.getIndexRefreshInterval());
        if (refreshInterval != null) {
            builder.refreshInterval(Time.of(t -> t.time(refreshInterval)));
        }
        return builder.build();
    }

    private String formatRefreshInterval(Duration refreshInterval) {
        if (refreshInterval == null) {
            return null;
        }
        if (refreshInterval.isZero() || refreshInterval.isNegative()) {
            return "-1";
        }
        return refreshInterval.toMillis() + "ms";
    }

    private TemplateDefinition loadTemplateDefinition(String templateResource) {
        try (InputStream stream =
                ElasticSearchRestDAOV8.class.getResourceAsStream(templateResource)) {
            if (stream == null) {
                throw new IOException("Template resource not found: " + templateResource);
            }
            JsonNode root = objectMapper.readTree(IOUtils.toString(stream));
            JsonNode templateNode = root.get("template");
            JsonNode settings = templateNode != null ? templateNode.get("settings") : null;
            JsonNode mappings = templateNode != null ? templateNode.get("mappings") : null;
            return new TemplateDefinition(settings, mappings);
        } catch (IOException e) {
            throw new NonTransientException("Failed to load template: " + templateResource, e);
        }
    }

    private void ensureWriteIndex(String aliasName) throws IOException {
        BooleanResponse exists =
                executeWithRetry(
                        () -> elasticSearchClient.indices().existsAlias(r -> r.name(aliasName)));
        if (exists.value()) {
            return;
        }
        String indexName = aliasName + "-000001";
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .indices()
                            .create(
                                    r ->
                                            r.index(indexName)
                                                    .aliases(aliasName, a -> a.isWriteIndex(true)));
                    return null;
                });
        logger.info("Created write index '{}' for alias '{}'", indexName, aliasName);
    }

    private void waitForHealthyCluster() throws IOException {
        HealthStatus waitForStatus = parseHealthStatus(clusterHealthColor);
        executeWithRetry(
                () -> {
                    elasticSearchClient
                            .cluster()
                            .health(
                                    h ->
                                            h.waitForStatus(waitForStatus)
                                                    .timeout(Time.of(t -> t.time("30s"))));
                    return null;
                });
    }

    private static class TemplateDefinition {
        private final JsonNode settings;
        private final JsonNode mappings;

        private TemplateDefinition(JsonNode settings, JsonNode mappings) {
            this.settings = settings;
            this.mappings = mappings;
        }
    }

    private <T> T executeWithRetry(Callable<T> action) throws IOException {
        try {
            return retryTemplate.execute(context -> action.call());
        } catch (Exception e) {
            if (e instanceof IOException ioException) {
                throw ioException;
            }
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("Elasticsearch operation failed", e);
        }
    }

    private static String normalizeResourcePrefix(String indexPrefix) {
        String prefix = StringUtils.trimToNull(indexPrefix);
        if (prefix == null) {
            return null;
        }
        prefix = StringUtils.stripEnd(prefix, "_-");
        return StringUtils.trimToNull(prefix);
    }

    private static String prefixedResourceName(String resourcePrefix, String baseName) {
        if (StringUtils.isBlank(resourcePrefix)) {
            return baseName;
        }
        if (StringUtils.isBlank(baseName)) {
            throw new IllegalArgumentException("baseName must not be blank");
        }
        return resourcePrefix + "-" + baseName;
    }
}
