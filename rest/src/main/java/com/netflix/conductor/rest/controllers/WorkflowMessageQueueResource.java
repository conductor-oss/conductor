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
package com.netflix.conductor.rest.controllers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.model.WorkflowMessage;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

/**
 * REST controller for the Workflow Message Queue (WMQ) push endpoint.
 *
 * <p>Only registered when {@code conductor.workflow-message-queue.enabled=true}. When the feature
 * is disabled this bean does not exist and the endpoint returns 404.
 */
@RestController
@RequestMapping(WORKFLOW)
@ConditionalOnProperty(name = "conductor.workflow-message-queue.enabled", havingValue = "true")
public class WorkflowMessageQueueResource {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(WorkflowMessageQueueResource.class);

    private final WorkflowService workflowService;
    private final WorkflowMessageQueueDAO dao;
    private final WorkflowExecutor workflowExecutor;

    public WorkflowMessageQueueResource(
            WorkflowService workflowService,
            WorkflowMessageQueueDAO dao,
            WorkflowExecutor workflowExecutor) {
        this.workflowService = workflowService;
        this.dao = dao;
        this.workflowExecutor = workflowExecutor;
    }

    @PostMapping(
            value = "/{workflowId}/messages",
            consumes = APPLICATION_JSON_VALUE,
            produces = TEXT_PLAIN_VALUE)
    @Operation(
            summary = "Push a message into a running workflow's message queue",
            description =
                    "The workflow must be in RUNNING state. The message payload is arbitrary JSON. "
                            + "Returns the generated message ID. "
                            + "If a PULL_WORKFLOW_MESSAGES task is waiting, it will be woken up immediately.")
    public ResponseEntity<String> pushMessage(
            @PathVariable("workflowId") String workflowId,
            @RequestBody Map<String, Object> payload) {

        WorkflowModel workflow;
        try {
            workflow = workflowService.getWorkflowModel(workflowId, false);
        } catch (NotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Workflow not found: " + workflowId);
        }

        if (workflow.getStatus() != WorkflowModel.Status.RUNNING) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            "Workflow "
                                    + workflowId
                                    + " is not in RUNNING state (current: "
                                    + workflow.getStatus()
                                    + ")");
        }

        String messageId = UUID.randomUUID().toString();
        WorkflowMessage message =
                new WorkflowMessage(messageId, workflowId, payload, Instant.now().toString());

        try {
            dao.push(workflowId, message);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage());
        }

        // Re-validate workflow status after push to catch TOCTOU races where the workflow
        // transitioned to a terminal state between the initial check and the push.
        WorkflowModel postPushWorkflow;
        try {
            postPushWorkflow = workflowService.getWorkflowModel(workflowId, false);
        } catch (NotFoundException e) {
            dao.delete(workflowId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Workflow not found after push: " + workflowId);
        }
        if (postPushWorkflow.getStatus() != WorkflowModel.Status.RUNNING) {
            dao.delete(workflowId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(
                            "Workflow "
                                    + workflowId
                                    + " transitioned out of RUNNING state during push (current: "
                                    + postPushWorkflow.getStatus()
                                    + ")");
        }

        // Trigger an immediate workflow evaluation so a waiting PULL_WORKFLOW_MESSAGES
        // task can be woken up without waiting for the next poll cycle.
        try {
            workflowExecutor.decide(workflowId);
        } catch (Exception e) {
            LOGGER.warn(
                    "Failed to trigger decide() for workflowId={}; sweeper will pick it up",
                    workflowId,
                    e);
        }

        return ResponseEntity.ok(messageId);
    }
}
