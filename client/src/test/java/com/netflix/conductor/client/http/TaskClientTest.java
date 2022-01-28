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
package com.netflix.conductor.client.http;

import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;

import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class TaskClientTest {

    @Mock private ClientHandler clientHandler;

    @Mock private ClientConfig clientConfig;

    private TaskClient taskClient;

    @Before
    public void before() {
        this.taskClient = new TaskClient(clientConfig, clientHandler);
        this.taskClient.setRootURI("http://myuri:8080/");
    }

    @Test
    public void testSearch() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<TaskSummary> taskSearchResult = new SearchResult<>();

        taskSearchResult.setTotalHits(1);
        TaskSummary taskSummary = mock(TaskSummary.class);

        taskSearchResult.setResults(Collections.singletonList(taskSummary));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<TaskSummary>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                TaskSummary.class))))
                .thenReturn(taskSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/tasks/search?query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<TaskSummary> searchResult = taskClient.search("my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(taskSummary), searchResult.getResults());
    }

    @Test
    public void testSearchV2() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<Task> taskSearchResult = new SearchResult<>();
        taskSearchResult.setTotalHits(1);
        Task task = mock(Task.class);
        taskSearchResult.setResults(Collections.singletonList(task));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<Task>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                Task.class))))
                .thenReturn(taskSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/tasks/search-v2?query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<Task> searchResult = taskClient.searchV2("my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(task), searchResult.getResults());
    }

    @Test
    public void testSearchWithParams() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<TaskSummary> taskSearchResult = new SearchResult<>();

        taskSearchResult.setTotalHits(1);
        TaskSummary taskSummary = mock(TaskSummary.class);

        taskSearchResult.setResults(Collections.singletonList(taskSummary));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<TaskSummary>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                TaskSummary.class))))
                .thenReturn(taskSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/tasks/search?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<TaskSummary> searchResult =
                taskClient.search(0, 10, "sort", "text", "my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(taskSummary), searchResult.getResults());
    }

    @Test
    public void testSearchV2WithParams() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<Task> taskSearchResult = new SearchResult<>();
        taskSearchResult.setTotalHits(1);
        Task task = mock(Task.class);
        taskSearchResult.setResults(Collections.singletonList(task));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<Task>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                Task.class))))
                .thenReturn(taskSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/tasks/search-v2?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<Task> searchResult =
                taskClient.searchV2(0, 10, "sort", "text", "my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(task), searchResult.getResults());
    }
}
