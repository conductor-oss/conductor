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
package com.netflix.conductor.es8.dao.index;

import org.apache.commons.lang3.StringUtils;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.es8.dao.query.parser.Expression;
import com.netflix.conductor.es8.dao.query.parser.internal.ParserException;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class ElasticSearchBaseDAO implements IndexDAO {

    String indexPrefix;
    ObjectMapper objectMapper;

    Query boolQueryBuilder(String expression, String queryString) throws ParserException {
        Query queryBuilder = Query.of(q -> q.matchAll(m -> m));
        if (StringUtils.isNotEmpty(expression)) {
            Expression exp = Expression.fromString(expression);
            queryBuilder = exp.getFilterBuilder();
        }
        Query baseQuery = queryBuilder;
        Query filterQuery = Query.of(q -> q.bool(b -> b.must(baseQuery)));
        Query stringQuery = Query.of(q -> q.simpleQueryString(qs -> qs.query(queryString)));
        return Query.of(q -> q.bool(b -> b.must(stringQuery).must(filterQuery)));
    }

    protected String getIndexName(String documentType) {
        if (StringUtils.isBlank(indexPrefix)) {
            return documentType;
        }
        if (indexPrefix.endsWith("_")) {
            return indexPrefix + documentType;
        }
        return indexPrefix + "_" + documentType;
    }
}
