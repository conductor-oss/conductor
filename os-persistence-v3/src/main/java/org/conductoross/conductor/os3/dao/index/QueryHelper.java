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
package org.conductoross.conductor.os3.dao.index;

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.json.JsonData;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class for building OpenSearch queries using the opensearch-java 3.x client API.
 *
 * This class provides utility methods to construct queries using the new functional builder pattern
 * introduced in opensearch-java 3.x, replacing the imperative QueryBuilder API from the
 * High-Level REST Client.
 */
public class QueryHelper {

    /**
     * Creates a match query for a single field.
     *
     * @param field The field name to match against
     * @param value The value to match
     * @return A Query object configured for match query
     */
    public static Query matchQuery(String field, String value) {
        return Query.of(q -> q
            .match(m -> m
                .field(field)
                .query(FieldValue.of(value))
            )
        );
    }

    /**
     * Creates a term query for exact matching.
     *
     * @param field The field name
     * @param value The exact value to match
     * @return A Query object configured for term query
     */
    public static Query termQuery(String field, String value) {
        return Query.of(q -> q
            .term(t -> t
                .field(field)
                .value(FieldValue.of(value))
            )
        );
    }

    /**
     * Creates a range query for numeric or date fields.
     *
     * @param field The field name
     * @return A partial RangeQueryBuilder for chaining gte/lte/gt/lt calls
     */
    public static RangeQueryBuilder rangeQuery(String field) {
        return new RangeQueryBuilder(field);
    }

    /**
     * Helper class for building range queries with a fluent API.
     */
    public static class RangeQueryBuilder {
        private final String field;
        private JsonData gte;
        private JsonData lte;
        private JsonData gt;
        private JsonData lt;

        private RangeQueryBuilder(String field) {
            this.field = field;
        }

        public RangeQueryBuilder gte(Object value) {
            this.gte = JsonData.of(value);
            return this;
        }

        public RangeQueryBuilder lte(Object value) {
            this.lte = JsonData.of(value);
            return this;
        }

        public RangeQueryBuilder gt(Object value) {
            this.gt = JsonData.of(value);
            return this;
        }

        public RangeQueryBuilder lt(Object value) {
            this.lt = JsonData.of(value);
            return this;
        }

        public Query build() {
            return Query.of(q -> q
                .range(r -> {
                    r.field(field);
                    if (gte != null) r.gte(gte);
                    if (lte != null) r.lte(lte);
                    if (gt != null) r.gt(gt);
                    if (lt != null) r.lt(lt);
                    return r;
                })
            );
        }
    }

    /**
     * Creates a query string query for full-text search.
     *
     * @param queryString The query string
     * @return A Query object configured for query string search
     */
    public static Query queryStringQuery(String queryString) {
        return Query.of(q -> q
            .queryString(qs -> qs
                .query(queryString)
            )
        );
    }

    /**
     * Creates an exists query to check for field presence.
     *
     * @param field The field name to check existence
     * @return A Query object configured for exists query
     */
    public static Query existsQuery(String field) {
        return Query.of(q -> q
            .exists(e -> e.field(field))
        );
    }

    /**
     * Creates a match-all query.
     *
     * @return A Query object that matches all documents
     */
    public static Query matchAllQuery() {
        return Query.of(q -> q.matchAll(m -> m));
    }

    /**
     * Helper class for building boolean queries with must/should/filter/mustNot clauses.
     */
    public static class BoolQueryBuilder {
        private final BoolQuery.Builder builder = new BoolQuery.Builder();
        private int minimumShouldMatch = 0;

        public BoolQueryBuilder must(Query query) {
            builder.must(query);
            return this;
        }

        public BoolQueryBuilder should(Query query) {
            builder.should(query);
            return this;
        }

        public BoolQueryBuilder filter(Query query) {
            builder.filter(query);
            return this;
        }

        public BoolQueryBuilder mustNot(Query query) {
            builder.mustNot(query);
            return this;
        }

        public BoolQueryBuilder minimumShouldMatch(int minimum) {
            this.minimumShouldMatch = minimum;
            return this;
        }

        public Query build() {
            if (minimumShouldMatch > 0) {
                builder.minimumShouldMatch(String.valueOf(minimumShouldMatch));
            }
            return Query.of(q -> q.bool(builder.build()));
        }
    }

    /**
     * Creates a new boolean query builder.
     *
     * @return A new BoolQueryBuilder for constructing complex boolean queries
     */
    public static BoolQueryBuilder boolQuery() {
        return new BoolQueryBuilder();
    }
}
