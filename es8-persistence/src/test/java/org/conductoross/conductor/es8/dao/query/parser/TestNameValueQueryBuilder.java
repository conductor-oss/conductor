/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.es8.dao.query.parser;

import java.util.List;

import org.conductoross.conductor.es8.dao.query.parser.internal.AbstractParserTest;
import org.junit.Test;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestNameValueQueryBuilder extends AbstractParserTest {

    @Test
    public void equalsUsesTermQuery() throws Exception {
        NameValue nameValue = new NameValue(getInputStream("status='RUNNING'"));

        Query query = nameValue.getFilterBuilder();

        assertTrue(query.isTerm());
        assertEquals("status", query.term().field());
        assertTrue(query.term().value().isString());
        assertEquals("RUNNING", query.term().value().stringValue());
    }

    @Test
    public void notEqualsUsesMustNotTermQuery() throws Exception {
        NameValue nameValue = new NameValue(getInputStream("status!='RUNNING'"));

        Query query = nameValue.getFilterBuilder();

        assertTrue(query.isBool());
        assertEquals(1, query.bool().mustNot().size());
        Query mustNot = query.bool().mustNot().getFirst();
        assertTrue(mustNot.isTerm());
        assertEquals("status", mustNot.term().field());
        assertEquals("RUNNING", mustNot.term().value().stringValue());
    }

    @Test
    public void inNormalizesQuotedAndNumericValues() throws Exception {
        NameValue nameValue = new NameValue(getInputStream("priority IN (1, 2.5, '3')"));

        Query query = nameValue.getFilterBuilder();

        assertTrue(query.isTerms());
        List<FieldValue> values = query.terms().terms().value();
        assertEquals(3, values.size());
        assertTrue(values.get(0).isLong());
        assertEquals(1L, values.get(0).longValue());
        assertTrue(values.get(1).isDouble());
        assertEquals(2.5d, values.get(1).doubleValue(), 0.000001d);
        assertTrue(values.get(2).isString());
        assertEquals("3", values.get(2).stringValue());
    }

    @Test
    public void equalsNullUsesMissingFieldQuery() throws Exception {
        NameValue nameValue = new NameValue(getInputStream("archived = null"));

        Query query = nameValue.getFilterBuilder();

        assertTrue(query.isBool());
        assertEquals(1, query.bool().mustNot().size());
        assertTrue(query.bool().mustNot().getFirst().isExists());
        assertEquals("archived", query.bool().mustNot().getFirst().exists().field());
    }
}
