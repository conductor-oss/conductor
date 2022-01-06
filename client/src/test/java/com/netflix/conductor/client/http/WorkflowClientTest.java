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

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;

import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class WorkflowClientTest {

    @Mock private ClientHandler clientHandler;

    @Mock private ClientConfig clientConfig;

    private WorkflowClient workflowClient;

    @Before
    public void before() {
        this.workflowClient = new WorkflowClient(clientConfig, clientHandler);
        this.workflowClient.setRootURI("http://myuri:8080/");
    }

    @Test
    public void testSearch() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<WorkflowSummary> workflowSearchResult = new SearchResult<>();

        workflowSearchResult.setTotalHits(1);
        WorkflowSummary workflowSummary = mock(WorkflowSummary.class);

        workflowSearchResult.setResults(Collections.singletonList(workflowSummary));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<WorkflowSummary>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                WorkflowSummary.class))))
                .thenReturn(workflowSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/workflow/search?query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<WorkflowSummary> searchResult = workflowClient.search("my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflowSummary), searchResult.getResults());
    }

    @Test
    public void testSearchV2() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<Workflow> workflowSearchResult = new SearchResult<>();
        workflowSearchResult.setTotalHits(1);
        Workflow workflow = mock(Workflow.class);
        workflowSearchResult.setResults(Collections.singletonList(workflow));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<Workflow>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                Workflow.class))))
                .thenReturn(workflowSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/workflow/search-v2?query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<Workflow> searchResult = workflowClient.searchV2("my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow), searchResult.getResults());
    }

    @Test
    public void testSearchWithParams() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<WorkflowSummary> workflowSearchResult = new SearchResult<>();

        workflowSearchResult.setTotalHits(1);
        WorkflowSummary workflowSummary = mock(WorkflowSummary.class);

        workflowSearchResult.setResults(Collections.singletonList(workflowSummary));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<WorkflowSummary>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                WorkflowSummary.class))))
                .thenReturn(workflowSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/workflow/search?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<WorkflowSummary> searchResult =
                workflowClient.search(0, 10, "sort", "text", "my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflowSummary), searchResult.getResults());
    }

    @Test
    public void testSearchV2WithParams() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<Workflow> workflowSearchResult = new SearchResult<>();
        workflowSearchResult.setTotalHits(1);
        Workflow workflow = mock(Workflow.class);
        workflowSearchResult.setResults(Collections.singletonList(workflow));
        when(clientResponse.getEntity(
                        argThat(
                                (GenericType<SearchResult<Workflow>> type) ->
                                        ((ParameterizedType) type.getType())
                                                        .getRawType()
                                                        .equals(SearchResult.class)
                                                && ((ParameterizedType) type.getType())
                                                        .getActualTypeArguments()[0].equals(
                                                                Workflow.class))))
                .thenReturn(workflowSearchResult);
        when(clientHandler.handle(
                        argThat(
                                argument ->
                                        argument.getURI()
                                                .equals(
                                                        URI.create(
                                                                "http://myuri:8080/workflow/search-v2?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<Workflow> searchResult =
                workflowClient.searchV2(0, 10, "sort", "text", "my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow), searchResult.getResults());
    }
}
