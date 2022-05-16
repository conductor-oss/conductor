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
package com.netflix.conductor.es6.dao.query.parser;

import java.io.InputStream;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.netflix.conductor.es6.dao.query.parser.internal.AbstractNode;
import com.netflix.conductor.es6.dao.query.parser.internal.ComparisonOp;
import com.netflix.conductor.es6.dao.query.parser.internal.ComparisonOp.Operators;
import com.netflix.conductor.es6.dao.query.parser.internal.ConstValue;
import com.netflix.conductor.es6.dao.query.parser.internal.ListConst;
import com.netflix.conductor.es6.dao.query.parser.internal.Name;
import com.netflix.conductor.es6.dao.query.parser.internal.ParserException;
import com.netflix.conductor.es6.dao.query.parser.internal.Range;

/**
 *
 *
 * <pre>
 * Represents an expression of the form as below:
 * key OPR value
 * OPR is the comparison operator which could be one of the following:
 * 	&gt;, &lt;, = , !=, IN, BETWEEN
 * </pre>
 */
public class NameValue extends AbstractNode implements FilterProvider {

    private Name name;

    private ComparisonOp op;

    private ConstValue value;

    private Range range;

    private ListConst valueList;

    public NameValue(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        this.name = new Name(is);
        this.op = new ComparisonOp(is);

        if (this.op.getOperator().equals(Operators.BETWEEN.value())) {
            this.range = new Range(is);
        }
        if (this.op.getOperator().equals(Operators.IN.value())) {
            this.valueList = new ListConst(is);
        } else {
            this.value = new ConstValue(is);
        }
    }

    @Override
    public String toString() {
        return "" + name + op + value;
    }

    /**
     * @return the name
     */
    public Name getName() {
        return name;
    }

    /**
     * @return the op
     */
    public ComparisonOp getOp() {
        return op;
    }

    /**
     * @return the value
     */
    public ConstValue getValue() {
        return value;
    }

    @Override
    public QueryBuilder getFilterBuilder() {
        if (op.getOperator().equals(Operators.EQUALS.value())) {
            return QueryBuilders.queryStringQuery(
                    name.getName() + ":" + value.getValue().toString());
        } else if (op.getOperator().equals(Operators.BETWEEN.value())) {
            return QueryBuilders.rangeQuery(name.getName())
                    .from(range.getLow())
                    .to(range.getHigh());
        } else if (op.getOperator().equals(Operators.IN.value())) {
            return QueryBuilders.termsQuery(name.getName(), valueList.getList());
        } else if (op.getOperator().equals(Operators.NOT_EQUALS.value())) {
            return QueryBuilders.queryStringQuery(
                    "NOT " + name.getName() + ":" + value.getValue().toString());
        } else if (op.getOperator().equals(Operators.GREATER_THAN.value())) {
            return QueryBuilders.rangeQuery(name.getName())
                    .from(value.getValue())
                    .includeLower(false)
                    .includeUpper(false);
        } else if (op.getOperator().equals(Operators.IS.value())) {
            if (value.getSysConstant().equals(ConstValue.SystemConsts.NULL)) {
                return QueryBuilders.boolQuery()
                        .mustNot(
                                QueryBuilders.boolQuery()
                                        .must(QueryBuilders.matchAllQuery())
                                        .mustNot(QueryBuilders.existsQuery(name.getName())));
            } else if (value.getSysConstant().equals(ConstValue.SystemConsts.NOT_NULL)) {
                return QueryBuilders.boolQuery()
                        .mustNot(
                                QueryBuilders.boolQuery()
                                        .must(QueryBuilders.matchAllQuery())
                                        .must(QueryBuilders.existsQuery(name.getName())));
            }
        } else if (op.getOperator().equals(Operators.LESS_THAN.value())) {
            return QueryBuilders.rangeQuery(name.getName())
                    .to(value.getValue())
                    .includeLower(false)
                    .includeUpper(false);
        } else if (op.getOperator().equals(Operators.STARTS_WITH.value())) {
            return QueryBuilders.prefixQuery(name.getName(), value.getUnquotedValue());
        }

        throw new IllegalStateException("Incorrect/unsupported operators");
    }
}
