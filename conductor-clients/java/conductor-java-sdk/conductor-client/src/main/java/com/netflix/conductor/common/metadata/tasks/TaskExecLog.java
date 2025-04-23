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
package com.netflix.conductor.common.metadata.tasks;

import java.util.Objects;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskExecLog {

    private String log;

    private String taskId;

    private long createdTime;

    public TaskExecLog(String log) {
        this.log = log;
        this.createdTime = System.currentTimeMillis();
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TaskExecLog that = (TaskExecLog) o;
        return createdTime == that.createdTime && Objects.equals(log, that.log) && Objects.equals(taskId, that.taskId);
    }

    public int hashCode() {
        return Objects.hash(log, taskId, createdTime);
    }
}