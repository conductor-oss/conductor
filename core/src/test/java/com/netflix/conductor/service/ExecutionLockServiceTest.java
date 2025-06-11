/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.sync.Lock;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
public class ExecutionLockServiceTest {

    @Mock private Lock distributedLock;
    @Mock private ConductorProperties conductorProperties;

    private ExecutionLockService executionLockService;

    @Before
    public void setup() {
        when(conductorProperties.isWorkflowExecutionLockEnabled()).thenReturn(true);
        when(conductorProperties.getLockLeaseTime()).thenReturn(Duration.ofMillis(1000));
        when(conductorProperties.getLockTimeToTry()).thenReturn(Duration.ofMillis(1000));
        executionLockService = new ExecutionLockService(conductorProperties, distributedLock);
    }

    @Test
    public void simpleAcquireAndReleaseLock() {
        when(distributedLock.acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS))
                .thenReturn(true);
        assertDoesNotThrow(
                () -> {
                    try (var lockInstance = executionLockService.acquireLock("testId")) {
                        assertNotNull(lockInstance);
                    }
                });
        verify(distributedLock, times(1)).acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS);
        verify(distributedLock, times(1)).releaseLock("testId");
    }

    @Test
    public void simpleNestedAcquireAndReleaseLockWithSameId() {
        when(distributedLock.acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS))
                .thenReturn(true);
        assertDoesNotThrow(
                () -> {
                    try (var lockInstance = executionLockService.acquireLock("testId")) {
                        assertNotNull(lockInstance);
                        try (var lockInstanceNested = executionLockService.acquireLock("testId")) {
                            assertNotNull(lockInstanceNested);
                        }
                    }
                });
        verify(distributedLock, times(1)).acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS);
        verify(distributedLock, times(1)).releaseLock("testId");
    }

    @Test
    public void simpleNestedAcquireAndReleaseLockWithDifferentId() {
        when(distributedLock.acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS))
                .thenReturn(true);
        when(distributedLock.acquireLock("testId2", 1000, 1000, TimeUnit.MILLISECONDS))
                .thenReturn(true);
        assertDoesNotThrow(
                () -> {
                    try (var lockInstance = executionLockService.acquireLock("testId")) {
                        assertNotNull(lockInstance);
                        try (var lockInstanceNested = executionLockService.acquireLock("testId2")) {
                            assertNotNull(lockInstanceNested);
                        }
                    }
                });
        verify(distributedLock, times(1)).acquireLock("testId", 1000, 1000, TimeUnit.MILLISECONDS);
        verify(distributedLock, times(1)).acquireLock("testId2", 1000, 1000, TimeUnit.MILLISECONDS);
        verify(distributedLock, times(1)).releaseLock("testId");
        verify(distributedLock, times(1)).releaseLock("testId2");
    }

    @Test
    public void nonReleasedLockCannotBeReleasedAfterLeaseTime() throws InterruptedException {
        when(distributedLock.acquireLock("testId", 0, 0, TimeUnit.MILLISECONDS)).thenReturn(true);
        try (var lockInstance = executionLockService.acquireLock("testId", 0, 0)) {
            Thread.sleep(1);
        }
        verify(distributedLock, times(0)).releaseLock("testId");
    }
}
