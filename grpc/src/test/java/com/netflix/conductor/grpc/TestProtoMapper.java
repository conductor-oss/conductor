/*
 * Copyright 2023 Conductor authors
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
package com.netflix.conductor.grpc;

import java.util.List;
import java.util.Map;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.proto.WorkflowTaskPb;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestProtoMapper {
  private final ProtoMapper mapper = ProtoMapper.INSTANCE;

  @Test
  public void workflowTaskToProto() {
    final WorkflowTask taskWithDefaultRetryCount = new WorkflowTask();
    final WorkflowTask taskWith1RetryCount = new WorkflowTask();
    taskWith1RetryCount.setRetryCount(1);
    final WorkflowTask taskWithNoRetryCount = new WorkflowTask();
    taskWithNoRetryCount.setRetryCount(0);
    assertEquals(-1, mapper.toProto(taskWithDefaultRetryCount).getRetryCount());
    assertEquals(1, mapper.toProto(taskWith1RetryCount).getRetryCount());
    assertEquals(0, mapper.toProto(taskWithNoRetryCount).getRetryCount());
  }

  @Test
  public void workflowTaskFromProto() {
    final WorkflowTaskPb.WorkflowTask taskWithDefaultRetryCount = WorkflowTaskPb.WorkflowTask.newBuilder().build();
    final WorkflowTaskPb.WorkflowTask taskWith1RetryCount = WorkflowTaskPb.WorkflowTask.newBuilder().setRetryCount(1).build();
    final WorkflowTaskPb.WorkflowTask taskWithNoRetryCount = WorkflowTaskPb.WorkflowTask.newBuilder().setRetryCount(-1).build();
    assertEquals(Integer.valueOf(0), mapper.fromProto(taskWithDefaultRetryCount).getRetryCount());
    assertEquals(1, mapper.fromProto(taskWith1RetryCount).getRetryCount().intValue());
    assertNull(mapper.fromProto(taskWithNoRetryCount).getRetryCount());
  }

  @Test
  public void workflowTaskAgentMetadataRoundTrip() {
    WorkflowTask task = new WorkflowTask();
    Map<String, Object> agentCard =
        Map.of(
            "name", "Travel Agent",
            "skills", List.of(Map.of("id", "book-trip")));
    Map<String, Object> a2a =
        Map.of("url", "https://agent.example.com", "agentCard", agentCard);
    task.setMetadata(
        Map.of(
            "agent",
            Map.of(
                "schemaVersion",
                1.0,
                "agentType",
                "a2a",
                "resolved",
                true,
                "a2a",
                a2a)));

    WorkflowTask decoded = mapper.fromProto(mapper.toProto(task));

    assertEquals(task.getMetadata(), decoded.getMetadata());
  }

  @Test
  public void workflowTaskProtoWithoutMetadataRemainsValid() {
    WorkflowTask decoded = mapper.fromProto(WorkflowTaskPb.WorkflowTask.newBuilder().build());

    assertTrue(decoded.getMetadata().isEmpty());
  }

  private static TaskContinuation marker() {
    TaskContinuation marker = new TaskContinuation("main", 3);
    marker.setState(TaskContinuation.LEASED);
    marker.setEpoch(7);
    marker.setWorker("host-a");
    marker.setQueue("build-7:greet.v1.code");
    marker.setQueuedAt(100);
    marker.setLeasedAt(200);
    marker.setAppliedAt(300);
    marker.setAttempts(2);
    marker.setDeferred("diverged: the code changed");
    marker.setStep(9);
    marker.setRecorded(Map.of("random_0", 0.5));
    marker.setResumedWith(Map.of("count", 4.0));
    marker.setDone(true);
    marker.setResult(Map.of("greeting", "hi"));
    marker.setError(Map.of("message", "boom"));
    return marker;
  }

  private static void assertSameMarker(TaskContinuation expected, TaskContinuation actual) {
    assertEquals(expected.getLane(), actual.getLane());
    assertEquals(expected.getN(), actual.getN());
    assertEquals(expected.getState(), actual.getState());
    assertEquals(expected.getEpoch(), actual.getEpoch());
    assertEquals(expected.getWorker(), actual.getWorker());
    assertEquals(expected.getQueue(), actual.getQueue());
    assertEquals(expected.getQueuedAt(), actual.getQueuedAt());
    assertEquals(expected.getLeasedAt(), actual.getLeasedAt());
    assertEquals(expected.getAppliedAt(), actual.getAppliedAt());
    assertEquals(expected.getAttempts(), actual.getAttempts());
    assertEquals(expected.getDeferred(), actual.getDeferred());
    assertEquals(expected.getStep(), actual.getStep());
    assertEquals(expected.getRecorded(), actual.getRecorded());
    assertEquals(expected.getResumedWith(), actual.getResumedWith());
    assertEquals(expected.isDone(), actual.isDone());
    assertEquals(expected.getResult(), actual.getResult());
    assertEquals(expected.getError(), actual.getError());
  }

  @Test
  public void aTaskKeepsItsContinuationOverGrpc() {
    Task task = new Task();
    task.setContinuation(marker());
    assertSameMarker(marker(), mapper.fromProto(mapper.toProto(task)).getContinuation());
  }

  @Test
  public void aMarkerThatHasNotRunYetGoesOverGrpc() {
    // a waiting marker, and one whose result and error the code cleared: no result, no error
    TaskContinuation waiting = new TaskContinuation("main", 1);
    TaskContinuation cleared = marker();
    cleared.setResult(null);
    cleared.setError(null);
    for (TaskContinuation marker : List.of(waiting, cleared)) {
      Task task = new Task();
      task.setContinuation(marker);
      TaskContinuation back = mapper.fromProto(mapper.toProto(task)).getContinuation();
      assertTrue(back.getResult().isEmpty());
      assertTrue(back.getError().isEmpty());
      assertFalse(back.hasError());
    }
  }

  @Test
  public void aTaskWithoutAContinuationStaysWithoutOne() {
    assertNull(mapper.fromProto(mapper.toProto(new Task())).getContinuation());
  }

  @Test
  public void aWorkflowKeepsItsNativeStateOverGrpc() {
    NativeState state = new NativeState();
    state.setCodeType("greet.v1.code");
    state.setContinuationTimeoutSeconds(30);
    state.setStep(9);
    state.setBootstrap(marker());
    state.setHandlers(marker());
    state.setReplies(Map.of("u1", Map.of("value", 1.0)));
    state.setSeen(List.of("m1", "m2"));
    state.setActiveContinuation("wf|main");
    state.setActiveUntil(500);
    Workflow workflow = new Workflow();
    workflow.setNativeState(state);

    NativeState back = mapper.fromProto(mapper.toProto(workflow)).getNativeState();

    assertEquals("greet.v1.code", back.getCodeType());
    assertEquals(30, back.getContinuationTimeoutSeconds());
    assertEquals(9, back.getStep());
    assertSameMarker(marker(), back.getBootstrap());
    assertSameMarker(marker(), back.getHandlers());
    assertEquals(state.getReplies(), back.getReplies());
    assertEquals(state.getSeen(), back.getSeen());
    assertEquals("wf|main", back.getActiveContinuation());
    assertEquals(500, back.getActiveUntil());
  }

  @Test
  public void aWorkflowWithoutNativeStateStaysWithoutOne() {
    assertNull(mapper.fromProto(mapper.toProto(new Workflow())).getNativeState());
  }
}
