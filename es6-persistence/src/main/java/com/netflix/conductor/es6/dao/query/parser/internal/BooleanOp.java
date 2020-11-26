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

public class BooleanOp extends AbstractNode {

    private String value;

    public BooleanOp(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] buffer = peek(3);
        if (buffer.length > 1 && buffer[0] == 'O' && buffer[1] == 'R') {
            this.value = "OR";
        } else if (buffer.length > 2 && buffer[0] == 'A' && buffer[1] == 'N' && buffer[2] == 'D') {
            this.value = "AND";
        } else {
            throw new ParserException("No valid boolean operator found...");
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

    public boolean isAnd() {
        return "AND".equals(value);
    }

    public boolean isOr() {
        return "OR".equals(value);
    }
}
