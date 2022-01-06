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
package com.netflix.conductor.client.grpc;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.grpc.ProtoMapper;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.TaskServiceGrpc;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.proto.TaskSummaryPb;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class TaskClientTest {

    @Mock ProtoMapper mockedProtoMapper;

    @Mock TaskServiceGrpc.TaskServiceBlockingStub mockedStub;

    TaskClient taskClient;

    @Before
    public void init() {
        taskClient = new TaskClient("test", 0);
        ReflectionTestUtils.setField(taskClient, "stub", mockedStub);
        ReflectionTestUtils.setField(taskClient, "protoMapper", mockedProtoMapper);
    }

    @Test
    public void testSearch() {
        TaskSummary taskSummary = mock(TaskSummary.class);
        TaskSummaryPb.TaskSummary taskSummaryPB = mock(TaskSummaryPb.TaskSummary.class);
        when(mockedProtoMapper.fromProto(taskSummaryPB)).thenReturn(taskSummary);
        TaskServicePb.TaskSummarySearchResult result =
                TaskServicePb.TaskSummarySearchResult.newBuilder()
                        .addResults(taskSummaryPB)
                        .setTotalHits(1)
                        .build();
        SearchPb.Request searchRequest =
                SearchPb.Request.newBuilder().setQuery("test query").build();
        when(mockedStub.search(searchRequest)).thenReturn(result);
        SearchResult<TaskSummary> searchResult = taskClient.search("test query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(taskSummary, searchResult.getResults().get(0));
    }

    @Test
    public void testSearchV2() {
        Task task = mock(Task.class);
        TaskPb.Task taskPB = mock(TaskPb.Task.class);
        when(mockedProtoMapper.fromProto(taskPB)).thenReturn(task);
        TaskServicePb.TaskSearchResult result =
                TaskServicePb.TaskSearchResult.newBuilder()
                        .addResults(taskPB)
                        .setTotalHits(1)
                        .build();
        SearchPb.Request searchRequest =
                SearchPb.Request.newBuilder().setQuery("test query").build();
        when(mockedStub.searchV2(searchRequest)).thenReturn(result);
        SearchResult<Task> searchResult = taskClient.searchV2("test query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(task, searchResult.getResults().get(0));
    }

    @Test
    public void testSearchWithParams() {
        TaskSummary taskSummary = mock(TaskSummary.class);
        TaskSummaryPb.TaskSummary taskSummaryPB = mock(TaskSummaryPb.TaskSummary.class);
        when(mockedProtoMapper.fromProto(taskSummaryPB)).thenReturn(taskSummary);
        TaskServicePb.TaskSummarySearchResult result =
                TaskServicePb.TaskSummarySearchResult.newBuilder()
                        .addResults(taskSummaryPB)
                        .setTotalHits(1)
                        .build();
        SearchPb.Request searchRequest =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(5)
                        .setSort("*")
                        .setFreeText("*")
                        .setQuery("test query")
                        .build();
        when(mockedStub.search(searchRequest)).thenReturn(result);
        SearchResult<TaskSummary> searchResult = taskClient.search(1, 5, "*", "*", "test query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(taskSummary, searchResult.getResults().get(0));
    }

    @Test
    public void testSearchV2WithParams() {
        Task task = mock(Task.class);
        TaskPb.Task taskPB = mock(TaskPb.Task.class);
        when(mockedProtoMapper.fromProto(taskPB)).thenReturn(task);
        TaskServicePb.TaskSearchResult result =
                TaskServicePb.TaskSearchResult.newBuilder()
                        .addResults(taskPB)
                        .setTotalHits(1)
                        .build();
        SearchPb.Request searchRequest =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(5)
                        .setSort("*")
                        .setFreeText("*")
                        .setQuery("test query")
                        .build();
        when(mockedStub.searchV2(searchRequest)).thenReturn(result);
        SearchResult<Task> searchResult = taskClient.searchV2(1, 5, "*", "*", "test query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(task, searchResult.getResults().get(0));
    }
}
