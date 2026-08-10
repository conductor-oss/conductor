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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.conductoross.conductor.es8.dao.query.parser.Expression;
import org.conductoross.conductor.es8.dao.query.parser.internal.ParserException;

import com.netflix.conductor.common.run.SearchResult;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.json.JsonData;

/** Handles query parsing and search operations used by ES8 index DAO. */
class Es8SearchSupport {

    private final ElasticsearchClient elasticSearchClient;
    private final String indexPrefix;

    Es8SearchSupport(ElasticsearchClient elasticSearchClient, String indexPrefix) {
        this.elasticSearchClient = elasticSearchClient;
        this.indexPrefix = indexPrefix;
    }

    Query boolQueryBuilder(String expression, String queryString) throws ParserException {
        Query queryBuilder = Query.of(q -> q.matchAll(m -> m));
        if (StringUtils.isNotEmpty(expression)) {
            Expression exp = Expression.fromString(expression);
            queryBuilder = exp.getFilterBuilder();
        }

        if (StringUtils.isBlank(queryString) || "*".equals(queryString.trim())) {
            return queryBuilder;
        }

        Query stringQuery = Query.of(q -> q.simpleQueryString(qs -> qs.query(queryString)));
        Query baseQuery = queryBuilder;
        return Query.of(q -> q.bool(b -> b.must(stringQuery).must(baseQuery)));
    }

    <T> SearchResult<T> searchObjectsViaExpression(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType,
            boolean idOnly,
            Class<T> clazz)
            throws ParserException, IOException {
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjects(
                getIndexName(docType), queryBuilder, start, size, sortOptions, idOnly, clazz);
    }

    SearchResult<String> searchObjectIdsViaExpression(
            String structuredQuery,
            int start,
            int size,
            List<String> sortOptions,
            String freeTextQuery,
            String docType)
            throws ParserException, IOException {
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        return searchObjectIds(getIndexName(docType), queryBuilder, start, size, sortOptions);
    }

    SearchResult<String> searchObjectIds(String indexName, Query queryBuilder, int start, int size)
            throws IOException {
        return searchObjectIds(indexName, queryBuilder, start, size, null);
    }

