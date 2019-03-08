package com.netflix.conductor.dao.es6.index;

import com.netflix.conductor.dao.IndexDAO;
import com.netflix.conductor.dao.es6.index.query.parser.Expression;
import com.netflix.conductor.elasticsearch.query.parser.ParserException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class ElasticSearchBaseDAO implements IndexDAO {

    String indexPrefix;

    String loadTypeMappingSource(String path) throws IOException {
        return applyIndexPrefixToTemplate(IOUtils.toString(ElasticSearchBaseDAO.class.getResourceAsStream(path)));
    }

    private String applyIndexPrefixToTemplate(String text) {
        String pattern = "\"template\": \"\\*(.*)\\*\"";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, m.group(0).replaceFirst(Pattern.quote(m.group(1)), indexPrefix + "_" + m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString();
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

    String indexName(String documentType) {
        return indexPrefix + "_" + documentType;
    }

}
