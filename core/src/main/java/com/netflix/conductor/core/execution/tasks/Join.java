/**
 * Copyright 2016 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.core.execution.tasks;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.Task.Status;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.execution.WorkflowExecutor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Viren
 *
 */
public class Join extends WorkflowSystemTask {

    public Join() {
        super("JOIN");
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean execute(Workflow workflow, Task task, WorkflowExecutor provider) {

        boolean allDone = true;
        boolean hasFailures = false;
        StringBuilder failureReason = new StringBuilder();
        List<String> joinOn = (List<String>) task.getInputData().get("joinOn");
        if (task.isLoopOverTask()) {
            //If join is part of loop over task, wait for specific iteration to get complete
            joinOn = joinOn.stream().map(name -> TaskUtils.appendIteration(name, task.getIteration())).collect(Collectors.toList());
        }
        for (String joinOnRef : joinOn) {
            Task forkedTask = workflow.getTaskByRefName(joinOnRef);
            if (forkedTask == null) {
                //Task is not even scheduled yet
                allDone = false;
                break;
            }
            Status taskStatus = forkedTask.getStatus();
            hasFailures = !taskStatus.isSuccessful() && !forkedTask.getWorkflowTask().isOptional();
            if (hasFailures) {
                failureReason.append(forkedTask.getReasonForIncompletion()).append(" ");
            }
            task.getOutputData().put(joinOnRef, forkedTask.getOutputData());
            if (!taskStatus.isTerminal()) {
                allDone = false;
            }
            if (hasFailures) {
                break;
            }
        }
        if (allDone || hasFailures) {
            if (hasFailures) {
                task.setReasonForIncompletion(failureReason.toString());
                task.setStatus(Status.FAILED);
            } else {
                task.setStatus(Status.COMPLETED);
            }
            return true;
        }
        return false;
    }
}
