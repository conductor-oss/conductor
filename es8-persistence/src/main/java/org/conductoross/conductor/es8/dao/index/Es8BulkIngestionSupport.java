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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.conductoross.conductor.es8.config.ElasticSearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import com.netflix.conductor.metrics.Monitors;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;

/** Owns async bulk ingestion setup and lifecycle for ES8 indexing writes. */
class Es8BulkIngestionSupport {

    private static final Logger logger = LoggerFactory.getLogger(Es8BulkIngestionSupport.class);

    private final ElasticsearchClient elasticSearchClient;
    private final ElasticsearchAsyncClient elasticSearchAsyncClient;
    private final RetryTemplate retryTemplate;
    private final ElasticSearchProperties properties;
    private final ExecutorService executorService;
    private final ExecutorService logExecutorService;
    private final String className;
    private final int indexBatchSize;
    private final int asyncBufferFlushTimeout;
    private final ConcurrentHashMap<Pair<String, Refresh>, BulkIngester<Void>> bulkIngesters;
    private final ConcurrentHashMap<Long, Long> bulkRequestStartTimes;
    private final ScheduledExecutorService bulkScheduler;

    Es8BulkIngestionSupport(
            ElasticsearchClient elasticSearchClient,
            ElasticsearchAsyncClient elasticSearchAsyncClient,
            RetryTemplate retryTemplate,
            ElasticSearchProperties properties,
            ExecutorService executorService,
            ExecutorService logExecutorService,
            String className) {
        this.elasticSearchClient = elasticSearchClient;
        this.elasticSearchAsyncClient = elasticSearchAsyncClient;
        this.retryTemplate = retryTemplate;
        this.properties = properties;
        this.executorService = executorService;
        this.logExecutorService = logExecutorService;
        this.className = className;
        this.indexBatchSize = properties.getIndexBatchSize();
        this.asyncBufferFlushTimeout = (int) properties.getAsyncBufferFlushTimeout().getSeconds();
        this.bulkIngesters = new ConcurrentHashMap<>();
        this.bulkRequestStartTimes = new ConcurrentHashMap<>();
        this.bulkScheduler =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "es8-bulk-flush");
                            thread.setDaemon(true);
                            return thread;
                        });
    }

    void close() {
        bulkIngesters.values().forEach(BulkIngester::close);
        shutdownScheduler();
    }

    void indexObject(String index, String docType, Object doc) {
        indexObject(index, docType, null, doc, null);
    }

    void indexObject(
            String index, String docType, String docId, Object doc, Refresh refreshPolicy) {
        BulkOperation operation =
                BulkOperation.of(
                        op ->
                                op.index(
                                        i -> {
                                            i.index(index).document(doc);
                                            if (docId != null) {
                                                i.id(docId);
                                            }
                                            return i;
                                        }));
        BulkIngester<Void> ingester = getBulkIngester(docType, refreshPolicy);
        ingester.add(operation);
    }

    private BulkIngester<Void> getBulkIngester(String docType, Refresh refreshPolicy) {
        Pair<String, Refresh> requestKey = new ImmutablePair<>(docType, refreshPolicy);
        return bulkIngesters.computeIfAbsent(
                requestKey, key -> createBulkIngester(docType, refreshPolicy));
    }

    private BulkIngester<Void> createBulkIngester(String docType, Refresh refreshPolicy) {
        BulkListener<Void> listener =
                new BulkListener<>() {
                    @Override
                    public void beforeBulk(
                            long executionId, BulkRequest request, List<Void> contexts) {
                        bulkRequestStartTimes.put(executionId, System.currentTimeMillis());
                    }

                    @Override
                    public void afterBulk(
                            long executionId,
                            BulkRequest request,
                            List<Void> contexts,
                            BulkResponse response) {
                        long duration = recordBulkDuration(executionId);
                        if (duration >= 0) {
                            Monitors.recordESIndexTime("index_object", docType, duration);
                        }
                        Monitors.recordWorkerQueueSize(
                                "indexQueue",
                                ((ThreadPoolExecutor) executorService).getQueue().size());
                        Monitors.recordWorkerQueueSize(
                                "logQueue",
                                ((ThreadPoolExecutor) logExecutorService).getQueue().size());
                        if (response.errors()) {
                            List<BulkOperation> failedOperations =
                                    collectFailedOperations(request, response);
                            long errorCount = failedOperations.size();
                            Monitors.error(className, "index");
                            logger.warn(
                                    "Bulk indexing reported {} failures for doc type {} ({} items)",
                                    errorCount,
                                    docType,
                                    response.items().size());
                            retryFailedOperations(docType, failedOperations);
                        }
                    }

                    @Override
                    public void afterBulk(
                            long executionId,
                            BulkRequest request,
                            List<Void> contexts,
                            Throwable failure) {
                        long duration = recordBulkDuration(executionId);
                        if (duration >= 0) {
                            Monitors.recordESIndexTime("index_object", docType, duration);
                        }
                        Monitors.error(className, "index");
                        logger.error("Bulk indexing failed for doc type {}", docType, failure);
                        try {
                            retryTemplate.execute(
                                    context -> {
                                        elasticSearchClient.bulk(request);
                                        return null;
                                    });
                        } catch (Exception retryException) {
                            logger.error(
                                    "Bulk indexing retry failed for doc type {}",
                                    docType,
                                    retryException);
                        }
                        Monitors.recordWorkerQueueSize(
                                "indexQueue",
                                ((ThreadPoolExecutor) executorService).getQueue().size());
                        Monitors.recordWorkerQueueSize(
                                "logQueue",
                                ((ThreadPoolExecutor) logExecutorService).getQueue().size());
                    }
                };

        return BulkIngester.of(
                builder -> {
                    builder.client(elasticSearchAsyncClient);
                    builder.maxOperations(indexBatchSize);
                    builder.maxConcurrentRequests(
                            Math.max(1, Math.min(4, properties.getAsyncMaxPoolSize())));
                    if (asyncBufferFlushTimeout > 0) {
                        builder.flushInterval(
                                asyncBufferFlushTimeout, TimeUnit.SECONDS, bulkScheduler);
                    }
                    builder.listener(listener);
                    if (refreshPolicy != null) {
                        builder.globalSettings(b -> b.refresh(refreshPolicy));
                    }
                    return builder;
                });
    }

    private long recordBulkDuration(long executionId) {
        Long start = bulkRequestStartTimes.remove(executionId);
        if (start == null) {
            return -1L;
        }
        return System.currentTimeMillis() - start;
    }

    static List<BulkOperation> collectFailedOperations(BulkRequest request, BulkResponse response) {
        List<BulkOperation> operations = request.operations();
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }
        List<BulkOperation> failedOperations = new ArrayList<>();
        int itemCount = Math.min(operations.size(), response.items().size());
        for (int i = 0; i < itemCount; i++) {
            if (response.items().get(i).error() != null) {
                failedOperations.add(operations.get(i));
            }
        }
        return failedOperations;
    }

    private void retryFailedOperations(String docType, List<BulkOperation> failedOperations) {
        if (failedOperations.isEmpty()) {
            return;
        }
        try {
            retryTemplate.execute(
                    context -> {
                        elasticSearchClient.bulk(b -> b.operations(failedOperations));
                        return null;
                    });
        } catch (Exception retryException) {
            logger.error(
                    "Bulk indexing retry for failed items failed for doc type {}",
                    docType,
                    retryException);
        }
    }

    private void shutdownScheduler() {
        try {
            bulkScheduler.shutdown();
            if (!bulkScheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                bulkScheduler.shutdownNow();
            }
        } catch (InterruptedException interruptedException) {
            bulkScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
