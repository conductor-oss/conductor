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
package com.netflix.conductor.core.reconciliation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.core.env.Environment;

import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.sync.Lock;
import com.netflix.conductor.dao.IndexDAO;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestWorkflowHistoryCleaner {

    private ExecutionDAOFacade executionDAOFacade;
    private IndexDAO indexDAO;
    private Lock lock;
    private WorkflowHistoryCleanupProperties properties;
    private Environment environment;

    @Before
    public void setUp() {
        executionDAOFacade = mock(ExecutionDAOFacade.class);
        indexDAO = mock(IndexDAO.class);
        lock = mock(Lock.class);

        properties = new WorkflowHistoryCleanupProperties();
        properties.setEnabled(true);
        properties.setRetentionDays(30);
        properties.setCatchUpDays(2);
        properties.setMaxIterationsPerDay(20);
        properties.setBatchPause(Duration.ZERO);
        properties.setIndexRefreshWait(Duration.ZERO);
        properties.setLockLeaseTime(Duration.ofMinutes(10));
        properties.setProcessedCacheSize(5_000);

        environment = mock(Environment.class);
        when(environment.getProperty(
                        eq("conductor.elasticsearch.index-prefix"), eq(String.class), anyString()))
                .thenReturn("conductor");
    }

    private WorkflowHistoryCleaner newCleaner() {
        WorkflowHistoryCleaner cleaner =
                new WorkflowHistoryCleaner(
                        executionDAOFacade, indexDAO, lock, properties, environment);
        // LifecycleAwareComponent extends SmartLifecycle; isRunning() returns true only after
        // start().
        cleaner.start();
        return cleaner;
    }

    @Test
    public void usesIndexPrefixToComposeIndexName() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        // retentionDays(30) + catchUpDays(2) iterates ttlDays 30 and 31.
        verify(indexDAO, times(1)).searchArchivableWorkflows("conductor_workflow", 30L);
        verify(indexDAO, times(1)).searchArchivableWorkflows("conductor_workflow", 31L);
    }

    @Test
    public void honorsExplicitIndexName() {
        properties.setWorkflowIndexName("my_custom_workflow");
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(indexDAO).searchArchivableWorkflows(eq("my_custom_workflow"), eq(30L));
    }

    @Test
    public void deletesAllReturnedWorkflowsAndReleasesLock() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        // First call returns two candidates, subsequent calls are empty.
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-1", "wf-2"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(executionDAOFacade).removeWorkflow("wf-1", false);
        verify(executionDAOFacade).removeWorkflow("wf-2", false);
        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void skipsRunWhenLockCannotBeAcquired() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        newCleaner().cleanup();

        verify(indexDAO, never()).searchArchivableWorkflows(anyString(), anyLong());
        verify(executionDAOFacade, never()).removeWorkflow(anyString(), eq(false));
        // The losing instance must not release the lock or it would break the winner's hold.
        verify(lock, never()).releaseLock(anyString());
    }

    @Test
    public void usesNonBlockingTryLockSemantics() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        // timeToTry must be 0L so a contending instance returns immediately instead of queueing.
        verify(lock)
                .acquireLock(
                        eq(properties.getLockId()), eq(0L), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void continuesAfterRemoveWorkflowFailure() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-bad", "wf-good"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());
        doThrow(new RuntimeException("boom"))
                .when(executionDAOFacade)
                .removeWorkflow("wf-bad", false);

        newCleaner().cleanup();

        verify(executionDAOFacade).removeWorkflow("wf-bad", false);
        verify(executionDAOFacade).removeWorkflow("wf-good", false);
        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void skipsWorkflowsAlreadyProcessedWhenIndexRemovalIsAsync() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // The async index delete lags, so the same ids keep coming back from the search.
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-a", "wf-b"))
                .thenReturn(Arrays.asList("wf-a", "wf-b"))
                .thenReturn(Arrays.asList("wf-a", "wf-b"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(executionDAOFacade, times(1)).removeWorkflow("wf-a", false);
        verify(executionDAOFacade, times(1)).removeWorkflow("wf-b", false);
    }

    @Test
    public void iteratesOverCatchUpRange() {
        properties.setRetentionDays(30);
        properties.setCatchUpDays(5);

        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        Set<Long> seenTtlDays = new HashSet<>();
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenAnswer(
                        (InvocationOnMock inv) -> {
                            seenTtlDays.add(inv.getArgument(1, Long.class));
                            return Collections.<String>emptyList();
                        });

        newCleaner().cleanup();

        assertEquals(new HashSet<>(Arrays.asList(30L, 31L, 32L, 33L, 34L)), seenTtlDays);
    }

    @Test
    public void boundsRecentIdCacheSoEvictedIdsGetReprocessed() {
        // Force the cache to size 2. When the async index delete lags, the oldest id (wf-a) is
        // evicted by the time the next page lands, so it should be retried.
        properties.setProcessedCacheSize(2);
        properties.setMaxIterationsPerDay(10);

        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-a", "wf-b", "wf-c"))
                .thenReturn(Arrays.asList("wf-a", "wf-b", "wf-c"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(executionDAOFacade, times(2)).removeWorkflow("wf-a", false);
        verify(executionDAOFacade, times(1)).removeWorkflow("wf-b", false);
        verify(executionDAOFacade, times(1)).removeWorkflow("wf-c", false);
    }

    @Test
    public void tryLockReturnsFalseWhenAcquireLockThrows() {
        // A lock client throwing (e.g. Redis connection drop) must not leak the exception or NPE
        // into the caller.
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("lock client unavailable"));

        newCleaner().cleanup();

        verify(indexDAO, never()).searchArchivableWorkflows(anyString(), anyLong());
        verify(executionDAOFacade, never()).removeWorkflow(anyString(), eq(false));
        verify(lock, never()).releaseLock(anyString());
    }

    @Test
    public void releasesLockEvenWhenCleanupBodyThrows() {
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenThrow(new RuntimeException("index unavailable"));

        newCleaner().cleanup();

        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void swallowsExceptionFromReleaseLock() {
        // releaseLock may throw (for example if the lock has already expired); the caller must
        // not see that exception.
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());
        doThrow(new IllegalStateException("Lock released due to expiration"))
                .when(lock)
                .releaseLock(anyString());

        newCleaner().cleanup();

        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void treatsNullCandidatesAsEmpty() {
        // An IndexDAO returning null must not NPE; the batch advances to the next ttlDays bucket.
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong())).thenReturn(null);

        newCleaner().cleanup();

        verify(executionDAOFacade, never()).removeWorkflow(anyString(), eq(false));
        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void treatsNonPositiveCatchUpDaysAsSingleDay() {
        // Misconfigured catchUpDays (0 or negative) must still process at least the retention day.
        properties.setCatchUpDays(0);
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows(anyString(), anyLong()))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(indexDAO, times(1)).searchArchivableWorkflows("conductor_workflow", 30L);
        verify(indexDAO, never()).searchArchivableWorkflows("conductor_workflow", 31L);
    }

    @Test
    public void handlesNonPositiveProcessedCacheSizeGracefully() {
        // processedCacheSize=0 should be clamped to capacity=1 instead of producing an NPE.
        properties.setProcessedCacheSize(0);
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-a", "wf-b"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        verify(executionDAOFacade).removeWorkflow("wf-a", false);
        verify(executionDAOFacade).removeWorkflow("wf-b", false);
    }

    @Test
    public void skipsRunWhenComponentStopped() {
        // After SmartLifecycle.stop() the trigger may still fire; the batch must not even attempt
        // the lock.
        WorkflowHistoryCleaner cleaner =
                new WorkflowHistoryCleaner(
                        executionDAOFacade, indexDAO, lock, properties, environment);
        cleaner.start();
        cleaner.stop();

        cleaner.cleanup();

        verify(lock, never()).acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class));
        verify(indexDAO, never()).searchArchivableWorkflows(anyString(), anyLong());
    }

    @Test
    public void abortsMidRunWhenComponentStops() {
        // stop() called mid-run cuts the next workflow and the next ttlDays bucket short.
        properties.setCatchUpDays(3);
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        WorkflowHistoryCleaner cleaner =
                new WorkflowHistoryCleaner(
                        executionDAOFacade, indexDAO, lock, properties, environment);
        cleaner.start();

        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenReturn(Arrays.asList("wf-1", "wf-2"))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 32L))
                .thenReturn(Collections.emptyList());
        doAnswer(
                        (InvocationOnMock inv) -> {
                            cleaner.stop();
                            return null;
                        })
                .when(executionDAOFacade)
                .removeWorkflow("wf-1", false);

        cleaner.cleanup();

        // wf-1 was in flight when stop() ran, so it completes; wf-2 must not be touched.
        verify(executionDAOFacade).removeWorkflow("wf-1", false);
        verify(executionDAOFacade, never()).removeWorkflow("wf-2", false);
        // The remaining catchUp buckets must also be skipped.
        verify(indexDAO, never()).searchArchivableWorkflows("conductor_workflow", 31L);
        verify(indexDAO, never()).searchArchivableWorkflows("conductor_workflow", 32L);
        // The lock still has to be released in finally.
        verify(lock).releaseLock(properties.getLockId());
    }

    @Test
    public void respectsMaxIterationsPerDay() {
        properties.setMaxIterationsPerDay(2);
        when(lock.acquireLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);

        // Each call returns a fresh id; without the safety cap this would loop forever.
        List<String> callSeq = new ArrayList<>();
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 30L))
                .thenAnswer(
                        (InvocationOnMock inv) -> {
                            String id = "wf-" + callSeq.size();
                            callSeq.add(id);
                            return Collections.singletonList(id);
                        });
        when(indexDAO.searchArchivableWorkflows("conductor_workflow", 31L))
                .thenReturn(Collections.emptyList());

        newCleaner().cleanup();

        // ttlDays=30 must be polled exactly maxIterationsPerDay (=2) times.
        verify(indexDAO, times(2)).searchArchivableWorkflows("conductor_workflow", 30L);
    }
}
