/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.es8.dao.index;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.OperationType;

import static org.junit.Assert.assertEquals;

public class Es8BulkIngestionSupportTest {

    @Test
    public void collectFailedOperationsReturnsOnlyFailedItems() {
        BulkOperation op1 =
                BulkOperation.of(
                        op ->
                                op.index(
                                        i ->
                                                i.index("conductor_task")
                                                        .id("task-1")
                                                        .document(Map.of())));
        BulkOperation op2 =
                BulkOperation.of(
                        op ->
                                op.index(
                                        i ->
                                                i.index("conductor_task")
                                                        .id("task-2")
                                                        .document(Map.of())));

        BulkRequest request = BulkRequest.of(b -> b.operations(op1, op2));

        BulkResponseItem successItem =
                BulkResponseItem.of(
                        i ->
                                i.operationType(OperationType.Index)
                                        .index("conductor_task")
                                        .id("task-1")
                                        .status(201));
        BulkResponseItem failedItem =
                BulkResponseItem.of(
                        i ->
                                i.operationType(OperationType.Index)
                                        .index("conductor_task")
                                        .id("task-2")
                                        .status(400)
                                        .error(
                                                ErrorCause.of(
                                                        e ->
                                                                e.type("mapper_parsing_exception")
                                                                        .reason("bad doc"))));
        BulkResponse response =
                BulkResponse.of(b -> b.errors(true).items(successItem, failedItem).took(1L));

        List<BulkOperation> failedOperations =
                Es8BulkIngestionSupport.collectFailedOperations(request, response);

        assertEquals(1, failedOperations.size());
        assertEquals("task-2", failedOperations.getFirst().index().id());
    }
}
