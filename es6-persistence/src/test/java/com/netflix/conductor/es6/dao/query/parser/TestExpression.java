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
package com.netflix.conductor.es6.dao.query.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import com.netflix.conductor.es6.dao.query.parser.internal.ConstValue;
import com.netflix.conductor.es6.dao.query.parser.internal.TestAbstractParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestExpression extends TestAbstractParser {

    @Test
    public void test() throws Exception {
        String test =
                "type='IMAGE' AND subType	='sdp' AND (metadata.width > 50 OR metadata.height > 50)";
        InputStream inputStream =
                new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        Expression expression = new Expression(inputStream);

        assertTrue(expression.isBinaryExpr());
        assertNull(expression.getGroupedExpression());
        assertNotNull(expression.getNameValue());

        NameValue nameValue = expression.getNameValue();
        assertEquals("type", nameValue.getName().getName());
        assertEquals("=", nameValue.getOp().getOperator());
        assertEquals("\"IMAGE\"", nameValue.getValue().getValue());

        Expression rightHandSide = expression.getRightHandSide();
        assertNotNull(rightHandSide);
        assertTrue(rightHandSide.isBinaryExpr());

        nameValue = rightHandSide.getNameValue();
        assertNotNull(nameValue); // subType = sdp
        assertNull(rightHandSide.getGroupedExpression());
        assertEquals("subType", nameValue.getName().getName());
        assertEquals("=", nameValue.getOp().getOperator());
        assertEquals("\"sdp\"", nameValue.getValue().getValue());

        assertEquals("AND", rightHandSide.getOperator().getOperator());
        rightHandSide = rightHandSide.getRightHandSide();
        assertNotNull(rightHandSide);
        assertFalse(rightHandSide.isBinaryExpr());
        GroupedExpression groupedExpression = rightHandSide.getGroupedExpression();
        assertNotNull(groupedExpression);
        expression = groupedExpression.getExpression();
        assertNotNull(expression);

        assertTrue(expression.isBinaryExpr());
        nameValue = expression.getNameValue();
        assertNotNull(nameValue);
        assertEquals("metadata.width", nameValue.getName().getName());
        assertEquals(">", nameValue.getOp().getOperator());
        assertEquals("50", nameValue.getValue().getValue());

        assertEquals("OR", expression.getOperator().getOperator());
        rightHandSide = expression.getRightHandSide();
        assertNotNull(rightHandSide);
        assertFalse(rightHandSide.isBinaryExpr());
        nameValue = rightHandSide.getNameValue();
        assertNotNull(nameValue);

        assertEquals("metadata.height", nameValue.getName().getName());
        assertEquals(">", nameValue.getOp().getOperator());
        assertEquals("50", nameValue.getValue().getValue());
    }

    @Test
    public void testWithSysConstants() throws Exception {
        String test = "type='IMAGE' AND subType	='sdp' AND description IS null";
        InputStream inputStream =
                new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        Expression expression = new Expression(inputStream);

        assertTrue(expression.isBinaryExpr());
        assertNull(expression.getGroupedExpression());
        assertNotNull(expression.getNameValue());

        NameValue nameValue = expression.getNameValue();
        assertEquals("type", nameValue.getName().getName());
        assertEquals("=", nameValue.getOp().getOperator());
        assertEquals("\"IMAGE\"", nameValue.getValue().getValue());

        Expression rightHandSide = expression.getRightHandSide();
        assertNotNull(rightHandSide);
        assertTrue(rightHandSide.isBinaryExpr());

        nameValue = rightHandSide.getNameValue();
        assertNotNull(nameValue); // subType = sdp
        assertNull(rightHandSide.getGroupedExpression());
        assertEquals("subType", nameValue.getName().getName());
        assertEquals("=", nameValue.getOp().getOperator());
        assertEquals("\"sdp\"", nameValue.getValue().getValue());

        assertEquals("AND", rightHandSide.getOperator().getOperator());
        rightHandSide = rightHandSide.getRightHandSide();
        assertNotNull(rightHandSide);
        assertFalse(rightHandSide.isBinaryExpr());

        GroupedExpression groupedExpression = rightHandSide.getGroupedExpression();
        assertNull(groupedExpression);
        nameValue = rightHandSide.getNameValue();
        assertNotNull(nameValue);
        assertEquals("description", nameValue.getName().getName());
        assertEquals("IS", nameValue.getOp().getOperator());

        ConstValue constValue = nameValue.getValue();
        assertNotNull(constValue);
        assertEquals(constValue.getSysConstant(), ConstValue.SystemConsts.NULL);

        test = "description IS not null";
        inputStream = new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        expression = new Expression(inputStream);

        nameValue = expression.getNameValue();
        assertNotNull(nameValue);
        assertEquals("description", nameValue.getName().getName());
        assertEquals("IS", nameValue.getOp().getOperator());

        constValue = nameValue.getValue();
        assertNotNull(constValue);
        assertEquals(constValue.getSysConstant(), ConstValue.SystemConsts.NOT_NULL);
    }
}
