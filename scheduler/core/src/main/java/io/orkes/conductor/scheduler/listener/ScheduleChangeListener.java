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
package io.orkes.conductor.scheduler.listener;

import com.fasterxml.jackson.annotation.JsonValue;
import io.orkes.conductor.scheduler.model.WorkflowSchedule;

/** Listener for changes to workflow schedule registrations. */
public interface ScheduleChangeListener {

    enum ScheduleChangeType {
        CREATED,
        UPDATED,
        DELETED,
        PAUSED,
        RESUMED;

        @JsonValue
        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    default void onScheduleRegistered(WorkflowSchedule schedule) {}

    default void onScheduleUpdated(WorkflowSchedule schedule) {}

    default void onScheduleDeleted(String name) {}

    default void onSchedulePaused(WorkflowSchedule schedule) {}

    default void onScheduleResumed(WorkflowSchedule schedule) {}
}
