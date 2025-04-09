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
package io.orkes.conductor.client.model;

import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveScheduleRequest {

    private String createdBy = null;

    private String cronExpression = null;

    private String name = null;

    private Boolean paused = null;

    private Boolean runCatchupScheduleInstances = null;

    private Long scheduleEndTime = null;

    private Long scheduleStartTime = null;

    private StartWorkflowRequest startWorkflowRequest = null;

    private String updatedBy = null;

    private String zoneId;

    private String description = null;

    // Adding to support Backward compatibility
    @Deprecated
    public SaveScheduleRequest createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

}