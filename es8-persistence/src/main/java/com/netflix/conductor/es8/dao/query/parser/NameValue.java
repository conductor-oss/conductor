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
package com.netflix.conductor.es8.dao.query.parser;

import java.io.InputStream;
import java.util.List;

import com.netflix.conductor.es8.dao.query.parser.internal.AbstractNode;
import com.netflix.conductor.es8.dao.query.parser.internal.ComparisonOp;
import com.netflix.conductor.es8.dao.query.parser.internal.ComparisonOp.Operators;
import com.netflix.conductor.es8.dao.query.parser.internal.ConstValue;
import com.netflix.conductor.es8.dao.query.parser.internal.ListConst;
import com.netflix.conductor.es8.dao.query.parser.internal.Name;
import com.netflix.conductor.es8.dao.query.parser.internal.ParserException;
import com.netflix.conductor.es8.dao.query.parser.internal.Range;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;

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
    public Query getFilterBuilder() {
        if (op.getOperator().equals(Operators.EQUALS.value())) {
            return queryString(name.getName() + ":" + value.getValue().toString());
        } else if (op.getOperator().equals(Operators.BETWEEN.value())) {
            return Query.of(
                    q ->
                            q.range(
                                    r ->
                                            r.untyped(
                                                    u ->
                                                            u.field(name.getName())
                                                                    .gte(JsonData.of(range.getLow()))
                                                                    .lte(
                                                                            JsonData.of(
                                                                                    range.getHigh())))));
        } else if (op.getOperator().equals(Operators.IN.value())) {
            List<FieldValue> values =
                    valueList.getList().stream().map(val -> FieldValue.of(val.toString())).toList();
            return Query.of(
                    q -> q.terms(t -> t.field(name.getName()).terms(tf -> tf.value(values))));
        } else if (op.getOperator().equals(Operators.NOT_EQUALS.value())) {
            Query query = queryString(name.getName() + ":" + value.getValue().toString());
            return Query.of(q -> q.bool(b -> b.mustNot(query)));
        } else if (op.getOperator().equals(Operators.GREATER_THAN.value())) {
            return Query.of(
                    q ->
                            q.range(
                                    r ->
                                            r.untyped(
                                                    u ->
                                                            u.field(name.getName())
                                                                    .gt(
                                                                            JsonData.of(
                                                                                    value
                                                                                            .getValue())))));
        } else if (op.getOperator().equals(Operators.IS.value())) {
            if (value.getSysConstant().equals(ConstValue.SystemConsts.NULL)) {
                return Query.of(
                        q ->
                                q.bool(
                                        b ->
                                                b.mustNot(
                                                        Query.of(
                                                                exists ->
                                                                        exists.exists(
                                                                                e ->
                                                                                        e.field(
                                                                                                name
                                                                                                        .getName()))))));
            } else if (value.getSysConstant().equals(ConstValue.SystemConsts.NOT_NULL)) {
                return Query.of(q -> q.exists(e -> e.field(name.getName())));
            }
        } else if (op.getOperator().equals(Operators.LESS_THAN.value())) {
            return Query.of(
                    q ->
                            q.range(
                                    r ->
                                            r.untyped(
                                                    u ->
                                                            u.field(name.getName())
                                                                    .lt(
                                                                            JsonData.of(
                                                                                    value
                                                                                            .getValue())))));
        } else if (op.getOperator().equals(Operators.STARTS_WITH.value())) {
            return Query.of(
                    q -> q.prefix(p -> p.field(name.getName()).value(value.getUnquotedValue())));
        }

        throw new IllegalStateException("Incorrect/unsupported operators");
    }

    private Query queryString(String query) {
        return Query.of(q -> q.queryString(qs -> qs.query(query)));
    }
}
