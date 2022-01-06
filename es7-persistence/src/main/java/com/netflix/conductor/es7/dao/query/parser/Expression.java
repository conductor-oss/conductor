/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.es7.dao.query.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;

import com.netflix.conductor.es7.dao.query.parser.internal.AbstractNode;
import com.netflix.conductor.es7.dao.query.parser.internal.BooleanOp;
import com.netflix.conductor.es7.dao.query.parser.internal.ParserException;

/** @author Viren */
public class Expression extends AbstractNode implements FilterProvider {

    private NameValue nameVal;

    private GroupedExpression ge;

    private BooleanOp op;

    private Expression rhs;

    public Expression(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] peeked = peek(1);

        if (peeked[0] == '(') {
            this.ge = new GroupedExpression(is);
        } else {
            this.nameVal = new NameValue(is);
        }

        peeked = peek(3);
        if (isBoolOpr(peeked)) {
            // we have an expression next
            this.op = new BooleanOp(is);
            this.rhs = new Expression(is);
        }
    }

    public boolean isBinaryExpr() {
        return this.op != null;
    }

    public BooleanOp getOperator() {
        return this.op;
    }

    public Expression getRightHandSide() {
        return this.rhs;
    }

    public boolean isNameValue() {
        return this.nameVal != null;
    }

    public NameValue getNameValue() {
        return this.nameVal;
    }

    public GroupedExpression getGroupedExpression() {
        return this.ge;
    }

    @Override
    public QueryBuilder getFilterBuilder() {
        QueryBuilder lhs = null;
        if (nameVal != null) {
            lhs = nameVal.getFilterBuilder();
        } else {
            lhs = ge.getFilterBuilder();
        }

        if (this.isBinaryExpr()) {
            QueryBuilder rhsFilter = rhs.getFilterBuilder();
            if (this.op.isAnd()) {
                return QueryBuilders.boolQuery().must(lhs).must(rhsFilter);
            } else {
                return QueryBuilders.boolQuery().should(lhs).should(rhsFilter);
            }
        } else {
            return lhs;
        }
    }

    @Override
    public String toString() {
        if (isBinaryExpr()) {
            return "" + (nameVal == null ? ge : nameVal) + op + rhs;
        } else {
            return "" + (nameVal == null ? ge : nameVal);
        }
    }

    public static Expression fromString(String value) throws ParserException {
        return new Expression(new BufferedInputStream(new ByteArrayInputStream(value.getBytes())));
    }
}
