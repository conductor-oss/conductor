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
package com.netflix.conductor.contribs.listener.statuschange;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.contribs.listener.StatusNotifier;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonFilter("SecretRemovalFilter")
class StatusChangeNotification extends WorkflowSummary {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatusChangePublisher.class);
    private ObjectMapper objectMapper = new ObjectMapper();
    private StatusNotifier statusNotifier;

    StatusChangeNotification(Workflow workflow) {
        super(workflow);
        Map<String, Object> variables = workflow.getVariables();
        Object statusNotifierVariable = variables.get("statusNotifier");
        if (statusNotifier != null) {
            try {
                statusNotifier =
                        objectMapper.readValue(
                                statusNotifierVariable.toString(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public StatusNotifier getStatusNotifier() {
        return statusNotifier;
    }

    String toJsonString() {
        String jsonString;
        try {
            SimpleBeanPropertyFilter theFilter =
                    SimpleBeanPropertyFilter.serializeAllExcept("input", "output");
            FilterProvider provider =
                    new SimpleFilterProvider().addFilter("SecretRemovalFilter", theFilter);
            jsonString = objectMapper.writer(provider).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to convert workflow {} id: {} to String. Exception: {}",
                    this.getWorkflowType(),
                    this.getWorkflowId(),
                    e);
            throw new RuntimeException(e);
        }
        return jsonString;
    }

    /*
     * https://github.com/Netflix/conductor/pull/2128
     * To enable Workflow/Task Summary Input/Output JSON Serialization, use the following:
     * conductor.app.summary-input-output-json-serialization.enabled=true
     */
    String toJsonStringWithInputOutput() {
        String jsonString;
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            SimpleBeanPropertyFilter emptyFilter = SimpleBeanPropertyFilter.serializeAllExcept();
            FilterProvider provider =
                    new SimpleFilterProvider().addFilter("SecretRemovalFilter", emptyFilter);
            jsonString = objectMapper.writer(provider).writeValueAsString(this);
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to convert workflow {} id: {} to String. Exception: {}",
                    this.getWorkflowType(),
                    this.getWorkflowId(),
                    e);
            throw new RuntimeException(e);
        }
        return jsonString;
    }
}
