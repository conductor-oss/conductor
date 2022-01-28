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
package com.netflix.conductor.es7.dao.query.parser.internal;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/** @author Viren List of constants */
public class ListConst extends AbstractNode {

    private List<Object> values;

    public ListConst(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] peeked = read(1);
        assertExpected(peeked, "(");
        this.values = readList();
    }

    private List<Object> readList() throws Exception {
        List<Object> list = new LinkedList<Object>();
        boolean valid = false;
        char c;

        StringBuilder sb = new StringBuilder();
        while (is.available() > 0) {
            c = (char) is.read();
            if (c == ')') {
                valid = true;
                break;
            } else if (c == ',') {
                list.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        list.add(sb.toString().trim());
        if (!valid) {
            throw new ParserException("Expected ')' but never encountered in the stream");
        }
        return list;
    }

    public List<Object> getList() {
        return (List<Object>) values;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
