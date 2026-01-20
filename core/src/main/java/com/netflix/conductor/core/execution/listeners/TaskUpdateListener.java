/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.core.execution.listeners;

import com.netflix.conductor.model.TaskModel;

public interface TaskUpdateListener<T> {
    String getType();

    T targetFromOptions(Object entry);

    void onTaskUpdate(TaskModel task, T options);

    default void onTaskUpdateWithOptions(TaskModel task, Object entry) {
        onTaskUpdate(task, targetFromOptions(entry));
    }

    interface Action<T> {
        Result onTaskUpdate(T target, TaskModel updated);

        enum Result {
            TARGET_UPDATED,
            TASK_UPDATED,
            BOTH_UPDATED,
            UNMODIFIED;

            public boolean targetUpdated() {
                return this == TARGET_UPDATED;
            }

            public boolean taskUpdated() {
                return this == TASK_UPDATED;
            }

            public Result or(Result other) {
                if (this == UNMODIFIED) return other;
                if (other == UNMODIFIED) return this;
                if (this == other) return this;

                return BOTH_UPDATED;
            }
        }
    }
}
