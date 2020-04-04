package com.netflix.conductor.dao.es6.index;

import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Thread-safe wrapper for {@link BulkRequest}.
 */
class BulkRequestWrapper
{
    private final BulkRequest bulkRequest;

    BulkRequestWrapper(@Nonnull BulkRequest bulkRequest) {
        this.bulkRequest = Objects.requireNonNull(bulkRequest);
    }

    public void add(@Nonnull UpdateRequest req) {
        synchronized (bulkRequest) {
            bulkRequest.add(Objects.requireNonNull(req));
        }
    }

    public void add(@Nonnull IndexRequest req) {
        synchronized (bulkRequest) {
            bulkRequest.add(Objects.requireNonNull(req));
        }
    }

    BulkRequest get()
    {
        return bulkRequest;
    }

    int numberOfActions() {
        synchronized (bulkRequest) {
            return bulkRequest.numberOfActions();
        }
    }
}
