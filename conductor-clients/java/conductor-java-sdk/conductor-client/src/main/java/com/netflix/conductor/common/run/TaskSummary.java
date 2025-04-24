/* 
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.common.run;

import com.netflix.conductor.common.metadata.tasks.Task;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskSummary {

    private String workflowId;

    private String workflowType;

    private String correlationId;

    private String scheduledTime;

    private String startTime;

    private String updateTime;

    private String endTime;

    private Task.Status status;

    private String reasonForIncompletion;

    private long executionTime;

    private long queueWaitTime;

    private String taskDefName;

    private String taskType;

    private String input;

    private String output;

    
    private String taskId;

    
    private String externalInputPayloadStoragePath;

    
    private String externalOutputPayloadStoragePath;

    
    private int workflowPriority;

    private String domain;
}