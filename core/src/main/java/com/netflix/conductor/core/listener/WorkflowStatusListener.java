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
package com.netflix.conductor.core.listener;

import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.annotation.JsonValue;

/** Listener for the completed and terminated workflows */
public interface WorkflowStatusListener {

    enum WorkflowEventType {
        STARTED,
        RERAN,
        RETRIED,
        PAUSED,
        RESUMED,
        RESTARTED,
        COMPLETED,
        TERMINATED,
        FINALIZED;

        @JsonValue // Ensures correct JSON serialization
        @Override
        public String toString() {
            return name().toLowerCase(); // Convert to lowercase for consistency
        }
    }

    default void onWorkflowCompletedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowCompleted(workflow);
        }
    }

    default void onWorkflowTerminatedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowTerminated(workflow);
        }
    }

    default void onWorkflowFinalizedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowFinalized(workflow);
        }
    }

    default void onWorkflowStartedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowStarted(workflow);
        }
    }

    default void onWorkflowRestartedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowRestarted(workflow);
        }
    }

    default void onWorkflowRerunIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowRerun(workflow);
        }
    }

    default void onWorkflowRetriedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowRetried(workflow);
        }
    }

    default void onWorkflowPausedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowPaused(workflow);
        }
    }

    default void onWorkflowResumedIfEnabled(WorkflowModel workflow) {
        if (workflow.getWorkflowDefinition().isWorkflowStatusListenerEnabled()) {
            onWorkflowResumed(workflow);
        }
    }

    void onWorkflowCompleted(WorkflowModel workflow);

    void onWorkflowTerminated(WorkflowModel workflow);

    default void onWorkflowFinalized(WorkflowModel workflow) {}

    default void onWorkflowStarted(WorkflowModel workflow) {}

    default void onWorkflowRestarted(WorkflowModel workflow) {}

    default void onWorkflowRerun(WorkflowModel workflow) {}

    default void onWorkflowPaused(WorkflowModel workflow) {}

    default void onWorkflowResumed(WorkflowModel workflow) {}

    default void onWorkflowRetried(WorkflowModel workflow) {}
}
