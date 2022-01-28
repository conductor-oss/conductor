/*
 * Copyright 2016 Netflix, Inc.
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
package com.netflix.conductor.es7.dao.index;

import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.junit.Test;
import org.mockito.Mockito;

public class TestBulkRequestBuilderWrapper {
    BulkRequestBuilder builder = Mockito.mock(BulkRequestBuilder.class);
    BulkRequestBuilderWrapper wrapper = new BulkRequestBuilderWrapper(builder);

    @Test(expected = Exception.class)
    public void testAddNullUpdateRequest() {
        wrapper.add((UpdateRequest) null);
    }

    @Test(expected = Exception.class)
    public void testAddNullIndexRequest() {
        wrapper.add((IndexRequest) null);
    }

    @Test
    public void testBuilderCalls() {
        IndexRequest indexRequest = new IndexRequest();
        UpdateRequest updateRequest = new UpdateRequest();

        wrapper.add(indexRequest);
        wrapper.add(updateRequest);
        wrapper.numberOfActions();
        wrapper.execute();

        Mockito.verify(builder, Mockito.times(1)).add(indexRequest);
        Mockito.verify(builder, Mockito.times(1)).add(updateRequest);
        Mockito.verify(builder, Mockito.times(1)).numberOfActions();
        Mockito.verify(builder, Mockito.times(1)).execute();
    }
}
