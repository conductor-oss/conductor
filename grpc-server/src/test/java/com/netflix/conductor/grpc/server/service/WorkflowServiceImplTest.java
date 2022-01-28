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
package com.netflix.conductor.grpc.server.service;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.WorkflowServicePb;
import com.netflix.conductor.proto.WorkflowPb;
import com.netflix.conductor.proto.WorkflowSummaryPb;
import com.netflix.conductor.service.WorkflowService;

import io.grpc.stub.StreamObserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class WorkflowServiceImplTest {

    private static final String WORKFLOW_ID = "anyWorkflowId";
    private static final Boolean RESUME_SUBWORKFLOW_TASKS = true;

    @Mock private WorkflowService workflowService;

    private WorkflowServiceImpl workflowServiceImpl;

    @Before
    public void init() {
        initMocks(this);
        workflowServiceImpl = new WorkflowServiceImpl(workflowService, 5000);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void givenWorkflowIdWhenRetryWorkflowThenRetriedSuccessfully() {
        // Given
        WorkflowServicePb.RetryWorkflowRequest req =
                WorkflowServicePb.RetryWorkflowRequest.newBuilder()
                        .setWorkflowId(WORKFLOW_ID)
                        .setResumeSubworkflowTasks(true)
                        .build();
        // When
        workflowServiceImpl.retryWorkflow(req, mock(StreamObserver.class));
        // Then
        verify(workflowService).retryWorkflow(WORKFLOW_ID, RESUME_SUBWORKFLOW_TASKS);
    }

    @Test
    public void searchExceptionTest() throws InterruptedException {
        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(50000)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSummarySearchResult value) {}

                    @Override
                    public void onError(Throwable t) {
                        throwable.set(t);
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        workflowServiceImpl.search(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        assertEquals(
                "INVALID_ARGUMENT: Cannot return more than 5000 results",
                throwable.get().getMessage());
    }

    @Test
    public void searchV2ExceptionTest() throws InterruptedException {
        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(50000)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSearchResult value) {}

                    @Override
                    public void onError(Throwable t) {
                        throwable.set(t);
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        workflowServiceImpl.searchV2(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        assertEquals(
                "INVALID_ARGUMENT: Cannot return more than 5000 results",
                throwable.get().getMessage());
    }

    @Test
    public void searchTest() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<WorkflowServicePb.WorkflowSummarySearchResult> result =
                new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSummarySearchResult value) {
                        result.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        WorkflowSummary workflow = new WorkflowSummary();
        SearchResult<WorkflowSummary> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(workflow));

        when(workflowService.searchWorkflows(
                        anyInt(), anyInt(), anyList(), anyString(), anyString()))
                .thenReturn(searchResult);

        workflowServiceImpl.search(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        WorkflowServicePb.WorkflowSummarySearchResult workflowSearchResult = result.get();

        assertEquals(1, workflowSearchResult.getTotalHits());
        assertEquals(
                WorkflowSummaryPb.WorkflowSummary.newBuilder().build(),
                workflowSearchResult.getResultsList().get(0));
    }

    @Test
    public void searchByTasksTest() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<WorkflowServicePb.WorkflowSummarySearchResult> result =
                new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSummarySearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSummarySearchResult value) {
                        result.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        WorkflowSummary workflow = new WorkflowSummary();
        SearchResult<WorkflowSummary> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(workflow));

        when(workflowService.searchWorkflowsByTasks(
                        anyInt(), anyInt(), anyList(), anyString(), anyString()))
                .thenReturn(searchResult);

        workflowServiceImpl.searchByTasks(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        WorkflowServicePb.WorkflowSummarySearchResult workflowSearchResult = result.get();

        assertEquals(1, workflowSearchResult.getTotalHits());
        assertEquals(
                WorkflowSummaryPb.WorkflowSummary.newBuilder().build(),
                workflowSearchResult.getResultsList().get(0));
    }

    @Test
    public void searchV2Test() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<WorkflowServicePb.WorkflowSearchResult> result = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSearchResult value) {
                        result.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        Workflow workflow = new Workflow();
        SearchResult<Workflow> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(workflow));

        when(workflowService.searchWorkflowsV2(1, 1, Collections.singletonList("strings"), "*", ""))
                .thenReturn(searchResult);

        workflowServiceImpl.searchV2(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        WorkflowServicePb.WorkflowSearchResult workflowSearchResult = result.get();

        assertEquals(1, workflowSearchResult.getTotalHits());
        assertEquals(
                WorkflowPb.Workflow.newBuilder().build(),
                workflowSearchResult.getResultsList().get(0));
    }

    @Test
    public void searchByTasksV2Test() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<WorkflowServicePb.WorkflowSearchResult> result = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("")
                        .build();

        StreamObserver<WorkflowServicePb.WorkflowSearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(WorkflowServicePb.WorkflowSearchResult value) {
                        result.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        streamAlive.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        streamAlive.countDown();
                    }
                };

        Workflow workflow = new Workflow();
        SearchResult<Workflow> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(workflow));

        when(workflowService.searchWorkflowsByTasksV2(
                        1, 1, Collections.singletonList("strings"), "*", ""))
                .thenReturn(searchResult);

        workflowServiceImpl.searchByTasksV2(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        WorkflowServicePb.WorkflowSearchResult workflowSearchResult = result.get();

        assertEquals(1, workflowSearchResult.getTotalHits());
        assertEquals(
                WorkflowPb.Workflow.newBuilder().build(),
                workflowSearchResult.getResultsList().get(0));
    }
}