    SearchResult<String> searchObjectIds(
            String indexName, Query queryBuilder, int start, int size, List<String> sortOptions)
            throws IOException {
        List<SortOptions> sort = buildSortOptions(sortOptions);
        SearchResponse<Void> response =
                elasticSearchClient.search(
                        s -> {
                            s.index(indexName)
                                    .query(queryBuilder)
                                    .from(start)
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true))
                                    .source(src -> src.fetch(false));
                            if (!sort.isEmpty()) {
                                s.sort(sort);
                            }
                            return s;
                        },
                        Void.class);
        List<String> result =
                response.hits().hits().stream().map(hit -> hit.id()).collect(Collectors.toList());
        long count = totalHits(response);
        return new SearchResult<>(count, result);
    }

    <T> SearchResult<T> searchObjects(
            String indexName,
            Query queryBuilder,
            int start,
            int size,
            List<String> sortOptions,
            boolean idOnly,
            Class<T> clazz)
            throws IOException {
        List<SortOptions> sort = buildSortOptions(sortOptions);
        SearchResponse<T> response =
                elasticSearchClient.search(
                        s -> {
                            s.index(indexName)
                                    .query(queryBuilder)
                                    .from(start)
                                    .size(size)
                                    .trackTotalHits(t -> t.enabled(true));
                            if (idOnly) {
                                s.source(src -> src.fetch(false));
                            }
                            if (!sort.isEmpty()) {
                                s.sort(sort);
                            }
                            return s;
                        },
                        clazz);
        return mapSearchResult(response, idOnly, clazz);
    }

    long getObjectCounts(String structuredQuery, String freeTextQuery, String docType)
            throws ParserException, IOException {
        Query queryBuilder = boolQueryBuilder(structuredQuery, freeTextQuery);
        String indexName = getIndexName(docType);
        CountResponse countResponse =
                elasticSearchClient.count(c -> c.index(indexName).query(queryBuilder));
        return countResponse.count();
    }

    List<String> searchArchivableWorkflows(String indexName, long archiveTtlDays)
            throws IOException {
        String archiveTo = LocalDate.now().minusDays(archiveTtlDays).toString();
        String archiveFrom = LocalDate.now().minusDays(archiveTtlDays).minusDays(1).toString();

        Query endTimeRangeQuery =
                Query.of(
                        q ->
                                q.range(
                                        r ->
                                                r.untyped(
                                                        u ->
                                                                u.field("endTime")
                                                                        .lt(JsonData.of(archiveTo))
                                                                        .gte(
                                                                                JsonData.of(
                                                                                        archiveFrom)))));
        Query completedQuery = Query.of(q -> q.term(t -> t.field("status").value("COMPLETED")));
        Query failedQuery = Query.of(q -> q.term(t -> t.field("status").value("FAILED")));
        Query timedOutQuery = Query.of(q -> q.term(t -> t.field("status").value("TIMED_OUT")));
        Query terminatedQuery = Query.of(q -> q.term(t -> t.field("status").value("TERMINATED")));
        Query notArchivedQuery = Query.of(q -> q.exists(e -> e.field("archived")));

        Query query =
                Query.of(
                        q ->
                                q.bool(
                                        b ->
                                                b.must(endTimeRangeQuery)
                                                        .should(completedQuery)
                                                        .should(failedQuery)
                                                        .should(timedOutQuery)
                                                        .should(terminatedQuery)
                                                        .mustNot(notArchivedQuery)
                                                        .minimumShouldMatch("1")));
        SearchResult<String> workflowIds = searchObjectIds(indexName, query, 0, 1000);
        return workflowIds.getResults();
    }

    List<String> searchRecentRunningWorkflows(
            String workflowIndexName, int lastModifiedHoursAgoFrom, int lastModifiedHoursAgoTo)
            throws IOException {
        Instant now = Instant.now();
        long fromMillis = now.minus(Duration.ofHours(lastModifiedHoursAgoFrom)).toEpochMilli();
        long toMillis = now.minus(Duration.ofHours(lastModifiedHoursAgoTo)).toEpochMilli();

        Query gtUpdateTimeQuery =
                Query.of(
                        q ->
                                q.range(
                                        r ->
                                                r.untyped(
                                                        u ->
                                                                u.field("updateTime")
                                                                        .gt(
                                                                                JsonData.of(
                                                                                        fromMillis)))));
        Query ltUpdateTimeQuery =
                Query.of(
                        q ->
                                q.range(
                                        r ->
                                                r.untyped(
                                                        u ->
                                                                u.field("updateTime")
                                                                        .lt(
                                                                                JsonData.of(
                                                                                        toMillis)))));
        Query runningStatusQuery = Query.of(q -> q.term(t -> t.field("status").value("RUNNING")));

        Query query =
                Query.of(
                        q ->
                                q.bool(
                                        b ->
                                                b.must(gtUpdateTimeQuery)
                                                        .must(ltUpdateTimeQuery)
                                                        .must(runningStatusQuery)));
        SearchResult<String> workflowIds =
                searchObjectIds(
                        workflowIndexName,
                        query,
                        0,
                        5000,
                        Collections.singletonList("updateTime:ASC"));
        return workflowIds.getResults();
    }

    private <T> SearchResult<T> mapSearchResult(
            SearchResponse<T> response, boolean idOnly, Class<T> clazz) {
        long count = totalHits(response);
        List<T> result;
        if (idOnly) {
            result =
                    response.hits().hits().stream()
                            .map(hit -> clazz.cast(hit.id()))
                            .collect(Collectors.toList());
        } else {
            result =
                    response.hits().hits().stream()
                            .map(hit -> hit.source())
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
        }
        return new SearchResult<>(count, result);
    }

    private List<SortOptions> buildSortOptions(List<String> sortOptions) {
        if (sortOptions == null || sortOptions.isEmpty()) {
            return Collections.emptyList();
        }
        List<SortOptions> options = new ArrayList<>();
        for (String sortOption : sortOptions) {
            SortOrder order = SortOrder.Asc;
            String field = sortOption;
            int index = sortOption.indexOf(":");
            if (index > 0) {
                field = sortOption.substring(0, index);
                String orderValue = sortOption.substring(index + 1).trim().toUpperCase(Locale.ROOT);
                if ("DESC".equals(orderValue)) {
                    order = SortOrder.Desc;
                }
            }
            String sortField = field;
            SortOrder sortOrder = order;
            options.add(SortOptions.of(s -> s.field(f -> f.field(sortField).order(sortOrder))));
        }
        return options;
    }

    private long totalHits(SearchResponse<?> response) {
        if (response.hits().total() != null) {
            return response.hits().total().value();
        }
        return response.hits().hits().size();
    }

    private String getIndexName(String documentType) {
        if (StringUtils.isBlank(indexPrefix)) {
            return documentType;
        }
        if (indexPrefix.endsWith("_")) {
            return indexPrefix + documentType;
        }
        return indexPrefix + "_" + documentType;
    }
}
