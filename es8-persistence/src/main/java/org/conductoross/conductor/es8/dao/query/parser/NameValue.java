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
package org.conductoross.conductor.es8.dao.query.parser;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import org.conductoross.conductor.es8.dao.query.parser.internal.AbstractNode;
import org.conductoross.conductor.es8.dao.query.parser.internal.ComparisonOp;
import org.conductoross.conductor.es8.dao.query.parser.internal.ComparisonOp.Operators;
import org.conductoross.conductor.es8.dao.query.parser.internal.ConstValue;
import org.conductoross.conductor.es8.dao.query.parser.internal.ListConst;
import org.conductoross.conductor.es8.dao.query.parser.internal.Name;
import org.conductoross.conductor.es8.dao.query.parser.internal.ParserException;
import org.conductoross.conductor.es8.dao.query.parser.internal.Range;

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
            if (value.isSysConstant()) {
                return systemConstantQuery(value.getSysConstant(), true);
            }
            return Query.of(
                    q ->
                            q.term(
                                    t ->
                                            t.field(name.getName())
                                                    .value(toFieldValue(value.toString()))));
        } else if (op.getOperator().equals(Operators.BETWEEN.value())) {
            return Query.of(
                    q ->
                            q.range(
                                    r ->
                                            r.untyped(
                                                    u ->
                                                            u.field(name.getName())
                                                                    .gte(
                                                                            JsonData.of(
                                                                                    range.getLow()))
                                                                    .lte(
                                                                            JsonData.of(
                                                                                    range
                                                                                            .getHigh())))));
        } else if (op.getOperator().equals(Operators.IN.value())) {
            List<FieldValue> values =
                    valueList.getList().stream()
                            .map(value -> toFieldValue(String.valueOf(value)))
                            .toList();
            return Query.of(
                    q -> q.terms(t -> t.field(name.getName()).terms(tf -> tf.value(values))));
        } else if (op.getOperator().equals(Operators.NOT_EQUALS.value())) {
            if (value.isSysConstant()) {
                return systemConstantQuery(value.getSysConstant(), false);
            }
            Query query =
                    Query.of(
                            q ->
                                    q.term(
                                            t ->
                                                    t.field(name.getName())
                                                            .value(
                                                                    toFieldValue(
                                                                            value.toString()))));
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
            return systemConstantQuery(value.getSysConstant(), true);
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

    private Query systemConstantQuery(ConstValue.SystemConsts sysConst, boolean isEqualityCheck) {
        if (sysConst == ConstValue.SystemConsts.NULL) {
            return isEqualityCheck ? missingFieldQuery() : existsFieldQuery();
        }
        if (sysConst == ConstValue.SystemConsts.NOT_NULL) {
            return isEqualityCheck ? existsFieldQuery() : missingFieldQuery();
        }
        throw new IllegalStateException("Unsupported system constant: " + sysConst);
    }

    private Query existsFieldQuery() {
        return Query.of(q -> q.exists(e -> e.field(name.getName())));
    }

    private Query missingFieldQuery() {
        return Query.of(q -> q.bool(b -> b.mustNot(existsFieldQuery())));
    }

    private FieldValue toFieldValue(String rawValue) {
        String token = rawValue == null ? "" : rawValue.trim();
        if (token.isEmpty()) {
            return FieldValue.of("");
        }

        if (isQuoted(token)) {
            return FieldValue.of(unescapeQuoted(token.substring(1, token.length() - 1)));
        }

        String lower = token.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "false".equals(lower)) {
            return FieldValue.of(Boolean.parseBoolean(lower));
        }

        try {
            if (token.contains(".") || token.contains("e") || token.contains("E")) {
                return FieldValue.of(Double.parseDouble(token));
            }
            return FieldValue.of(Long.parseLong(token));
        } catch (NumberFormatException ignored) {
            return FieldValue.of(token);
        }
    }

    private boolean isQuoted(String token) {
        if (token.length() < 2) {
            return false;
        }
        char first = token.charAt(0);
        char last = token.charAt(token.length() - 1);
        return (first == '"' && last == '"') || (first == '\'' && last == '\'');
    }

    private String unescapeQuoted(String value) {
        return value.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\'", "'");
    }
}
