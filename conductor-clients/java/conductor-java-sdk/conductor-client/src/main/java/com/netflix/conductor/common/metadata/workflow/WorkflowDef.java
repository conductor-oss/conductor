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
package com.netflix.conductor.common.metadata.workflow;

import java.util.*;

import com.netflix.conductor.common.metadata.Auditable;
import com.netflix.conductor.common.metadata.SchemaDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;

import lombok.*;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowDef extends Auditable {

    public enum TimeoutPolicy {

        TIME_OUT_WF, ALERT_ONLY
    }

    private String name;

    private String description;

    private int version = 1;

    private List<WorkflowTask> tasks = new LinkedList<>();

    private List<String> inputParameters = new LinkedList<>();

    private Map<String, Object> outputParameters = new HashMap<>();

    private String failureWorkflow;

    private int schemaVersion = 2;

    // By default a workflow is restartable
    private boolean restartable = true;

    /**
     * Specify if workflow listener is enabled to invoke a callback for completed or terminated
     * workflows
     */
    private boolean workflowStatusListenerEnabled = false;

    private String ownerEmail;

    private TimeoutPolicy timeoutPolicy = TimeoutPolicy.ALERT_ONLY;

    private long timeoutSeconds;

    private Map<String, Object> variables = new HashMap<>();

    private Map<String, Object> inputTemplate = new HashMap<>();

    private String workflowStatusListenerSink;

    private RateLimitConfig rateLimitConfig;

    private SchemaDef inputSchema;

    private SchemaDef outputSchema;

    private boolean enforceSchema = true;

    private Map<String, Object> metadata = new HashMap<>();

    public String key() {
        return getKey(name, version);
    }

    public static String getKey(String name, int version) {
        return name + "." + version;
    }

    public boolean containsType(String taskType) {
        return collectTasks().stream().anyMatch(t -> t.getType().equals(taskType));
    }

    public WorkflowTask getNextTask(String taskReferenceName) {
        WorkflowTask workflowTask = getTaskByRefName(taskReferenceName);
        if (workflowTask != null && TaskType.TERMINATE.name().equals(workflowTask.getType())) {
            return null;
        }
        Iterator<WorkflowTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            WorkflowTask task = iterator.next();
            if (task.getTaskReferenceName().equals(taskReferenceName)) {
                // If taskReferenceName matches, break out
                break;
            }
            WorkflowTask nextTask = task.next(taskReferenceName, null);
            if (nextTask != null) {
                return nextTask;
            } else if (TaskType.DO_WHILE.name().equals(task.getType()) && !task.getTaskReferenceName().equals(taskReferenceName) && task.has(taskReferenceName)) {
                // If the task is child of Loop Task and at last position, return null.
                return null;
            }
            if (task.has(taskReferenceName)) {
                break;
            }
        }
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    public WorkflowTask getTaskByRefName(String taskReferenceName) {
        return collectTasks().stream().filter(workflowTask -> workflowTask.getTaskReferenceName().equals(taskReferenceName)).findFirst().orElse(null);
    }

    public List<WorkflowTask> collectTasks() {
        List<WorkflowTask> tasks = new LinkedList<>();
        for (WorkflowTask workflowTask : this.tasks) {
            tasks.addAll(workflowTask.collectTasks());
        }
        return tasks;
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WorkflowDef that = (WorkflowDef) o;
        return version == that.version && Objects.equals(name, that.name);
    }

    public int hashCode() {
        return Objects.hash(name, version);
    }

    public String toString() {
        return "WorkflowDef{" + "name='" + name + '\'' + ", description='" + description + '\'' + ", version=" + version + ", tasks=" + tasks + ", inputParameters=" + inputParameters + ", outputParameters=" + outputParameters + ", failureWorkflow='" + failureWorkflow + '\'' + ", schemaVersion=" + schemaVersion + ", restartable=" + restartable + ", workflowStatusListenerEnabled=" + workflowStatusListenerEnabled + ", ownerEmail='" + ownerEmail + '\'' + ", timeoutPolicy=" + timeoutPolicy + ", timeoutSeconds=" + timeoutSeconds + ", variables=" + variables + ", inputTemplate=" + inputTemplate + ", workflowStatusListenerSink='" + workflowStatusListenerSink + '\'' + ", rateLimitConfig=" + rateLimitConfig + ", inputSchema=" + inputSchema + ", outputSchema=" + outputSchema + ", enforceSchema=" + enforceSchema + ", metadata=" + metadata + '}';
    }
}