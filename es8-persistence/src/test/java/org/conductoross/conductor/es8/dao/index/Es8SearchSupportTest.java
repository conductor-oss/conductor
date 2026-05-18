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
package org.conductoross.conductor.es8.dao.index;

import java.util.List;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Es8SearchSupportTest {

    private final Es8SearchSupport support = new Es8SearchSupport(null, "conductor");

    @Test
    public void wildcardFreeTextUsesStructuredQueryOnly() throws Exception {
        Query query = support.boolQueryBuilder("status='RUNNING'", "*");

        assertTrue(query.isTerm());
        assertEquals("status", query.term().field());
        assertEquals("RUNNING", query.term().value().stringValue());
    }

    @Test
    public void blankFreeTextAndNoStructuredQueryReturnsMatchAll() throws Exception {
        Query query = support.boolQueryBuilder("", " ");

        assertTrue(query.isMatchAll());
    }

    @Test
    public void unquotedValueWithENotParsedAsDouble() throws Exception {
        // Regression: workflow names like "1E234" or "12E34" were incorrectly parsed as doubles,
        // causing terms queries on keyword fields to return 0 results.
        Query query = support.boolQueryBuilder("workflowType IN (1E234)", "*");

        assertTrue(query.isTerms());
        List<FieldValue> values = query.terms().terms().value();
        assertEquals(1, values.size());
        assertTrue(values.get(0).isString());
        assertEquals("1E234", values.get(0).stringValue());
    }

    @Test
    public void doubleQuotedStructuredQueryUsesTermQuery() throws Exception {
        String uuid = "09d13af8-3a2a-48bf-a91d-ef0a9114f07a";
        Query query = support.boolQueryBuilder("workflowId=\"" + uuid + "\"", "*");

        assertTrue(query.isTerm());
        assertEquals("workflowId", query.term().field());
        assertEquals(uuid, query.term().value().stringValue());
    }

    @Test
    public void andConditionStructuredQueryUsesBoolMustQuery() throws Exception {
        Query query =
                support.boolQueryBuilder(
                        "correlationId='corr-abc' AND workflowType='MyWorkflow'", "*");

        assertTrue(query.isBool());
        assertEquals(2, query.bool().must().size());
        assertTrue(query.bool().must().stream().anyMatch(Query::isTerm));
    }

    @Test
    public void explicitFreeTextAddsSimpleQueryStringClause() throws Exception {
        Query query = support.boolQueryBuilder("status='RUNNING'", "workflowId:abc");

        assertTrue(query.isBool());
        assertEquals(2, query.bool().must().size());
        assertTrue(query.bool().must().stream().anyMatch(Query::isSimpleQueryString));
        assertTrue(query.bool().must().stream().anyMatch(Query::isTerm));
    }
}
