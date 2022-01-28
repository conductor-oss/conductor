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
package com.netflix.conductor.es7.dao.query.parser;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import com.netflix.conductor.es7.dao.query.parser.internal.AbstractParserTest;
import com.netflix.conductor.es7.dao.query.parser.internal.ConstValue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** @author Viren */
public class TestExpression extends AbstractParserTest {

    @Test
    public void test() throws Exception {
        String test =
                "type='IMAGE' AND subType	='sdp' AND (metadata.width > 50 OR metadata.height > 50)";
        // test = "type='IMAGE' AND subType	='sdp'";
        // test = "(metadata.type = 'IMAGE')";
        InputStream is = new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        Expression expr = new Expression(is);

        System.out.println(expr);

        assertTrue(expr.isBinaryExpr());
        assertNull(expr.getGroupedExpression());
        assertNotNull(expr.getNameValue());

        NameValue nv = expr.getNameValue();
        assertEquals("type", nv.getName().getName());
        assertEquals("=", nv.getOp().getOperator());
        assertEquals("\"IMAGE\"", nv.getValue().getValue());

        Expression rhs = expr.getRightHandSide();
        assertNotNull(rhs);
        assertTrue(rhs.isBinaryExpr());

        nv = rhs.getNameValue();
        assertNotNull(nv); // subType = sdp
        assertNull(rhs.getGroupedExpression());
        assertEquals("subType", nv.getName().getName());
        assertEquals("=", nv.getOp().getOperator());
        assertEquals("\"sdp\"", nv.getValue().getValue());

        assertEquals("AND", rhs.getOperator().getOperator());
        rhs = rhs.getRightHandSide();
        assertNotNull(rhs);
        assertFalse(rhs.isBinaryExpr());
        GroupedExpression ge = rhs.getGroupedExpression();
        assertNotNull(ge);
        expr = ge.getExpression();
        assertNotNull(expr);

        assertTrue(expr.isBinaryExpr());
        nv = expr.getNameValue();
        assertNotNull(nv);
        assertEquals("metadata.width", nv.getName().getName());
        assertEquals(">", nv.getOp().getOperator());
        assertEquals("50", nv.getValue().getValue());

        assertEquals("OR", expr.getOperator().getOperator());
        rhs = expr.getRightHandSide();
        assertNotNull(rhs);
        assertFalse(rhs.isBinaryExpr());
        nv = rhs.getNameValue();
        assertNotNull(nv);

        assertEquals("metadata.height", nv.getName().getName());
        assertEquals(">", nv.getOp().getOperator());
        assertEquals("50", nv.getValue().getValue());
    }

    @Test
    public void testWithSysConstants() throws Exception {
        String test = "type='IMAGE' AND subType	='sdp' AND description IS null";
        InputStream is = new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        Expression expr = new Expression(is);

        System.out.println(expr);

        assertTrue(expr.isBinaryExpr());
        assertNull(expr.getGroupedExpression());
        assertNotNull(expr.getNameValue());

        NameValue nv = expr.getNameValue();
        assertEquals("type", nv.getName().getName());
        assertEquals("=", nv.getOp().getOperator());
        assertEquals("\"IMAGE\"", nv.getValue().getValue());

        Expression rhs = expr.getRightHandSide();
        assertNotNull(rhs);
        assertTrue(rhs.isBinaryExpr());

        nv = rhs.getNameValue();
        assertNotNull(nv); // subType = sdp
        assertNull(rhs.getGroupedExpression());
        assertEquals("subType", nv.getName().getName());
        assertEquals("=", nv.getOp().getOperator());
        assertEquals("\"sdp\"", nv.getValue().getValue());

        assertEquals("AND", rhs.getOperator().getOperator());
        rhs = rhs.getRightHandSide();
        assertNotNull(rhs);
        assertFalse(rhs.isBinaryExpr());
        GroupedExpression ge = rhs.getGroupedExpression();
        assertNull(ge);
        nv = rhs.getNameValue();
        assertNotNull(nv);
        assertEquals("description", nv.getName().getName());
        assertEquals("IS", nv.getOp().getOperator());
        ConstValue cv = nv.getValue();
        assertNotNull(cv);
        assertEquals(cv.getSysConstant(), ConstValue.SystemConsts.NULL);

        test = "description IS not null";
        is = new BufferedInputStream(new ByteArrayInputStream(test.getBytes()));
        expr = new Expression(is);

        System.out.println(expr);
        nv = expr.getNameValue();
        assertNotNull(nv);
        assertEquals("description", nv.getName().getName());
        assertEquals("IS", nv.getOp().getOperator());
        cv = nv.getValue();
        assertNotNull(cv);
        assertEquals(cv.getSysConstant(), ConstValue.SystemConsts.NOT_NULL);
    }
}
