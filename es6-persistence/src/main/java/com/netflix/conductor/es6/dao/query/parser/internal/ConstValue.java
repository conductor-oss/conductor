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

/**
 * Constant value can be:
 *
 * <ol>
 *   <li>List of values (a,b,c)
 *   <li>Range of values (m AND n)
 *   <li>A value (x)
 *   <li>A value is either a string or a number
 * </ol>
 */
public class ConstValue extends AbstractNode {

    public enum SystemConsts {
        NULL("null"),
        NOT_NULL("not null");
        private final String value;

        SystemConsts(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final String QUOTE = "\"";

    private Object value;

    private SystemConsts sysConsts;

    public ConstValue(InputStream is) throws ParserException {
        super(is);
    }

    @Override
    protected void _parse() throws Exception {
        byte[] peeked = peek(4);
        String sp = new String(peeked).trim();
        // Read a constant value (number or a string)
        if (peeked[0] == '"' || peeked[0] == '\'') {
            this.value = readString(is);
        } else if (sp.toLowerCase().startsWith("not")) {
            this.value = SystemConsts.NOT_NULL.value();
            sysConsts = SystemConsts.NOT_NULL;
            read(SystemConsts.NOT_NULL.value().length());
        } else if (sp.equalsIgnoreCase(SystemConsts.NULL.value())) {
            this.value = SystemConsts.NULL.value();
            sysConsts = SystemConsts.NULL;
            read(SystemConsts.NULL.value().length());
        } else {
            this.value = readNumber(is);
        }
    }

    private String readNumber(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        while (is.available() > 0) {
            is.mark(1);
            char c = (char) is.read();
            if (!isNumeric(c)) {
                is.reset();
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    /**
     * Reads an escaped string
     *
     * @throws Exception
     */
    private String readString(InputStream is) throws Exception {
        char delim = (char) read(1)[0];
        StringBuilder sb = new StringBuilder();
        boolean valid = false;
        while (is.available() > 0) {
            char c = (char) is.read();
            if (c == delim) {
                valid = true;
                break;
            } else if (c == '\\') {
                // read the next character as part of the value
                c = (char) is.read();
                sb.append(c);
            } else {
                sb.append(c);
            }
        }
        if (!valid) {
            throw new ParserException(
                    "String constant is not quoted with <" + delim + "> : " + sb.toString());
        }
        return QUOTE + sb.toString() + QUOTE;
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "" + value;
    }

    public String getUnquotedValue() {
        String result = toString();
        if (result.length() >= 2 && result.startsWith(QUOTE) && result.endsWith(QUOTE)) {
            result = result.substring(1, result.length() - 1);
        }
        return result;
    }

    public boolean isSysConstant() {
        return this.sysConsts != null;
    }

    public SystemConsts getSysConstant() {
        return this.sysConsts;
    }
}
