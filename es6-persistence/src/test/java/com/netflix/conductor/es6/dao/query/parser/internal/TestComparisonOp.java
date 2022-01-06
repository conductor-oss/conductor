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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class TestComparisonOp extends TestAbstractParser {

    @Test
    public void test() throws Exception {
        String[] tests = new String[] {"<", ">", "=", "!=", "IN", "BETWEEN", "STARTS_WITH"};
        for (String test : tests) {
            ComparisonOp name = new ComparisonOp(getInputStream(test));
            String nameVal = name.getOperator();
            assertNotNull(nameVal);
            assertEquals(test, nameVal);
        }
    }

    @Test(expected = ParserException.class)
    public void testInvalidOp() throws Exception {
        String test = "AND";
        ComparisonOp name = new ComparisonOp(getInputStream(test));
        String nameVal = name.getOperator();
        assertNotNull(nameVal);
        assertEquals(test, nameVal);
    }
}
