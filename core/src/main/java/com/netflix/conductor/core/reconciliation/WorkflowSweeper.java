/*
 *  Copyright 2021 Netflix, Inc.
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.core.reconciliation;

import java.util.concurrent.CompletableFuture;

public interface WorkflowSweeper {

    /**
     * Evaluate the given workflow through the state machine
     *
     * @param workflowId id of the workflow to be evaluated
     */
    void sweep(String workflowId);

    /**
     * Asynchronously perform the workflow evaluation for the given workflow
     *
     * @param workflowId id of the workflow to be evaluated
     */
    CompletableFuture<Void> sweepAsync(String workflowId);
}
