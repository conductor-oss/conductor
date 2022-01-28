/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.es6.dao.index;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.es6.dao.query.parser.Expression;
import com.netflix.conductor.es6.dao.query.parser.internal.ParserException;

abstract class ElasticSearchBaseDAO implements IndexDAO {

    String indexPrefix;

    String loadTypeMappingSource(String path) throws IOException {
        return applyIndexPrefixToTemplate(
                IOUtils.toString(ElasticSearchBaseDAO.class.getResourceAsStream(path)));
    }

    private String applyIndexPrefixToTemplate(String text) {
        String pattern = "\"template\": \"\\*(.*)\\*\"";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(
                    sb,
                    m.group(0)
                            .replaceFirst(
                                    Pattern.quote(m.group(1)), indexPrefix + "_" + m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    BoolQueryBuilder boolQueryBuilder(String expression, String queryString)
            throws ParserException {
        QueryBuilder queryBuilder = QueryBuilders.matchAllQuery();
        if (StringUtils.isNotEmpty(expression)) {
            Expression exp = Expression.fromString(expression);
            queryBuilder = exp.getFilterBuilder();
        }
        BoolQueryBuilder filterQuery = QueryBuilders.boolQuery().must(queryBuilder);
        QueryStringQueryBuilder stringQuery = QueryBuilders.queryStringQuery(queryString);
        return QueryBuilders.boolQuery().must(stringQuery).must(filterQuery);
    }

    protected String getIndexName(String documentType) {
        return indexPrefix + "_" + documentType;
    }
}
