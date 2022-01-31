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
package com.netflix.conductor.contribs.listener.conductorqueue;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.dal.ModelMapper;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.listener.WorkflowStatusListener;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Publishes a {@link Message} containing a {@link WorkflowSummary} to the undlerying {@link
 * QueueDAO} implementation on a workflow completion or termination event.
 */
public class ConductorQueueStatusPublisher implements WorkflowStatusListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ConductorQueueStatusPublisher.class);
    private final QueueDAO queueDAO;
    private final ModelMapper modelMapper;
    private final ObjectMapper objectMapper;

    private final String successStatusQueue;
    private final String failureStatusQueue;
    private final String finalizeStatusQueue;

    public ConductorQueueStatusPublisher(
            QueueDAO queueDAO,
            ModelMapper modelMapper,
            ObjectMapper objectMapper,
            ConductorQueueStatusPublisherProperties properties) {
        this.queueDAO = queueDAO;
        this.modelMapper = modelMapper;
        this.objectMapper = objectMapper;
        this.successStatusQueue = properties.getSuccessQueue();
        this.failureStatusQueue = properties.getFailureQueue();
        this.finalizeStatusQueue = properties.getFinalizeQueue();
    }

    @Override
    public void onWorkflowCompleted(WorkflowModel workflow) {
        LOGGER.info("Publishing callback of workflow {} on completion ", workflow.getWorkflowId());
        queueDAO.push(successStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    @Override
    public void onWorkflowTerminated(WorkflowModel workflow) {
        LOGGER.info("Publishing callback of workflow {} on termination", workflow.getWorkflowId());
        queueDAO.push(failureStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    @Override
    public void onWorkflowFinalized(WorkflowModel workflow) {
        LOGGER.info("Publishing callback of workflow {} on finalization", workflow.getWorkflowId());
        queueDAO.push(finalizeStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    private Message workflowToMessage(WorkflowModel workflow) {
        String jsonWfSummary;
        WorkflowSummary summary = new WorkflowSummary(modelMapper.getWorkflow(workflow));
        try {
            jsonWfSummary = objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            LOGGER.error(
                    "Failed to convert WorkflowSummary: {} to String. Exception: {}", summary, e);
            throw new RuntimeException(e);
        }
        return new Message(workflow.getWorkflowId(), jsonWfSummary, null);
    }
}
