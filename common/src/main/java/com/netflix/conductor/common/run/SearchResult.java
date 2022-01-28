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
package com.netflix.conductor.common.run;

import java.util.List;

public class SearchResult<T> {

    private long totalHits;

    private List<T> results;

    public SearchResult() {}

    public SearchResult(long totalHits, List<T> results) {
        super();
        this.totalHits = totalHits;
        this.results = results;
    }

    /** @return the totalHits */
    public long getTotalHits() {
        return totalHits;
    }

    /** @return the results */
    public List<T> getResults() {
        return results;
    }

    /** @param totalHits the totalHits to set */
    public void setTotalHits(long totalHits) {
        this.totalHits = totalHits;
    }

    /** @param results the results to set */
    public void setResults(List<T> results) {
        this.results = results;
    }
}
