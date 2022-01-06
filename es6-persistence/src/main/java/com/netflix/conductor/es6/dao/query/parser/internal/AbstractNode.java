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
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public abstract class AbstractNode {

    public static final Pattern WHITESPACE = Pattern.compile("\\s");

    protected static Set<Character> comparisonOprs = new HashSet<>();

    static {
        comparisonOprs.add('>');
        comparisonOprs.add('<');
        comparisonOprs.add('=');
    }

    protected InputStream is;

    protected AbstractNode(InputStream is) throws ParserException {
        this.is = is;
        this.parse();
    }

    protected boolean isNumber(String test) {
        try {
            // If you can convert to a big decimal value, then it is a number.
            new BigDecimal(test);
            return true;

        } catch (NumberFormatException e) {
            // Ignore
        }
        return false;
    }

    protected boolean isBoolOpr(byte[] buffer) {
        if (buffer.length > 1 && buffer[0] == 'O' && buffer[1] == 'R') {
            return true;
        } else {
            return buffer.length > 2 && buffer[0] == 'A' && buffer[1] == 'N' && buffer[2] == 'D';
        }
    }

    protected boolean isComparisonOpr(byte[] buffer) {
        if (buffer[0] == 'I' && buffer[1] == 'N') {
            return true;
        } else if (buffer[0] == '!' && buffer[1] == '=') {
            return true;
        } else {
            return comparisonOprs.contains((char) buffer[0]);
        }
    }

    protected byte[] peek(int length) throws Exception {
        return read(length, true);
    }

    protected byte[] read(int length) throws Exception {
        return read(length, false);
    }

    protected String readToken() throws Exception {
        skipWhitespace();
        StringBuilder sb = new StringBuilder();
        while (is.available() > 0) {
            char c = (char) peek(1)[0];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                is.skip(1);
                break;
            } else if (c == '=' || c == '>' || c == '<' || c == '!') {
                // do not skip
                break;
            }
            sb.append(c);
            is.skip(1);
        }
        return sb.toString().trim();
    }

    protected boolean isNumeric(char c) {
        return c == '-' || c == 'e' || (c >= '0' && c <= '9') || c == '.';
    }

    protected void assertExpected(byte[] found, String expected) throws ParserException {
        assertExpected(new String(found), expected);
    }

    protected void assertExpected(String found, String expected) throws ParserException {
        if (!found.equals(expected)) {
            throw new ParserException("Expected " + expected + ", found " + found);
        }
    }

    protected void assertExpected(char found, char expected) throws ParserException {
        if (found != expected) {
            throw new ParserException("Expected " + expected + ", found " + found);
        }
    }

    protected static void efor(int length, FunctionThrowingException<Integer> consumer)
            throws Exception {
        for (int i = 0; i < length; i++) {
            consumer.accept(i);
        }
    }

    protected abstract void _parse() throws Exception;

    // Public stuff here
    private void parse() throws ParserException {
        // skip white spaces
        skipWhitespace();
        try {
            _parse();
        } catch (Exception e) {
            if (!(e instanceof ParserException)) {
                throw new ParserException("Error parsing", e);
            } else {
                throw (ParserException) e;
            }
        }
        skipWhitespace();
    }

    // Private methods

    private byte[] read(int length, boolean peekOnly) throws Exception {
        byte[] buf = new byte[length];
        if (peekOnly) {
            is.mark(length);
        }
        efor(length, (Integer c) -> buf[c] = (byte) is.read());
        if (peekOnly) {
            is.reset();
        }
        return buf;
    }

    protected void skipWhitespace() throws ParserException {
        try {
            while (is.available() > 0) {
                byte c = peek(1)[0];
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    // skip
                    read(1);
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            throw new ParserException(e.getMessage(), e);
        }
    }
}
