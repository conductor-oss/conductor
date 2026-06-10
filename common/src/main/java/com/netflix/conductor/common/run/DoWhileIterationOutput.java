/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.common.run;

import java.util.Map;

/**
 * Represents the output for a single DO_WHILE loop iteration, returned by the paginated iterations
 * endpoint. When {@code summarized} is {@code true}, the server has replaced the full output with a
 * lightweight sentinel and {@code output} will be {@code null}.
 */
public class DoWhileIterationOutput {

    /** 1-based iteration number. */
    private int iteration;

    /**
     * Map of inner-task reference name (without iteration suffix) to that task's output data.
     * {@code null} when the iteration has been summarized.
     */
    private Map<String, Object> output;

    /**
     * Map of inner-task reference name (without iteration suffix) to that task's input data. {@code
     * null} when the task records have been pruned (e.g. via keepLastN).
     */
    private Map<String, Object> inputData;

    /**
     * Map of inner-task reference name (without iteration suffix) to that task's taskId. Used by
     * the UI to fetch logs for a specific iteration's task. {@code null} when the task records have
     * been pruned.
     */
    private Map<String, String> taskIds;

    /**
     * {@code true} when the full output for this iteration was replaced by a lightweight sentinel
     * ({@code {"_summarized": true}}) or is absent entirely.
     */
    private boolean summarized;

    public DoWhileIterationOutput() {}

    public DoWhileIterationOutput(
            int iteration,
            Map<String, Object> output,
            Map<String, Object> inputData,
            Map<String, String> taskIds,
            boolean summarized) {
        this.iteration = iteration;
        this.output = output;
        this.inputData = inputData;
        this.taskIds = taskIds;
        this.summarized = summarized;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public Map<String, Object> getInputData() {
        return inputData;
    }

    public void setInputData(Map<String, Object> inputData) {
        this.inputData = inputData;
    }

    public Map<String, String> getTaskIds() {
        return taskIds;
    }

    public void setTaskIds(Map<String, String> taskIds) {
        this.taskIds = taskIds;
    }

    public boolean isSummarized() {
        return summarized;
    }

    public void setSummarized(boolean summarized) {
        this.summarized = summarized;
    }
}
