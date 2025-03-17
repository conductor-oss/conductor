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
package com.netflix.conductor.common.run;

import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Extended version of WorkflowSummary that retains input/output as Map */
public class WorkflowSummaryExtended extends WorkflowSummary {

    @ProtoField(id = 9) // Ensure Protobuf compatibility
    @JsonIgnore
    private Map<String, Object> inputMap;

    @ProtoField(id = 10)
    @JsonIgnore
    private Map<String, Object> outputMap;

    public WorkflowSummaryExtended(Workflow workflow) {
        super(workflow);
        if (workflow.getInput() != null) {
            this.inputMap = workflow.getInput();
        }
        if (workflow.getOutput() != null) {
            this.outputMap = workflow.getOutput();
        }
    }

    /** New method for JSON serialization */
    @JsonProperty("input")
    public Map<String, Object> getInputMap() {
        return inputMap;
    }

    /** New method for JSON serialization */
    @JsonProperty("output")
    public Map<String, Object> getOutputMap() {
        return outputMap;
    }

    public void setInputMap(Map<String, Object> inputMap) {
        this.inputMap = inputMap;
    }

    public void setOutputMap(Map<String, Object> outputMap) {
        this.outputMap = outputMap;
    }
}
