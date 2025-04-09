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

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkResponse<T> {

    /**
     * Key - entityId Value - error message processing this entity
     */
    private Map<String, String> bulkErrorResults = new HashMap<>();

    private List<T> bulkSuccessfulResults = new ArrayList<>();

    private String message = "Bulk Request has been processed.";

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