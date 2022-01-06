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
package com.netflix.conductor.es6.dao.query.parser.internal;

import java.io.InputStream;

public class ComparisonOp extends AbstractNode {

    public enum Operators {
        BETWEEN("BETWEEN"),
        EQUALS("="),
        LESS_THAN("<"),
        GREATER_THAN(">"),
        IN("IN"),
        NOT_EQUALS("!="),
        IS("IS"),
        STARTS_WITH("STARTS_WITH");

        private final String value;

        Operators(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    static {
        int max = 0;
        for (Operators op : Operators.values()) {
            max = Math.max(max, op.value().length());
        }
        maxOperatorLength = max;
    }

    private static final int maxOperatorLength;

    private static final int betweenLen = Operators.BETWEEN.value().length();
    private static final int startsWithLen = Operators.STARTS_WITH.value().length();

    private String value;

    public ComparisonOp(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] peeked = peek(maxOperatorLength);
        if (peeked[0] == '=' || peeked[0] == '>' || peeked[0] == '<') {
            this.value = new String(peeked, 0, 1);
        } else if (peeked[0] == 'I' && peeked[1] == 'N') {
            this.value = "IN";
        } else if (peeked[0] == 'I' && peeked[1] == 'S') {
            this.value = "IS";
        } else if (peeked[0] == '!' && peeked[1] == '=') {
            this.value = "!=";
        } else if (peeked.length >= betweenLen
                && peeked[0] == 'B'
                && peeked[1] == 'E'
                && peeked[2] == 'T'
                && peeked[3] == 'W'
                && peeked[4] == 'E'
                && peeked[5] == 'E'
                && peeked[6] == 'N') {
            this.value = Operators.BETWEEN.value();
        } else if (peeked.length == startsWithLen
                && new String(peeked).equals(Operators.STARTS_WITH.value())) {
            this.value = Operators.STARTS_WITH.value();
        } else {
            throw new ParserException(
                    "Expecting an operator (=, >, <, !=, BETWEEN, IN, STARTS_WITH), but found none.  Peeked=>"
                            + new String(peeked));
        }

        read(this.value.length());
    }

    @Override
    public String toString() {
        return " " + value + " ";
    }

    public String getOperator() {
        return value;
    }
}
