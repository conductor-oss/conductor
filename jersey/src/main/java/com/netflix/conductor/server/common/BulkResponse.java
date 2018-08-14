package com.netflix.conductor.server.common;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Synchronous response to return a list of failed and succeeded entities for the bulk request.
 */
public class BulkResponse {

    private final List<String> bulkSuccessfulResults;
    private final List<Pair<String,String>> bulkErrorResults;
    private final String message = "Bulk Request has been processed.";

    public BulkResponse() {
        this.bulkSuccessfulResults = new ArrayList<>();
        this.bulkErrorResults = new ArrayList<>();
    }


    public List<String> getBulkSuccessfulResults() {
        return bulkSuccessfulResults;
    }

    public List<Pair<String, String>> getBulkErrorResults() {
        return bulkErrorResults;
    }


    public void appendSuccessResponse(String id) {
        bulkSuccessfulResults.add(id);
    }

    /**
     *
     * @param id - id of a failed entity
     * @param message - error message
     */
    public void appendFailedResponse(String id, String message) {
        bulkErrorResults.add(MutablePair.of(id,message));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BulkResponse)) return false;
        BulkResponse that = (BulkResponse) o;
        return Objects.equals(bulkSuccessfulResults, that.bulkSuccessfulResults) &&
                Objects.equals(bulkErrorResults, that.bulkErrorResults) &&
                Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {

        return Objects.hash(bulkSuccessfulResults, bulkErrorResults, message);
    }

    @Override
    public String toString() {
        return "BulkResponse{" +
                "bulkSuccessfulResults=" + bulkSuccessfulResults +
                ", bulkErrorResults=" + bulkErrorResults +
                ", message='" + message + '\'' +
                '}';
    }
}
