package com.netflix.conductor.client.http;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.sun.jersey.api.client.ClientHandler;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.config.ClientConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.net.URI;
import java.util.Collections;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WorkflowClientTest {

    @Mock
    private ClientHandler clientHandler;

    @Mock
    private ClientConfig clientConfig;

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
        WorkflowSummary workflowSummary = new WorkflowSummary(new Workflow());

        workflowSearchResult.setResults(Collections.singletonList(workflowSummary));
        when(clientResponse.getEntity(argThat((GenericType<SearchResult<WorkflowSummary>> type) ->
                ((ParameterizedTypeImpl) type.getType()).getRawType().equals(SearchResult.class) &&
                        ((ParameterizedTypeImpl) type.getType()).getActualTypeArguments()[0].equals(WorkflowSummary.class)
        ))).thenReturn(workflowSearchResult);
        when(clientHandler.handle(argThat(argument -> argument.getURI().equals(URI.create("http://myuri:8080/workflow/search?query=my_complex_query")))))
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
        Workflow workflow = new Workflow();
        workflowSearchResult.setResults(Collections.singletonList(workflow));
        when(clientResponse.getEntity(argThat((GenericType<SearchResult<Workflow>> type) ->
                ((ParameterizedTypeImpl) type.getType()).getRawType().equals(SearchResult.class) &&
                        ((ParameterizedTypeImpl) type.getType()).getActualTypeArguments()[0].equals(Workflow.class)
        ))).thenReturn(workflowSearchResult);
        when(clientHandler.handle(argThat(argument -> argument.getURI().equals(URI.create("http://myuri:8080/workflow/search-v2?query=my_complex_query")))))
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
        WorkflowSummary workflowSummary = new WorkflowSummary(new Workflow());

        workflowSearchResult.setResults(Collections.singletonList(workflowSummary));
        when(clientResponse.getEntity(argThat((GenericType<SearchResult<WorkflowSummary>> type) ->
                ((ParameterizedTypeImpl) type.getType()).getRawType().equals(SearchResult.class) &&
                        ((ParameterizedTypeImpl) type.getType()).getActualTypeArguments()[0].equals(WorkflowSummary.class)
        ))).thenReturn(workflowSearchResult);
        when(clientHandler.handle(argThat(argument -> argument.getURI().equals(URI.create("http://myuri:8080/workflow/search?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<WorkflowSummary> searchResult = workflowClient.search(0,10,"sort","text","my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflowSummary), searchResult.getResults());
    }

    @Test
    public void testSearchV2WithParams() {
        ClientResponse clientResponse = mock(ClientResponse.class);
        SearchResult<Workflow> workflowSearchResult = new SearchResult<>();
        workflowSearchResult.setTotalHits(1);
        Workflow workflow = new Workflow();
        workflowSearchResult.setResults(Collections.singletonList(workflow));
        when(clientResponse.getEntity(argThat((GenericType<SearchResult<Workflow>> type) ->
                ((ParameterizedTypeImpl) type.getType()).getRawType().equals(SearchResult.class) &&
                        ((ParameterizedTypeImpl) type.getType()).getActualTypeArguments()[0].equals(Workflow.class)
        ))).thenReturn(workflowSearchResult);
        when(clientHandler.handle(argThat(argument -> argument.getURI().equals(URI.create("http://myuri:8080/workflow/search-v2?start=0&size=10&sort=sort&freeText=text&query=my_complex_query")))))
                .thenReturn(clientResponse);
        SearchResult<Workflow> searchResult = workflowClient.searchV2(0,10,"sort","text","my_complex_query");
        assertEquals(1, searchResult.getTotalHits());
        assertEquals(Collections.singletonList(workflow), searchResult.getResults());
    }
}
