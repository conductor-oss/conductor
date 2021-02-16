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
package com.netflix.conductor.dao.es7.index;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.netflix.conductor.common.utils.JsonMapperProvider;
import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.es7.index.query.parser.Expression;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

abstract class ElasticSearchBaseDAO implements IndexDAO {

    String indexPrefix;

    String loadTypeMappingSource(String path) throws IOException {
        return applyIndexPrefixToTemplate(IOUtils.toString(ElasticSearchBaseDAO.class.getResourceAsStream(path)));
    }

    private String applyIndexPrefixToTemplate(String text) throws JsonProcessingException {
        ObjectMapper mapper = new JsonMapperProvider().get();
        String indexPatternsFieldName = "index_patterns";
        JsonNode root = mapper.readTree(text);
        if (root != null) {
            JsonNode indexPatternsNodeValue = root.get(indexPatternsFieldName);
            if (indexPatternsNodeValue != null && indexPatternsNodeValue.isArray()) {
                ArrayList<String> patternsWithPrefix = new ArrayList<>();
                indexPatternsNodeValue.forEach(v -> {
                    String patternText = v.asText();
                    StringBuffer sb = new StringBuffer();
                    if (patternText.startsWith("*")) {
                        sb.append("*").append(indexPrefix).append("_").append(patternText.substring(1));
                    } else {
                        sb.append(indexPrefix).append("_").append(patternText);
                    }
                    patternsWithPrefix.add(sb.toString());
                });
                ((ObjectNode) root).set(indexPatternsFieldName, mapper.valueToTree(patternsWithPrefix));
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            }
        }
        return text;
    }

    BoolQueryBuilder boolQueryBuilder(String expression, String queryString) throws ParserException {
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
