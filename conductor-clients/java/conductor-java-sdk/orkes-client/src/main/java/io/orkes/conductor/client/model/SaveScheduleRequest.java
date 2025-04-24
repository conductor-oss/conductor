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
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
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

    private String description;

    public SaveScheduleRequest createdBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public SaveScheduleRequest cronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
        return this;
    }

    public SaveScheduleRequest name(String name) {
        this.name = name;
        return this;
    }

    public SaveScheduleRequest paused(Boolean paused) {
        this.paused = paused;
        return this;
    }

    public Boolean isPaused() {
        return paused;
    }

    public SaveScheduleRequest runCatchupScheduleInstances(Boolean runCatchupScheduleInstances) {
        this.runCatchupScheduleInstances = runCatchupScheduleInstances;
        return this;
    }

    public Boolean isRunCatchupScheduleInstances() {
        return runCatchupScheduleInstances;
    }

    public SaveScheduleRequest scheduleEndTime(Long scheduleEndTime) {
        this.scheduleEndTime = scheduleEndTime;
        return this;
    }

    public SaveScheduleRequest scheduleStartTime(Long scheduleStartTime) {
        this.scheduleStartTime = scheduleStartTime;
        return this;
    }

    public SaveScheduleRequest startWorkflowRequest(StartWorkflowRequest startWorkflowRequest) {
        this.startWorkflowRequest = startWorkflowRequest;
        return this;
    }

    public SaveScheduleRequest updatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
        return this;
    }

    public SaveScheduleRequest zoneId(String zoneId) {
        this.zoneId = zoneId;
        return this;
    }

}