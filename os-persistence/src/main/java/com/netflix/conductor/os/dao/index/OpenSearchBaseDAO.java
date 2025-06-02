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
package com.netflix.conductor.os.dao.index;

import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.QueryStringQueryBuilder;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.os.dao.query.parser.Expression;
import com.netflix.conductor.os.dao.query.parser.internal.ParserException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

abstract class OpenSearchBaseDAO implements IndexDAO {

    String indexPrefix;
    ObjectMapper objectMapper;

    String loadTypeMappingSource(String path) throws IOException {
        return applyIndexPrefixToTemplate(
                IOUtils.toString(OpenSearchBaseDAO.class.getResourceAsStream(path)));
    }

    private String applyIndexPrefixToTemplate(String text) throws JsonProcessingException {
        String indexPatternsFieldName = "index_patterns";
        JsonNode root = objectMapper.readTree(text);
        if (root != null) {
            JsonNode indexPatternsNodeValue = root.get(indexPatternsFieldName);
            if (indexPatternsNodeValue != null && indexPatternsNodeValue.isArray()) {
                ArrayList<String> patternsWithPrefix = new ArrayList<>();
                indexPatternsNodeValue.forEach(
                        v -> {
                            String patternText = v.asText();
                            StringBuilder sb = new StringBuilder();
                            if (patternText.startsWith("*")) {
                                sb.append("*")
                                        .append(indexPrefix)
                                        .append("_")
                                        .append(patternText.substring(1));
                            } else {
                                sb.append(indexPrefix).append("_").append(patternText);
                            }
                            patternsWithPrefix.add(sb.toString());
                        });
                ((ObjectNode) root)
                        .set(indexPatternsFieldName, objectMapper.valueToTree(patternsWithPrefix));
                System.out.println(
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
        }
        return text;
    }

    org.opensearch.index.query.BoolQueryBuilder boolQueryBuilder(
            String expression, String queryString) throws ParserException {
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
