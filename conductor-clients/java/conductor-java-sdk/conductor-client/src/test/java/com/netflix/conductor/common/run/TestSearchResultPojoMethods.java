/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.run;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestSearchResultPojoMethods {

    @Test
    void testNoArgsConstructor() {
        SearchResult<String> result = new SearchResult<>();
        assertEquals(0, result.getTotalHits());
        assertNull(result.getResults());
    }

    @Test
    void testParameterizedConstructor() {
        long totalHits = 10;
        List<String> results = Arrays.asList("result1", "result2");

        SearchResult<String> result = new SearchResult<>(totalHits, results);

        assertEquals(totalHits, result.getTotalHits());
        assertEquals(results, result.getResults());
    }

    @Test
    void testGetTotalHits() {
        long totalHits = 5;
        SearchResult<Integer> result = new SearchResult<>(totalHits, new ArrayList<>());

        assertEquals(totalHits, result.getTotalHits());
    }

    @Test
    void testGetResults() {
        List<Double> resultList = Arrays.asList(1.1, 2.2, 3.3);
        SearchResult<Double> result = new SearchResult<>(3, resultList);

        assertEquals(resultList, result.getResults());
    }

    @Test
    void testSetTotalHits() {
        SearchResult<String> result = new SearchResult<>();
        long totalHits = 15;

        result.setTotalHits(totalHits);

        assertEquals(totalHits, result.getTotalHits());
    }

    @Test
    void testSetResults() {
        SearchResult<Integer> result = new SearchResult<>();
        List<Integer> resultList = Arrays.asList(1, 2, 3, 4);

        result.setResults(resultList);

        assertEquals(resultList, result.getResults());
    }

    @Test
    void testSettersAfterConstructor() {
        // Create with initial values
        SearchResult<String> result = new SearchResult<>(5, Arrays.asList("a", "b"));

        // Change values using setters
        long newTotalHits = 10;
        List<String> newResults = Arrays.asList("c", "d", "e");

        result.setTotalHits(newTotalHits);
        result.setResults(newResults);

        // Verify the changes
        assertEquals(newTotalHits, result.getTotalHits());
        assertEquals(newResults, result.getResults());
    }
}