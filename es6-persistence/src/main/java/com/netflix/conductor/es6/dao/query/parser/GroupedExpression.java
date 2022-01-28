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

import com.netflix.conductor.es6.dao.query.parser.internal.AbstractNode;
import com.netflix.conductor.es6.dao.query.parser.internal.ParserException;

public class GroupedExpression extends AbstractNode implements FilterProvider {

    private Expression expression;

    public GroupedExpression(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] peeked = read(1);
        assertExpected(peeked, "(");

        this.expression = new Expression(is);

        peeked = read(1);
        assertExpected(peeked, ")");
    }

    @Override
    public String toString() {
        return "(" + expression + ")";
    }

    /** @return the expression */
    public Expression getExpression() {
        return expression;
    }

    @Override
    public QueryBuilder getFilterBuilder() {
        return expression.getFilterBuilder();
    }
}
