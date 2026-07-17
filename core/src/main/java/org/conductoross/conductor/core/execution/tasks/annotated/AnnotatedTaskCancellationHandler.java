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
package org.conductoross.conductor.core.execution.tasks.annotated;

import com.netflix.conductor.common.metadata.tasks.Task;

/**
 * Optional lifecycle hook for beans that expose {@code @WorkerTask} methods as embedded system
 * tasks.
 */
public interface AnnotatedTaskCancellationHandler {

    /**
     * Propagates cancellation to resources owned by an annotated task.
     *
     * @param taskType annotated task type being canceled
     * @param task current public task representation
     * @param reason cancellation reason supplied by the engine
     */
    void cancel(String taskType, Task task, String reason);
}
