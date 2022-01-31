/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.es6.config;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("conductor.elasticsearch")
public class ElasticSearchProperties {

    /**
     * The comma separated list of urls for the elasticsearch cluster. Format --
     * host1:port1,host2:port2
     */
    private String url = "localhost:9300";

    /** The index prefix to be used when creating indices */
    private String indexPrefix = "conductor";

    /** The color of the elasticserach cluster to wait for to confirm healthy status */
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
    private int indexReplicasCount = 1;

    /** The number of task log results that will be returned in the response */
    private int taskLogResultLimit = 10;

    /** The timeout in milliseconds used when requesting a connection from the connection manager */
    private int restClientConnectionRequestTimeout = -1;

    /** Used to control if index management is to be enabled or will be controlled externally */
    private boolean autoIndexManagementEnabled = true;

    /**
     * Document types are deprecated in ES6 and removed from ES7. This property can be used to
     * disable the use of specific document types with an override. This property is currently used
     * in ES6 module.
     *
     * <p><em>Note that this property will only take effect if {@link
     * ElasticSearchProperties#isAutoIndexManagementEnabled} is set to false and index management is
     * handled outside of this module.</em>
     */
    private String documentTypeOverride = "";

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

    public List<URL> toURLs() {
        String clusterAddress = getUrl();
        String[] hosts = clusterAddress.split(",");
        return Arrays.stream(hosts)
                .map(
                        host ->
                                (host.startsWith("http://")
                                                || host.startsWith("https://")
                                                || host.startsWith("tcp://"))
                                        ? toURL(host)
                                        : toURL("tcp://" + host))
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
