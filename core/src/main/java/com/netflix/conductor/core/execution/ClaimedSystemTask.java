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
package com.netflix.conductor.core.execution;

import com.netflix.conductor.model.TaskModel;

/**
 * An async system task claimed at poll time by {@link
 * com.netflix.conductor.service.ExecutionService#claimSystemTasks(String, int, int)}: the task has
 * been persisted as IN_PROGRESS and its queue message leased before dispatch.
 *
 * <p>The claim flips every task to IN_PROGRESS, so the executor can no longer use the current
 * status to distinguish a first execution from a re-execution; {@code preClaimStatus} carries that
 * information instead ({@code SCHEDULED} → {@code start()}, otherwise {@code execute()}).
 */
public record ClaimedSystemTask(TaskModel task, TaskModel.Status preClaimStatus) {}
