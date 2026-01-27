/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.os.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.env.Environment;

import jakarta.annotation.PostConstruct;

/**
 * Configuration properties for OpenSearch integration.
 *
 * <p>This class supports the dedicated {@code conductor.opensearch.*} namespace. For backward
 * compatibility, legacy {@code conductor.elasticsearch.*} properties are also supported but
 * deprecated. When both namespaces are configured, {@code conductor.opensearch.*} takes precedence.
 *
 * <p><b>Migration Guide:</b> Replace {@code conductor.elasticsearch.*} properties with their {@code
 * conductor.opensearch.*} equivalents. The legacy namespace will be removed in a future major
 * release.
 *
 * @see <a href="https://github.com/conductor-oss/conductor">Conductor Documentation</a>
 */
@ConfigurationProperties("conductor.opensearch")
public class OpenSearchProperties {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchProperties.class);

    private static final String LEGACY_PREFIX = "conductor.elasticsearch.";
    private static final String NEW_PREFIX = "conductor.opensearch.";

    /** Supported OpenSearch versions. Update this set when new versions are supported. */
    private static final Set<Integer> SUPPORTED_VERSIONS = Set.of(1, 2);

    private Environment environment;

    /**
     * The OpenSearch version (1, 2, etc.) for version-specific API handling. Defaults to 2
     * (OpenSearch 2.x).
     */
    private int version = 2;

    /**
     * The comma separated list of urls for the OpenSearch cluster. Format --
     * host1:port1,host2:port2
     */
    private String url = "localhost:9201";

    /** The index prefix to be used when creating indices */
    private String indexPrefix = "conductor";

    /** The color of the OpenSearch cluster to wait for to confirm healthy status */
    private String clusterHealthColor = "green";

    /** The size of the batch to be used for bulk indexing in async mode */
    private int indexBatchSize = 1;

    /** The size of the queue used for holding async indexing tasks */
    private int asyncWorkerQueueSize = 100;

    /** The maximum number of threads allowed in the async pool */
    private int asyncMaxPoolSize = 12;

    /**
     * The time in seconds after which the async buffers will be flushed (if no activity) to prevent
     * data loss
     */
    @DurationUnit(ChronoUnit.SECONDS)
    private Duration asyncBufferFlushTimeout = Duration.ofSeconds(10);

    /** The number of shards that the index will be created with */
    private int indexShardCount = 5;

    /** The number of replicas that the index will be configured to have */
    private int indexReplicasCount = 0;

    /** The number of task log results that will be returned in the response */
    private int taskLogResultLimit = 10;

    /** The timeout in milliseconds used when requesting a connection from the connection manager */
    private int restClientConnectionRequestTimeout = -1;

    /** Used to control if index management is to be enabled or will be controlled externally */
    private boolean autoIndexManagementEnabled = true;

    /**
     * Document types are deprecated in OpenSearch. This property can be used to disable the use of
     * specific document types with an override.
     *
     * <p><em>Note that this property will only take effect if {@link
     * OpenSearchProperties#isAutoIndexManagementEnabled} is set to false and index management is
     * handled outside of this module.</em>
     */
    private String documentTypeOverride = "";

    /** OpenSearch basic auth username */
    private String username;

    /** OpenSearch basic auth password */
    private String password;

    public OpenSearchProperties() {}

    public OpenSearchProperties(Environment environment) {
        this.environment = environment;
    }

    /**
     * Post-construction initialization that handles backward compatibility with legacy
     * conductor.elasticsearch.* properties. Logs deprecation warnings when legacy properties are
     * detected. Also validates the configured OpenSearch version.
     */
    @PostConstruct
    public void init() {
        if (environment == null) {
            return;
        }

        boolean usingLegacyProperties = false;

        // Check and apply legacy version property
        // Note: conductor.elasticsearch.version=0 is a special value used to disable ES7
        // auto-config
        // For OpenSearch, we only consider positive version numbers from legacy config
        String legacyVersion = environment.getProperty(LEGACY_PREFIX + "version");
        if (legacyVersion != null && !hasNewProperty("version")) {
            try {
                int parsedVersion = Integer.parseInt(legacyVersion);
                // Only use legacy version if it's a valid OpenSearch version (not 0 which is ES7
                // disable flag)
                if (parsedVersion > 0) {
                    this.version = parsedVersion;
                    usingLegacyProperties = true;
                    log.info(
                            "Using OpenSearch version {} from legacy property 'conductor.elasticsearch.version'. "
                                    + "Consider migrating to 'conductor.opensearch.version'.",
                            parsedVersion);
                }
            } catch (NumberFormatException e) {
                log.warn(
                        "Invalid value '{}' for conductor.elasticsearch.version. Using default version {}.",
                        legacyVersion,
                        this.version);
            }
        }

        // Check and apply legacy properties with deprecation warnings
        String legacyUrl = environment.getProperty(LEGACY_PREFIX + "url");
        if (legacyUrl != null && !hasNewProperty("url")) {
            this.url = legacyUrl;
            usingLegacyProperties = true;
        }

        String legacyIndexName = environment.getProperty(LEGACY_PREFIX + "indexName");
        if (legacyIndexName != null && !hasNewProperty("indexPrefix")) {
            this.indexPrefix = legacyIndexName;
            usingLegacyProperties = true;
        }

        String legacyClusterHealthColor =
                environment.getProperty(LEGACY_PREFIX + "clusterHealthColor");
        if (legacyClusterHealthColor != null && !hasNewProperty("clusterHealthColor")) {
            this.clusterHealthColor = legacyClusterHealthColor;
            usingLegacyProperties = true;
        }

        String legacyIndexBatchSize = environment.getProperty(LEGACY_PREFIX + "indexBatchSize");
        if (legacyIndexBatchSize != null && !hasNewProperty("indexBatchSize")) {
            this.indexBatchSize = Integer.parseInt(legacyIndexBatchSize);
            usingLegacyProperties = true;
        }

        String legacyAsyncWorkerQueueSize =
                environment.getProperty(LEGACY_PREFIX + "asyncWorkerQueueSize");
        if (legacyAsyncWorkerQueueSize != null && !hasNewProperty("asyncWorkerQueueSize")) {
            this.asyncWorkerQueueSize = Integer.parseInt(legacyAsyncWorkerQueueSize);
            usingLegacyProperties = true;
        }

        String legacyAsyncMaxPoolSize = environment.getProperty(LEGACY_PREFIX + "asyncMaxPoolSize");
        if (legacyAsyncMaxPoolSize != null && !hasNewProperty("asyncMaxPoolSize")) {
            this.asyncMaxPoolSize = Integer.parseInt(legacyAsyncMaxPoolSize);
            usingLegacyProperties = true;
        }

        String legacyIndexShardCount = environment.getProperty(LEGACY_PREFIX + "indexShardCount");
        if (legacyIndexShardCount != null && !hasNewProperty("indexShardCount")) {
            this.indexShardCount = Integer.parseInt(legacyIndexShardCount);
            usingLegacyProperties = true;
        }

        String legacyIndexReplicasCount =
                environment.getProperty(LEGACY_PREFIX + "indexReplicasCount");
        if (legacyIndexReplicasCount != null && !hasNewProperty("indexReplicasCount")) {
            this.indexReplicasCount = Integer.parseInt(legacyIndexReplicasCount);
            usingLegacyProperties = true;
        }

        String legacyTaskLogResultLimit =
                environment.getProperty(LEGACY_PREFIX + "taskLogResultLimit");
        if (legacyTaskLogResultLimit != null && !hasNewProperty("taskLogResultLimit")) {
            this.taskLogResultLimit = Integer.parseInt(legacyTaskLogResultLimit);
            usingLegacyProperties = true;
        }

        String legacyRestClientConnectionRequestTimeout =
                environment.getProperty(LEGACY_PREFIX + "restClientConnectionRequestTimeout");
        if (legacyRestClientConnectionRequestTimeout != null
                && !hasNewProperty("restClientConnectionRequestTimeout")) {
            this.restClientConnectionRequestTimeout =
                    Integer.parseInt(legacyRestClientConnectionRequestTimeout);
            usingLegacyProperties = true;
        }

        String legacyAutoIndexManagementEnabled =
                environment.getProperty(LEGACY_PREFIX + "autoIndexManagementEnabled");
        if (legacyAutoIndexManagementEnabled != null
                && !hasNewProperty("autoIndexManagementEnabled")) {
            this.autoIndexManagementEnabled =
                    Boolean.parseBoolean(legacyAutoIndexManagementEnabled);
            usingLegacyProperties = true;
        }

        String legacyDocumentTypeOverride =
                environment.getProperty(LEGACY_PREFIX + "documentTypeOverride");
        if (legacyDocumentTypeOverride != null && !hasNewProperty("documentTypeOverride")) {
            this.documentTypeOverride = legacyDocumentTypeOverride;
            usingLegacyProperties = true;
        }

        String legacyUsername = environment.getProperty(LEGACY_PREFIX + "username");
        if (legacyUsername != null && !hasNewProperty("username")) {
            this.username = legacyUsername;
            usingLegacyProperties = true;
        }

        String legacyPassword = environment.getProperty(LEGACY_PREFIX + "password");
        if (legacyPassword != null && !hasNewProperty("password")) {
            this.password = legacyPassword;
            usingLegacyProperties = true;
        }

        if (usingLegacyProperties) {
            log.warn(
                    "DEPRECATION WARNING: You are using legacy 'conductor.elasticsearch.*' properties "
                            + "for OpenSearch configuration. Please migrate to 'conductor.opensearch.*' namespace. "
                            + "The legacy namespace will be removed in a future major release. "
                            + "See migration guide at: https://conductor-oss.github.io/conductor/");
        }

        // Validate the configured OpenSearch version
        validateVersion();
    }

    /**
     * Validates that the configured OpenSearch version is supported.
     *
     * @throws IllegalArgumentException if the version is not supported
     */
    private void validateVersion() {
        if (!SUPPORTED_VERSIONS.contains(this.version)) {
            String supportedVersionsStr =
                    SUPPORTED_VERSIONS.stream()
                            .sorted()
                            .map(String::valueOf)
                            .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    String.format(
                            "Unsupported OpenSearch version: %d. Supported versions are: [%s]. "
                                    + "Please configure 'conductor.opensearch.version' with a supported version.",
                            this.version, supportedVersionsStr));
        }
        log.info("OpenSearch configured with version: {}", this.version);
    }

    /**
     * Returns the set of supported OpenSearch versions.
     *
     * @return unmodifiable set of supported version numbers
     */
    public static Set<Integer> getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    private boolean hasNewProperty(String propertyName) {
        return environment != null && environment.containsProperty(NEW_PREFIX + propertyName);
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getIndexPrefix() {
        return indexPrefix;
    }

    public void setIndexPrefix(String indexPrefix) {
        this.indexPrefix = indexPrefix;
    }

    public String getClusterHealthColor() {
        return clusterHealthColor;
    }

    public void setClusterHealthColor(String clusterHealthColor) {
        this.clusterHealthColor = clusterHealthColor;
    }

    public int getIndexBatchSize() {
        return indexBatchSize;
    }

    public void setIndexBatchSize(int indexBatchSize) {
        this.indexBatchSize = indexBatchSize;
    }

    public int getAsyncWorkerQueueSize() {
        return asyncWorkerQueueSize;
    }

    public void setAsyncWorkerQueueSize(int asyncWorkerQueueSize) {
        this.asyncWorkerQueueSize = asyncWorkerQueueSize;
    }

    public int getAsyncMaxPoolSize() {
        return asyncMaxPoolSize;
    }

    public void setAsyncMaxPoolSize(int asyncMaxPoolSize) {
        this.asyncMaxPoolSize = asyncMaxPoolSize;
    }

    public Duration getAsyncBufferFlushTimeout() {
        return asyncBufferFlushTimeout;
    }

    public void setAsyncBufferFlushTimeout(Duration asyncBufferFlushTimeout) {
        this.asyncBufferFlushTimeout = asyncBufferFlushTimeout;
    }

    public int getIndexShardCount() {
        return indexShardCount;
    }

    public void setIndexShardCount(int indexShardCount) {
        this.indexShardCount = indexShardCount;
    }

    public int getIndexReplicasCount() {
        return indexReplicasCount;
    }

    public void setIndexReplicasCount(int indexReplicasCount) {
        this.indexReplicasCount = indexReplicasCount;
    }

    public int getTaskLogResultLimit() {
        return taskLogResultLimit;
    }

    public void setTaskLogResultLimit(int taskLogResultLimit) {
        this.taskLogResultLimit = taskLogResultLimit;
    }

    public int getRestClientConnectionRequestTimeout() {
        return restClientConnectionRequestTimeout;
    }

    public void setRestClientConnectionRequestTimeout(int restClientConnectionRequestTimeout) {
        this.restClientConnectionRequestTimeout = restClientConnectionRequestTimeout;
    }

    public boolean isAutoIndexManagementEnabled() {
        return autoIndexManagementEnabled;
    }

    public void setAutoIndexManagementEnabled(boolean autoIndexManagementEnabled) {
        this.autoIndexManagementEnabled = autoIndexManagementEnabled;
    }

    public String getDocumentTypeOverride() {
        return documentTypeOverride;
    }

    public void setDocumentTypeOverride(String documentTypeOverride) {
        this.documentTypeOverride = documentTypeOverride;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<URL> toURLs() {
        String clusterAddress = getUrl();
        String[] hosts = clusterAddress.split(",");
        return Arrays.stream(hosts)
                .map(
                        host ->
                                (host.startsWith("http://") || host.startsWith("https://"))
                                        ? toURL(host)
                                        : toURL("http://" + host))
                .collect(Collectors.toList());
    }

    private URL toURL(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(url + "can not be converted to java.net.URL");
        }
    }
}
