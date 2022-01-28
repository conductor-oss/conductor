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

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/** @author Viren */
public class TestConstValue extends AbstractParserTest {

    @Test
    public void testStringConst() throws Exception {
        String test = "'string value'";
        String expected =
                test.replaceAll(
                        "'", "\""); // Quotes are removed but then the result is double quoted.
        ConstValue cv = new ConstValue(getInputStream(test));
        assertNotNull(cv.getValue());
        assertEquals(expected, cv.getValue());
        assertTrue(cv.getValue() instanceof String);

        test = "\"string value\"";
        cv = new ConstValue(getInputStream(test));
        assertNotNull(cv.getValue());
        assertEquals(expected, cv.getValue());
        assertTrue(cv.getValue() instanceof String);
    }

    @Test
    public void testSystemConst() throws Exception {
        String test = "null";
        ConstValue cv = new ConstValue(getInputStream(test));
        assertNotNull(cv.getValue());
        assertTrue(cv.getValue() instanceof String);
        assertEquals(cv.getSysConstant(), ConstValue.SystemConsts.NULL);
        test = "null";

        test = "not null";
        cv = new ConstValue(getInputStream(test));
        assertNotNull(cv.getValue());
        assertEquals(cv.getSysConstant(), ConstValue.SystemConsts.NOT_NULL);
    }

    @Test(expected = ParserException.class)
    public void testInvalid() throws Exception {
        String test = "'string value";
        new ConstValue(getInputStream(test));
    }

    @Test
    public void testNumConst() throws Exception {
        String test = "12345.89";
        ConstValue cv = new ConstValue(getInputStream(test));
        assertNotNull(cv.getValue());
        assertTrue(
                cv.getValue()
                        instanceof
                        String); // Numeric values are stored as string as we are just passing thru
        // them to ES
        assertEquals(test, cv.getValue());
    }

    @Test
    public void testRange() throws Exception {
        String test = "50 AND 100";
        Range range = new Range(getInputStream(test));
        assertEquals("50", range.getLow());
        assertEquals("100", range.getHigh());
    }

    @Test(expected = ParserException.class)
    public void testBadRange() throws Exception {
        String test = "50 AND";
        new Range(getInputStream(test));
    }

    @Test
    public void testArray() throws Exception {
        String test = "(1, 3, 'name', 'value2')";
        ListConst lc = new ListConst(getInputStream(test));
        List<Object> list = lc.getList();
        assertEquals(4, list.size());
        assertTrue(list.contains("1"));
        assertEquals("'value2'", list.get(3)); // Values are preserved as it is...
    }
}
