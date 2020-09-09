/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.elasticsearch;

import com.google.common.base.Strings;
import com.netflix.conductor.core.config.Configuration;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface ElasticSearchConfiguration extends Configuration {

    String ELASTICSEARCH_PROPERTY_NAME = "workflow.elasticsearch.instanceType";
    ElasticSearchInstanceType ELASTICSEARCH_INSTANCE_TYPE_DEFAULT_VALUE = ElasticSearchInstanceType.MEMORY;

    String ELASTIC_SEARCH_URL_PROPERTY_NAME = "workflow.elasticsearch.url";
    String ELASTIC_SEARCH_URL_DEFAULT_VALUE = "localhost:9300";

    String ELASTIC_SEARCH_HEALTH_COLOR_PROPERTY_NAME = "workflow.elasticsearch.cluster.health.color";
    String ELASTIC_SEARCH_HEALTH_COLOR_DEFAULT_VALUE = "green";

    String ELASTIC_SEARCH_INDEX_NAME_PROPERTY_NAME = "workflow.elasticsearch.index.name";
    String ELASTIC_SEARCH_INDEX_NAME_DEFAULT_VALUE = "conductor";

    String TASK_LOG_INDEX_NAME_PROPERTY_NAME = "workflow.elasticsearch.tasklog.index.name";
    String TASK_LOG_INDEX_NAME_DEFAULT_VALUE = "task_log";

    String EMBEDDED_DATA_PATH_PROPERTY_NAME = "workflow.elasticsearch.embedded.data.path";
    String EMBEDDED_DATA_PATH_DEFAULT_VALUE = "path.data";

    String EMBEDDED_HOME_PATH_PROPERTY_NAME = "workflow.elasticsearch.embedded.data.home";
    String EMBEDDED_HOME_PATH_DEFAULT_VALUE = "path.home";

    String EMBEDDED_PORT_PROPERTY_NAME = "workflow.elasticsearch.embedded.port";
    int EMBEDDED_PORT_DEFAULT_VALUE = 9200;

    String EMBEDDED_CLUSTER_NAME_PROPERTY_NAME = "workflow.elasticsearch.embedded.cluster.name";
    String EMBEDDED_CLUSTER_NAME_DEFAULT_VALUE = "elasticsearch_test";

    String EMBEDDED_HOST_PROPERTY_NAME = "workflow.elasticsearch.embedded.host";
    String EMBEDDED_HOST_DEFAULT_VALUE = "127.0.0.1";

    String EMBEDDED_SETTINGS_FILE_PROPERTY_NAME = "workflow.elasticsearch.embedded.settings.file";
    String EMBEDDED_SETTINGS_FILE_DEFAULT_VALUE = "embedded-es.yml";

    String ELASTIC_SEARCH_ARCHIVE_SEARCH_BATCH_SIZE_PROPERTY_NAME = "workflow.elasticsearch.archive.search.batchSize";
    int ELASTIC_SEARCH_ARCHIVE_SEARCH_BATCH_SIZE_DEFAULT_VALUE = 5000;

    String ELASTIC_SEARCH_INDEX_BATCH_SIZE_PROPERTY_NAME = "workflow.elasticsearch.index.batchSize";
    int ELASTIC_SEARCH_INDEX_BATCH_SIZE_DEFAULT_VALUE = 1;

    String ELASTIC_SEARCH_ASYNC_DAO_WORKER_QUEUE_SIZE = "workflow.elasticsearch.async.dao.worker.queue.size";
    int DEFAULT_ASYNC_WORKER_QUEUE_SIZE = 100;

    String ELASTIC_SEARCH_ASYNC_DAO_MAX_POOL_SIZE = "workflow.elasticsearch.async.dao.max.pool.size";
    int DEFAULT_ASYNC_MAX_POOL_SIZE = 12;

    String ELASTIC_SEARCH_ASYNC_BUFFER_FLUSH_TIMEOUT_PROPERTY_NAME = "workflow.elasticsearch.async.buffer.flush.timeout.seconds";
    int ELASTIC_SEARCH_ASYNC_BUFFER_FLUSH_TIMEOUT_DEFAULT_VALUE = 10;

    String ELASTIC_SEARCH_INDEX_SHARD_COUNT_PROPERTY_NAME = "workflow.elasticsearch.index.shard.count";
    int ELASTIC_SEARCH_INDEX_SHARD_COUNT_DEFAULT_VALUE = 5;

    String ELASTIC_SEARCH_INDEX_REPLICAS_COUNT_PROPERTY_NAME = "workflow.elasticsearch.index.replicas.count";
    int ELASTIC_SEARCH_INDEX_REPLICAS_COUNT_DEFAULT_VALUE = 1;

    String ELASTIC_SEARCH_TASK_LOG_RESULT_LIMIT = "tasklog.elasticsearch.query.size";
    int ELASTIC_SEARCH_TASK_LOG_RESULT_LIMIT_DEFAULT_VALUE = 10;

    String ELASTIC_SEARCH_REST_CLIENT_CONNECTION_REQUEST_TIMEOUT_PROPERTY_NAME = "workflow.elasticsearch.rest.client.connectionRequestTimeout.milliseconds";
    int ELASTIC_SEARCH_REST_CLIENT_CONNECTION_REQUEST_TIMEOUT_DEFAULT_VALUE = -1;

    default String getURL() {
        return getProperty(ELASTIC_SEARCH_URL_PROPERTY_NAME, ELASTIC_SEARCH_URL_DEFAULT_VALUE);
    }

    default List<URI> getURIs(){

        String clusterAddress = getURL();

        String[] hosts = clusterAddress.split(",");

        return Arrays.stream(hosts).map( host ->
           (host.startsWith("http://") || host.startsWith("https://") || host.startsWith("tcp://")) ? URI.create(host) : URI.create("tcp://" + host)
        ).collect(Collectors.toList());
    }

    default String getIndexName() {
        return getProperty(ELASTIC_SEARCH_INDEX_NAME_PROPERTY_NAME, ELASTIC_SEARCH_INDEX_NAME_DEFAULT_VALUE);
    }

    default String getTasklogIndexName() {
        return getProperty(TASK_LOG_INDEX_NAME_PROPERTY_NAME, TASK_LOG_INDEX_NAME_DEFAULT_VALUE);
    }

    default String getClusterHealthColor() {
        return getProperty(ELASTIC_SEARCH_HEALTH_COLOR_PROPERTY_NAME, ELASTIC_SEARCH_HEALTH_COLOR_DEFAULT_VALUE);
    }

    default String getEmbeddedDataPath() {
        return getProperty(EMBEDDED_DATA_PATH_PROPERTY_NAME, EMBEDDED_DATA_PATH_DEFAULT_VALUE);
    }

    default String getEmbeddedHomePath() {
        return getProperty(EMBEDDED_HOME_PATH_PROPERTY_NAME, EMBEDDED_HOME_PATH_DEFAULT_VALUE);
    }

    default int getEmbeddedPort() {
        return getIntProperty(EMBEDDED_PORT_PROPERTY_NAME, EMBEDDED_PORT_DEFAULT_VALUE);

    }

    default String getEmbeddedClusterName() {
        return getProperty(EMBEDDED_CLUSTER_NAME_PROPERTY_NAME, EMBEDDED_CLUSTER_NAME_DEFAULT_VALUE);
    }

    default String getEmbeddedHost() {
        return getProperty(EMBEDDED_HOST_PROPERTY_NAME, EMBEDDED_HOST_DEFAULT_VALUE);
    }

    default String getEmbeddedSettingsFile() {
        return getProperty(EMBEDDED_SETTINGS_FILE_PROPERTY_NAME, EMBEDDED_SETTINGS_FILE_DEFAULT_VALUE);
    }

    default int getElasticsearchRestClientConnectionRequestTimeout() {
        return getIntProperty(ELASTIC_SEARCH_REST_CLIENT_CONNECTION_REQUEST_TIMEOUT_PROPERTY_NAME,
                ELASTIC_SEARCH_REST_CLIENT_CONNECTION_REQUEST_TIMEOUT_DEFAULT_VALUE);
    }

    default ElasticSearchInstanceType getElasticSearchInstanceType() {
        ElasticSearchInstanceType elasticSearchInstanceType = ELASTICSEARCH_INSTANCE_TYPE_DEFAULT_VALUE;
        String instanceTypeConfig = getProperty(ELASTICSEARCH_PROPERTY_NAME, "");
        if (!Strings.isNullOrEmpty(instanceTypeConfig)) {
            elasticSearchInstanceType = ElasticSearchInstanceType.valueOf(instanceTypeConfig.toUpperCase());
        }
        return elasticSearchInstanceType;
    }

    enum ElasticSearchInstanceType {
        MEMORY, EXTERNAL
    }

    default int getArchiveSearchBatchSize() {
        return getIntProperty(ELASTIC_SEARCH_ARCHIVE_SEARCH_BATCH_SIZE_PROPERTY_NAME,
            ELASTIC_SEARCH_ARCHIVE_SEARCH_BATCH_SIZE_DEFAULT_VALUE);
    }

    default int getIndexBatchSize() {
        return getIntProperty(ELASTIC_SEARCH_INDEX_BATCH_SIZE_PROPERTY_NAME,
                ELASTIC_SEARCH_INDEX_BATCH_SIZE_DEFAULT_VALUE);
    }

    default int getAsyncWorkerQueueSize() {
        return  getIntProperty(ELASTIC_SEARCH_ASYNC_DAO_WORKER_QUEUE_SIZE, DEFAULT_ASYNC_WORKER_QUEUE_SIZE);
    }

    default int getAsyncMaxPoolSize() {
        return  getIntProperty(ELASTIC_SEARCH_ASYNC_DAO_MAX_POOL_SIZE, DEFAULT_ASYNC_MAX_POOL_SIZE);
    }

    default int getAsyncBufferFlushTimeout() {
        return getIntProperty(ELASTIC_SEARCH_ASYNC_BUFFER_FLUSH_TIMEOUT_PROPERTY_NAME,
            ELASTIC_SEARCH_ASYNC_BUFFER_FLUSH_TIMEOUT_DEFAULT_VALUE);
    }

    default int getElasticSearchIndexShardCount()
    {
        return getIntProperty(ELASTIC_SEARCH_INDEX_SHARD_COUNT_PROPERTY_NAME,
                              ELASTIC_SEARCH_INDEX_SHARD_COUNT_DEFAULT_VALUE);
    }

    default int getElasticSearchIndexReplicationCount()
    {
        return getIntProperty(ELASTIC_SEARCH_INDEX_REPLICAS_COUNT_PROPERTY_NAME,
                              ELASTIC_SEARCH_INDEX_REPLICAS_COUNT_DEFAULT_VALUE);
    }

    default int getElasticSearchTasklogLimit()
    {
        return getIntProperty(ELASTIC_SEARCH_TASK_LOG_RESULT_LIMIT,
                ELASTIC_SEARCH_TASK_LOG_RESULT_LIMIT_DEFAULT_VALUE);
    }
}
