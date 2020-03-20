/*
 * Copyright 2020 Medallia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

