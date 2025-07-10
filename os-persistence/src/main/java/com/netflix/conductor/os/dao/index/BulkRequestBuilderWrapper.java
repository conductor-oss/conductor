/*
 * Copyright 2023 Conductor Authors.
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
package com.netflix.conductor.os.dao.index;

import java.util.Objects;

import org.opensearch.action.bulk.BulkRequestBuilder;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.update.UpdateRequest;
import org.springframework.lang.NonNull;

/** Thread-safe wrapper for {@link BulkRequestBuilder}. */
public class BulkRequestBuilderWrapper {
    private final BulkRequestBuilder bulkRequestBuilder;

    public BulkRequestBuilderWrapper(@NonNull BulkRequestBuilder bulkRequestBuilder) {
        this.bulkRequestBuilder = Objects.requireNonNull(bulkRequestBuilder);
    }

    public void add(@NonNull UpdateRequest req) {
        synchronized (bulkRequestBuilder) {
            bulkRequestBuilder.add(Objects.requireNonNull(req));
        }
    }

    public void add(@NonNull IndexRequest req) {
        synchronized (bulkRequestBuilder) {
            bulkRequestBuilder.add(Objects.requireNonNull(req));
        }
    }

    public int numberOfActions() {
        synchronized (bulkRequestBuilder) {
            return bulkRequestBuilder.numberOfActions();
        }
    }

    public org.opensearch.common.action.ActionFuture<BulkResponse> execute() {
        synchronized (bulkRequestBuilder) {
            return bulkRequestBuilder.execute();
        }
    }
}
