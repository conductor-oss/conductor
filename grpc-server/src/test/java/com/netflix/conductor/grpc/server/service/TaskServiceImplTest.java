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

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.SearchResult;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.grpc.SearchPb;
import com.netflix.conductor.grpc.TaskServicePb;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.proto.TaskSummaryPb;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.TaskService;

import io.grpc.stub.StreamObserver;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class TaskServiceImplTest {

    @Mock private TaskService taskService;

    @Mock private ExecutionService executionService;

    private TaskServiceImpl taskServiceImpl;

    @Before
    public void init() {
        initMocks(this);
        taskServiceImpl = new TaskServiceImpl(executionService, taskService, 5000);
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
                        .setFreeText("*")
                        .build();

        StreamObserver<TaskServicePb.TaskSummarySearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(TaskServicePb.TaskSummarySearchResult value) {}

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

        taskServiceImpl.search(req, streamObserver);

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
                        .setFreeText("*")
                        .build();

        StreamObserver<TaskServicePb.TaskSearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(TaskServicePb.TaskSearchResult value) {}

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

        taskServiceImpl.searchV2(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        assertEquals(
                "INVALID_ARGUMENT: Cannot return more than 5000 results",
                throwable.get().getMessage());
    }

    @Test
    public void searchTest() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<TaskServicePb.TaskSummarySearchResult> result = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("*")
                        .build();

        StreamObserver<TaskServicePb.TaskSummarySearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(TaskServicePb.TaskSummarySearchResult value) {
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

        TaskSummary taskSummary = new TaskSummary();
        SearchResult<TaskSummary> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(taskSummary));

        when(taskService.search(1, 1, "strings", "*", "")).thenReturn(searchResult);

        taskServiceImpl.search(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        TaskServicePb.TaskSummarySearchResult taskSummarySearchResult = result.get();

        assertEquals(1, taskSummarySearchResult.getTotalHits());
        assertEquals(
                TaskSummaryPb.TaskSummary.newBuilder().build(),
                taskSummarySearchResult.getResultsList().get(0));
    }

    @Test
    public void searchV2Test() throws InterruptedException {

        CountDownLatch streamAlive = new CountDownLatch(1);
        AtomicReference<TaskServicePb.TaskSearchResult> result = new AtomicReference<>();

        SearchPb.Request req =
                SearchPb.Request.newBuilder()
                        .setStart(1)
                        .setSize(1)
                        .setSort("strings")
                        .setQuery("")
                        .setFreeText("*")
                        .build();

        StreamObserver<TaskServicePb.TaskSearchResult> streamObserver =
                new StreamObserver<>() {
                    @Override
                    public void onNext(TaskServicePb.TaskSearchResult value) {
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

        Task task = new Task();
        SearchResult<Task> searchResult = new SearchResult<>();
        searchResult.setTotalHits(1);
        searchResult.setResults(Collections.singletonList(task));

        when(taskService.searchV2(1, 1, "strings", "*", "")).thenReturn(searchResult);

        taskServiceImpl.searchV2(req, streamObserver);

        streamAlive.await(10, TimeUnit.MILLISECONDS);

        TaskServicePb.TaskSearchResult taskSearchResult = result.get();

        assertEquals(1, taskSearchResult.getTotalHits());
        assertEquals(
                TaskPb.Task.newBuilder().setCallbackFromWorker(true).build(),
                taskSearchResult.getResultsList().get(0));
    }
}
