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
package com.netflix.conductor.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response object to return a list of succeeded entities and a map of failed ones, including error
 * message, for the bulk request.
 */
public class BulkResponse {

    /** Key - entityId Value - error message processing this entity */
    private final Map<String, String> bulkErrorResults;

    private final List<String> bulkSuccessfulResults;
    private final String message = "Bulk Request has been processed.";

    public BulkResponse() {
        this.bulkSuccessfulResults = new ArrayList<>();
        this.bulkErrorResults = new HashMap<>();
    }

    public List<String> getBulkSuccessfulResults() {
        return bulkSuccessfulResults;
    }

    public Map<String, String> getBulkErrorResults() {
        return bulkErrorResults;
    }

    public void appendSuccessResponse(String id) {
        bulkSuccessfulResults.add(id);
    }

    public void appendFailedResponse(String id, String errorMessage) {
        bulkErrorResults.put(id, errorMessage);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BulkResponse)) {
            return false;
        }
        BulkResponse that = (BulkResponse) o;
        return Objects.equals(bulkSuccessfulResults, that.bulkSuccessfulResults)
                && Objects.equals(bulkErrorResults, that.bulkErrorResults);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bulkSuccessfulResults, bulkErrorResults, message);
    }

    @Override
    public String toString() {
        return "BulkResponse{"
                + "bulkSuccessfulResults="
                + bulkSuccessfulResults
                + ", bulkErrorResults="
                + bulkErrorResults
                + ", message='"
                + message
                + '\''
                + '}';
    }
}
