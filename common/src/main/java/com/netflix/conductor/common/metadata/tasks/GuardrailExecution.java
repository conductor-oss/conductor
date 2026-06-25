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
package com.netflix.conductor.common.metadata.tasks;

/**
 * A record of a single LLM guardrail (PII scrub) hook that ran for a task. This is observability /
 * audit data attached to the task as a first-class field (not in {@code outputData}), so it is not
 * referenceable via {@code ${...}} expressions.
 */
public class GuardrailExecution {

    /** INPUT or OUTPUT. */
    private String phase;

    /** Guardrail type: HTTP, JAVASCRIPT, WORKER, WORKFLOW. */
    private String type;

    /** URL / JS expression / worker name / workflow name. */
    private String target;

    /** FAIL or WARN. */
    private String failureMode;

    /** Text before scrubbing. */
    private String input;

    /** Text after scrubbing. */
    private String output;

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getFailureMode() {
        return failureMode;
    }

    public void setFailureMode(String failureMode) {
        this.failureMode = failureMode;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }
}
