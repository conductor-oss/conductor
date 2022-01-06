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
package com.netflix.conductor.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
public class SemaphoreUtilTest {

    @Test
    public void testBlockAfterAvailablePermitsExhausted() throws Exception {
        int threads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        SemaphoreUtil semaphoreUtil = new SemaphoreUtil(threads);

        List<CompletableFuture<Void>> futuresList = new ArrayList<>();
        IntStream.range(0, threads)
                .forEach(
                        t ->
                                futuresList.add(
                                        CompletableFuture.runAsync(
                                                () -> semaphoreUtil.acquireSlots(1),
                                                executorService)));

        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(
                        futuresList.toArray(new CompletableFuture[futuresList.size()]));

        allFutures.get();

        assertEquals(0, semaphoreUtil.availableSlots());
        assertFalse(semaphoreUtil.acquireSlots(1));

        executorService.shutdown();
    }

    @Test
    public void testAllowsPollingWhenPermitBecomesAvailable() throws Exception {
        int threads = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threads);
        SemaphoreUtil semaphoreUtil = new SemaphoreUtil(threads);

        List<CompletableFuture<Void>> futuresList = new ArrayList<>();
        IntStream.range(0, threads)
                .forEach(
                        t ->
                                futuresList.add(
                                        CompletableFuture.runAsync(
                                                () -> semaphoreUtil.acquireSlots(1),
                                                executorService)));

        CompletableFuture<Void> allFutures =
                CompletableFuture.allOf(
                        futuresList.toArray(new CompletableFuture[futuresList.size()]));
        allFutures.get();

        assertEquals(0, semaphoreUtil.availableSlots());
        semaphoreUtil.completeProcessing(1);

        assertTrue(semaphoreUtil.availableSlots() > 0);
        assertTrue(semaphoreUtil.acquireSlots(1));

        executorService.shutdown();
    }
}
