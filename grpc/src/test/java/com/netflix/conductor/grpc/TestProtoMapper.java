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
package com.netflix.conductor.grpc;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.proto.WorkflowTaskPb;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
