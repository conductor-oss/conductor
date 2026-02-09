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
package org.conductoross.conductor.os3.dao.query.parser;

import java.io.InputStream;

import org.conductoross.conductor.os3.dao.query.parser.internal.*;
import org.conductoross.conductor.os3.dao.query.parser.internal.ComparisonOp.Operators;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.query_dsl.Query;

/**
 * @author Viren
 *     <pre>
 * Represents an expression of the form as below:
 * key OPR value
 * OPR is the comparison operator which could be on the following:
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
    public Query getFilter() {
        if (op.getOperator().equals(Operators.EQUALS.value())) {
            String queryStr = name.getName() + ":" + value.getValue().toString();
            return Query.of(q -> q.queryString(qs -> qs.query(queryStr)));
        } else if (op.getOperator().equals(Operators.BETWEEN.value())) {
            String fieldName = name.getName();
            return Query.of(
                    q ->
                            q.range(
                                    r ->
                                            r.field(fieldName)
                                                    .from(JsonData.of(range.getLow()))
                                                    .to(JsonData.of(range.getHigh()))));
        } else if (op.getOperator().equals(Operators.IN.value())) {
            String fieldName = name.getName();
            return Query.of(
                    q ->
                            q.terms(
                                    t ->
                                            t.field(fieldName)
                                                    .terms(
                                                            tv ->
                                                                    tv.value(
                                                                            valueList
                                                                                    .getList()
                                                                                    .stream()
                                                                                    .map(
                                                                                            v ->
                                                                                                    org
                                                                                                            .opensearch
                                                                                                            .client
                                                                                                            .opensearch
                                                                                                            ._types
                                                                                                            .FieldValue
                                                                                                            .of(
                                                                                                                    v
                                                                                                                            .toString()))
                                                                                    .collect(
                                                                                            java
                                                                                                    .util
                                                                                                    .stream
                                                                                                    .Collectors
                                                                                                    .toList())))));
        } else if (op.getOperator().equals(Operators.NOT_EQUALS.value())) {
            String queryStr = "NOT " + name.getName() + ":" + value.getValue().toString();
            return Query.of(q -> q.queryString(qs -> qs.query(queryStr)));
        } else if (op.getOperator().equals(Operators.GREATER_THAN.value())) {
            String fieldName = name.getName();
            return Query.of(
                    q -> q.range(r -> r.field(fieldName).gt(JsonData.of(value.getValue()))));
        } else if (op.getOperator().equals(Operators.IS.value())) {
            if (value.getSysConstant().equals(ConstValue.SystemConsts.NULL)) {
                String fieldName = name.getName();
                return Query.of(
                        q ->
                                q.bool(
                                        b ->
                                                b.mustNot(
                                                        Query.of(
                                                                q2 ->
                                                                        q2.bool(
                                                                                b2 ->
                                                                                        b2.must(
                                                                                                        Query
                                                                                                                .of(
                                                                                                                        q3 ->
                                                                                                                                q3
                                                                                                                                        .matchAll(
                                                                                                                                                m ->
                                                                                                                                                        m)))
                                                                                                .mustNot(
                                                                                                        Query
                                                                                                                .of(
                                                                                                                        q4 ->
                                                                                                                                q4
                                                                                                                                        .exists(
                                                                                                                                                e ->
                                                                                                                                                        e
                                                                                                                                                                .field(
                                                                                                                                                                        fieldName)))))))));
            } else if (value.getSysConstant().equals(ConstValue.SystemConsts.NOT_NULL)) {
                String fieldName = name.getName();
                return Query.of(
                        q ->
                                q.bool(
                                        b ->
                                                b.mustNot(
                                                        Query.of(
                                                                q2 ->
                                                                        q2.bool(
                                                                                b2 ->
                                                                                        b2.must(
                                                                                                        Query
                                                                                                                .of(
                                                                                                                        q3 ->
                                                                                                                                q3
                                                                                                                                        .matchAll(
                                                                                                                                                m ->
                                                                                                                                                        m)))
                                                                                                .must(
                                                                                                        Query
                                                                                                                .of(
                                                                                                                        q4 ->
                                                                                                                                q4
                                                                                                                                        .exists(
                                                                                                                                                e ->
                                                                                                                                                        e
                                                                                                                                                                .field(
                                                                                                                                                                        fieldName)))))))));
            }
        } else if (op.getOperator().equals(Operators.LESS_THAN.value())) {
            String fieldName = name.getName();
            return Query.of(
                    q -> q.range(r -> r.field(fieldName).lt(JsonData.of(value.getValue()))));
        } else if (op.getOperator().equals(Operators.STARTS_WITH.value())) {
            String fieldName = name.getName();
            String prefix = value.getUnquotedValue();
            return Query.of(q -> q.prefix(p -> p.field(fieldName).value(prefix)));
        }

        throw new IllegalStateException("Incorrect/unsupported operators");
    }
}
