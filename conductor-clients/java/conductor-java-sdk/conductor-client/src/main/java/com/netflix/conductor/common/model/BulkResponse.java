/*
 * Copyright 2020 Conductor Authors.
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
 *
 * @param <T> the type of entities included in the successful results
 */
public class BulkResponse<T> {

    /**
     * Key - entityId Value - error message processing this entity
     */
    private final Map<String, String> bulkErrorResults;

    private final List<T> bulkSuccessfulResults;

    private final String message = "Bulk Request has been processed.";

    public BulkResponse() {
        this.bulkSuccessfulResults = new ArrayList<>();
        this.bulkErrorResults = new HashMap<>();
    }

    public List<T> getBulkSuccessfulResults() {
        return bulkSuccessfulResults;
    }

    public Map<String, String> getBulkErrorResults() {
        return bulkErrorResults;
    }

    public void appendSuccessResponse(T result) {
        bulkSuccessfulResults.add(result);
    }

    public void appendFailedResponse(String id, String errorMessage) {
        bulkErrorResults.put(id, errorMessage);
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BulkResponse)) {
            return false;
        }
        BulkResponse that = (BulkResponse) o;
        return Objects.equals(bulkSuccessfulResults, that.bulkSuccessfulResults) && Objects.equals(bulkErrorResults, that.bulkErrorResults);
    }

    public int hashCode() {
        return Objects.hash(bulkSuccessfulResults, bulkErrorResults, message);
    }

    public String toString() {
        return "BulkResponse{" + "bulkSuccessfulResults=" + bulkSuccessfulResults + ", bulkErrorResults=" + bulkErrorResults + ", message='" + message + '\'' + '}';
    }
}
