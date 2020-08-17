package com.netflix.conductor.grpc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.proto.WorkflowTaskPb;

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
    assertEquals(new Integer(0), mapper.fromProto(taskWithDefaultRetryCount).getRetryCount());
    assertEquals(1, mapper.fromProto(taskWith1RetryCount).getRetryCount().intValue());
    assertNull(mapper.fromProto(taskWithNoRetryCount).getRetryCount());
  }
}
