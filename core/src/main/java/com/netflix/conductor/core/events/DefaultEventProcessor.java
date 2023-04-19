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
package com.netflix.conductor.core.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventExecution.Status;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.exception.TransientException;
import com.netflix.conductor.core.execution.evaluators.Evaluator;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotify.futures.CompletableFutures;

import static com.netflix.conductor.core.utils.Utils.isTransientException;

/**
 * Event Processor is used to dispatch actions configured in the event handlers, based on incoming
 * events to the event queues.
 *
 * <p><code>Set conductor.default-event-processor.enabled=false</code> to disable event processing.
 */
@Component
@ConditionalOnProperty(
        name = "conductor.default-event-processor.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DefaultEventProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEventProcessor.class);

    private final MetadataService metadataService;
    private final ExecutionService executionService;
    private final ActionProcessor actionProcessor;

    private final ExecutorService eventActionExecutorService;
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;
    private final boolean isEventMessageIndexingEnabled;
    private final Map<String, Evaluator> evaluators;
    private final RetryTemplate retryTemplate;

    public DefaultEventProcessor(
            ExecutionService executionService,
            MetadataService metadataService,
            ActionProcessor actionProcessor,
            JsonUtils jsonUtils,
            ConductorProperties properties,
            ObjectMapper objectMapper,
            Map<String, Evaluator> evaluators,
            @Qualifier("onTransientErrorRetryTemplate") RetryTemplate retryTemplate) {
        this.executionService = executionService;
        this.metadataService = metadataService;
        this.actionProcessor = actionProcessor;
        this.objectMapper = objectMapper;
        this.jsonUtils = jsonUtils;
        this.evaluators = evaluators;
        this.retryTemplate = retryTemplate;

        if (properties.getEventProcessorThreadCount() <= 0) {
            throw new IllegalStateException(
                    "Cannot set event processor thread count to <=0. To disable event "
                            + "processing, set conductor.default-event-processor.enabled=false.");
        }
        ThreadFactory threadFactory =
                new BasicThreadFactory.Builder()
                        .namingPattern("event-action-executor-thread-%d")
                        .build();
        eventActionExecutorService =
                Executors.newFixedThreadPool(
                        properties.getEventProcessorThreadCount(), threadFactory);

        this.isEventMessageIndexingEnabled = properties.isEventMessageIndexingEnabled();
        LOGGER.info("Event Processing is ENABLED");
    }

    public void handle(ObservableQueue queue, Message msg) {
        List<EventExecution> transientFailures = null;
        boolean executionFailed = false;
        try {
            if (isEventMessageIndexingEnabled) {
                executionService.addMessage(queue.getName(), msg);
            }
            String event = queue.getType() + ":" + queue.getName();
            LOGGER.debug("Evaluating message: {} for event: {}", msg.getId(), event);
            transientFailures = executeEvent(event, msg);
        } catch (Exception e) {
            executionFailed = true;
            LOGGER.error("Error handling message: {} on queue:{}", msg, queue.getName(), e);
            Monitors.recordEventQueueMessagesError(queue.getType(), queue.getName());
        } finally {
            if (!executionFailed && CollectionUtils.isEmpty(transientFailures)) {
                queue.ack(Collections.singletonList(msg));
                LOGGER.debug("Message: {} acked on queue: {}", msg.getId(), queue.getName());
            } else if (queue.rePublishIfNoAck() || !CollectionUtils.isEmpty(transientFailures)) {
                // re-submit this message to the queue, to be retried later
                // This is needed for queues with no unack timeout, since messages are removed
                // from the queue
                queue.publish(Collections.singletonList(msg));
                LOGGER.debug("Message: {} published to queue: {}", msg.getId(), queue.getName());
            } else {
                queue.nack(Collections.singletonList(msg));
                LOGGER.debug("Message: {} nacked on queue: {}", msg.getId(), queue.getName());
            }
            Monitors.recordEventQueueMessagesHandled(queue.getType(), queue.getName());
        }
    }

    /**
     * Executes all the actions configured on all the event handlers triggered by the {@link
     * Message} on the queue If any of the actions on an event handler fails due to a transient
     * failure, the execution is not persisted such that it can be retried
     *
     * @return a list of {@link EventExecution} that failed due to transient failures.
     */
    protected List<EventExecution> executeEvent(String event, Message msg) throws Exception {
        List<EventHandler> eventHandlerList;
        List<EventExecution> transientFailures = new ArrayList<>();

        try {
            eventHandlerList = metadataService.getEventHandlersForEvent(event, true);
        } catch (TransientException transientException) {
            transientFailures.add(new EventExecution(event, msg.getId()));
            return transientFailures;
        }

        Object payloadObject = getPayloadObject(msg.getPayload());
        for (EventHandler eventHandler : eventHandlerList) {
            String condition = eventHandler.getCondition();
            String evaluatorType = eventHandler.getEvaluatorType();
            // Set default to true so that if condition is not specified, it falls through
            // to process the event.
            boolean success = true;
            if (StringUtils.isNotEmpty(condition) && evaluators.get(evaluatorType) != null) {
                Object result =
                        evaluators
                                .get(evaluatorType)
                                .evaluate(condition, jsonUtils.expand(payloadObject));
                success = ScriptEvaluator.toBoolean(result);
            } else if (StringUtils.isNotEmpty(condition)) {
                LOGGER.debug("Checking condition: {} for event: {}", condition, event);
                success = ScriptEvaluator.evalBool(condition, jsonUtils.expand(payloadObject));
            }

            if (!success) {
                String id = msg.getId() + "_" + 0;
                EventExecution eventExecution = new EventExecution(id, msg.getId());
                eventExecution.setCreated(System.currentTimeMillis());
                eventExecution.setEvent(eventHandler.getEvent());
                eventExecution.setName(eventHandler.getName());
                eventExecution.setStatus(Status.SKIPPED);
                eventExecution.getOutput().put("msg", msg.getPayload());
                eventExecution.getOutput().put("condition", condition);
                executionService.addEventExecution(eventExecution);
                LOGGER.debug(
                        "Condition: {} not successful for event: {} with payload: {}",
                        condition,
                        eventHandler.getEvent(),
                        msg.getPayload());
                continue;
            }

            CompletableFuture<List<EventExecution>> future =
                    executeActionsForEventHandler(eventHandler, msg);
            future.whenComplete(
                            (result, error) ->
                                    result.forEach(
                                            eventExecution -> {
                                                if (error != null
                                                        || eventExecution.getStatus()
                                                                == Status.IN_PROGRESS) {
                                                    transientFailures.add(eventExecution);
                                                } else {
                                                    executionService.updateEventExecution(
                                                            eventExecution);
                                                }
                                            }))
                    .get();
        }
        return processTransientFailures(transientFailures);
    }

    /**
     * Remove the event executions which failed temporarily.
     *
     * @param eventExecutions The event executions which failed with a transient error.
     * @return The event executions which failed with a transient error.
     */
    protected List<EventExecution> processTransientFailures(List<EventExecution> eventExecutions) {
        eventExecutions.forEach(executionService::removeEventExecution);
        return eventExecutions;
    }

    /**
     * @param eventHandler the {@link EventHandler} for which the actions are to be executed
     * @param msg the {@link Message} that triggered the event
     * @return a {@link CompletableFuture} holding a list of {@link EventExecution}s for the {@link
     *     Action}s executed in the event handler
     */
    protected CompletableFuture<List<EventExecution>> executeActionsForEventHandler(
            EventHandler eventHandler, Message msg) {
        List<CompletableFuture<EventExecution>> futuresList = new ArrayList<>();
        int i = 0;
        for (Action action : eventHandler.getActions()) {
            String id = msg.getId() + "_" + i++;
            EventExecution eventExecution = new EventExecution(id, msg.getId());
            eventExecution.setCreated(System.currentTimeMillis());
            eventExecution.setEvent(eventHandler.getEvent());
            eventExecution.setName(eventHandler.getName());
            eventExecution.setAction(action.getAction());
            eventExecution.setStatus(Status.IN_PROGRESS);
            if (executionService.addEventExecution(eventExecution)) {
                futuresList.add(
                        CompletableFuture.supplyAsync(
                                () ->
                                        execute(
                                                eventExecution,
                                                action,
                                                getPayloadObject(msg.getPayload())),
                                eventActionExecutorService));
            } else {
                LOGGER.warn("Duplicate delivery/execution of message: {}", msg.getId());
            }
        }
        return CompletableFutures.allAsList(futuresList);
    }

    /**
     * @param eventExecution the instance of {@link EventExecution}
     * @param action the {@link Action} to be executed for the event
     * @param payload the {@link Message#getPayload()}
     * @return the event execution updated with execution output, if the execution is
     *     completed/failed with non-transient error the input event execution, if the execution
     *     failed due to transient error
     */
    protected EventExecution execute(EventExecution eventExecution, Action action, Object payload) {
        try {
            LOGGER.debug(
                    "Executing action: {} for event: {} with messageId: {} with payload: {}",
                    action.getAction(),
                    eventExecution.getId(),
                    eventExecution.getMessageId(),
                    payload);

            // TODO: Switch to @Retryable annotation on SimpleActionProcessor.execute()
            Map<String, Object> output =
                    retryTemplate.execute(
                            context ->
                                    actionProcessor.execute(
                                            action,
                                            payload,
                                            eventExecution.getEvent(),
                                            eventExecution.getMessageId()));
            if (output != null) {
                eventExecution.getOutput().putAll(output);
            }
            eventExecution.setStatus(Status.COMPLETED);
            Monitors.recordEventExecutionSuccess(
                    eventExecution.getEvent(),
                    eventExecution.getName(),
                    eventExecution.getAction().name());
        } catch (RuntimeException e) {
            LOGGER.error(
                    "Error executing action: {} for event: {} with messageId: {}",
                    action.getAction(),
                    eventExecution.getEvent(),
                    eventExecution.getMessageId(),
                    e);
            if (!isTransientException(e)) {
                // not a transient error, fail the event execution
                eventExecution.setStatus(Status.FAILED);
                eventExecution.getOutput().put("exception", e.getMessage());
                Monitors.recordEventExecutionError(
                        eventExecution.getEvent(),
                        eventExecution.getName(),
                        eventExecution.getAction().name(),
                        e.getClass().getSimpleName());
            }
        }
        return eventExecution;
    }

    private Object getPayloadObject(String payload) {
        Object payloadObject = null;
        if (payload != null) {
            try {
                payloadObject = objectMapper.readValue(payload, Object.class);
            } catch (Exception e) {
                payloadObject = payload;
            }
        }
        return payloadObject;
    }
}
