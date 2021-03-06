/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 *
 */
package com.netflix.conductor.contribs.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.execution.WorkflowStatusListener;
import com.netflix.conductor.dao.QueueDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Collections;

/**
 * Publishes a @see Message containing a @see WorkflowSummary to a DynoQueue on a workflow completion or termination event.
 */
public class DynoQueueStatusPublisher implements WorkflowStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynoQueueStatusPublisher.class);
    private final QueueDAO queueDAO;
    private final ObjectMapper objectMapper;
    private final Configuration config;
    private final String successStatusQueue;
    private final String failureStatusQueue;
    private final String finalizeStatusQueue;

    @Inject
    public DynoQueueStatusPublisher(QueueDAO queueDAO, ObjectMapper objectMapper, Configuration config) {
        this.queueDAO = queueDAO;
        this.objectMapper = objectMapper;
        this.config = config;
        this.successStatusQueue = config.getProperty("workflowstatuslistener.publisher.success.queue", "_callbackSuccessQueue");
        this.failureStatusQueue = config.getProperty("workflowstatuslistener.publisher.failure.queue", "_callbackFailureQueue");
        this.finalizeStatusQueue = config.getProperty("workflowstatuslistener.publisher.finalize.queue", "_callbackFinalizeQueue");
    }

    @Override
    public void onWorkflowCompleted(Workflow workflow) {
        LOGGER.info("Publishing callback of workflow {} on completion ", workflow.getWorkflowId());
        queueDAO.push(successStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    @Override
    public void onWorkflowTerminated(Workflow workflow) {
        LOGGER.info("Publishing callback of workflow {} on termination", workflow.getWorkflowId());
        queueDAO.push(failureStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    @Override
    public void onWorkflowFinalized(Workflow workflow) {
        LOGGER.info("Publishing callback of workflow {} on finalization", workflow.getWorkflowId());
        queueDAO.push(finalizeStatusQueue, Collections.singletonList(workflowToMessage(workflow)));
    }

    private Message workflowToMessage(Workflow workflow) {
        String jsonWfSummary;
        WorkflowSummary summary = new WorkflowSummary(workflow);
        try {
            jsonWfSummary = objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            LOGGER.error("Failed to convert WorkflowSummary: {} to String. Exception: {}", summary, e);
            throw new RuntimeException(e);
        }
        return new Message(workflow.getWorkflowId(), jsonWfSummary, null);
    }

}
