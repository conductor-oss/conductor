/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.model;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class BaseModel {
    @JsonIgnore private Map<String, Object> inputPayload = new HashMap<>();
    @JsonIgnore private Map<String, Object> outputPayload = new HashMap<>();

    @JsonIgnore protected Map<String, Object> input = new HashMap<>();
    @JsonIgnore protected Map<String, Object> output = new HashMap<>();

    protected String externalInputPayloadStoragePath;
    protected String externalOutputPayloadStoragePath;

    public void externalizeInput(String path) {
        this.inputPayload = this.input;
        this.input = new HashMap<>();
        this.externalInputPayloadStoragePath = path;
    }

    public void externalizeOutput(String path) {
        this.outputPayload = this.output;
        this.output = new HashMap<>();
        this.externalOutputPayloadStoragePath = path;
    }

    public void internalizeInput(Map<String, Object> data) {
        this.input = new HashMap<>();
        this.inputPayload = data;
    }

    public void internalizeOutput(Map<String, Object> data) {
        this.output = new HashMap<>();
        this.outputPayload = data;
    }

    public void addInput(String key, Object value) {
        this.input.put(key, value);
    }

    public void addInput(Map<String, Object> inputData) {
        this.input.putAll(inputData);
    }

    public void addOutput(String key, Object value) {
        this.output.put(key, value);
    }

    public void addOutput(Map<String, Object> outputData) {
        this.output.putAll(outputData);
    }

    public String getExternalInputPayloadStoragePath() {
        return externalInputPayloadStoragePath;
    }

    public void setExternalInputPayloadStoragePath(String externalInputPayloadStoragePath) {
        this.externalInputPayloadStoragePath = externalInputPayloadStoragePath;
    }

    public String getExternalOutputPayloadStoragePath() {
        return externalOutputPayloadStoragePath;
    }

    public void setExternalOutputPayloadStoragePath(String externalOutputPayloadStoragePath) {
        this.externalOutputPayloadStoragePath = externalOutputPayloadStoragePath;
    }

    @JsonIgnore
    public Map<String, Object> getInput() {
        if (!inputPayload.isEmpty() && !input.isEmpty()) {
            input.putAll(inputPayload);
            inputPayload = new HashMap<>();
            return input;
        } else if (inputPayload.isEmpty()) {
            return input;
        } else {
            return inputPayload;
        }
    }

    @JsonIgnore
    public void setInput(Map<String, Object> input) {
        if (input == null) {
            input = new HashMap<>();
        }
        this.input = input;
    }

    @JsonIgnore
    public Map<String, Object> getOutput() {
        if (!outputPayload.isEmpty() && !output.isEmpty()) {
            output.putAll(outputPayload);
            outputPayload = new HashMap<>();
            return output;
        } else if (outputPayload.isEmpty()) {
            return output;
        } else {
            return outputPayload;
        }
    }

    @JsonIgnore
    public void setOutput(Map<String, Object> output) {
        if (output == null) {
            output = new HashMap<>();
        }
        this.output = output;
    }
}
