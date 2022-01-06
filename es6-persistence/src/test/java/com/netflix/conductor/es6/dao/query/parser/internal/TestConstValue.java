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

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TestConstValue extends TestAbstractParser {

    @Test
    public void testStringConst() throws Exception {
        String test = "'string value'";
        String expected =
                test.replaceAll(
                        "'", "\""); // Quotes are removed but then the result is double quoted.
        ConstValue constValue = new ConstValue(getInputStream(test));
        assertNotNull(constValue.getValue());
        assertEquals(expected, constValue.getValue());
        assertTrue(constValue.getValue() instanceof String);

        test = "\"string value\"";
        constValue = new ConstValue(getInputStream(test));
        assertNotNull(constValue.getValue());
        assertEquals(expected, constValue.getValue());
        assertTrue(constValue.getValue() instanceof String);
    }

    @Test
    public void testSystemConst() throws Exception {
        String test = "null";
        ConstValue constValue = new ConstValue(getInputStream(test));
        assertNotNull(constValue.getValue());
        assertTrue(constValue.getValue() instanceof String);
        assertEquals(constValue.getSysConstant(), ConstValue.SystemConsts.NULL);

        test = "not null";
        constValue = new ConstValue(getInputStream(test));
        assertNotNull(constValue.getValue());
        assertEquals(constValue.getSysConstant(), ConstValue.SystemConsts.NOT_NULL);
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
        ListConst listConst = new ListConst(getInputStream(test));
        List<Object> list = listConst.getList();
        assertEquals(4, list.size());
        assertTrue(list.contains("1"));
        assertEquals("'value2'", list.get(3)); // Values are preserved as it is...
    }
}
