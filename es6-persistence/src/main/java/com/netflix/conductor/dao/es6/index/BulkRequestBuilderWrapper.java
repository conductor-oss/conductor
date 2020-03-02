package com.netflix.conductor.dao.es6.index;

import org.elasticsearch.action.ActionFuture;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * Thread-safe wrapper for {@link BulkRequestBuilder}.
 */
public class BulkRequestBuilderWrapper {
	private final BulkRequestBuilder bulkRequestBuilder;

	public BulkRequestBuilderWrapper(@Nonnull BulkRequestBuilder bulkRequestBuilder) {
		this.bulkRequestBuilder = Objects.requireNonNull(bulkRequestBuilder);
	}

	public void add(@Nonnull UpdateRequest req) {
		synchronized (bulkRequestBuilder) {
			bulkRequestBuilder.add(Objects.requireNonNull(req));
		}
	}

	public void add(@Nonnull IndexRequest req) {
		synchronized (bulkRequestBuilder) {
			bulkRequestBuilder.add(Objects.requireNonNull(req));
		}
	}

	public int numberOfActions() {
		synchronized (bulkRequestBuilder) {
			return bulkRequestBuilder.numberOfActions();
		}
	}

	public ActionFuture<BulkResponse> execute() {
		synchronized (bulkRequestBuilder) {
			return bulkRequestBuilder.execute();
		}
	}
}

