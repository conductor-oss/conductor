/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.core.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventExecution.Status;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.events.EventHandler.Action;
import com.netflix.conductor.common.utils.RetryUtil;
import com.netflix.conductor.core.config.Configuration;
import com.netflix.conductor.core.events.queue.Message;
import com.netflix.conductor.core.events.queue.ObservableQueue;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.core.utils.JsonUtils;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.service.ExecutionService;
import com.netflix.conductor.service.MetadataService;
import com.spotify.futures.CompletableFutures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Viren
 * Event Processor is used to dispatch actions based on the incoming events to execution queue.
 */
public class SimpleEventProcessor implements EventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SimpleEventProcessor.class);
    private static final String className = SimpleEventProcessor.class.getSimpleName();
    private static final int RETRY_COUNT = 3;

    private final MetadataService metadataService;
    private final ExecutionService executionService;
    private final ActionProcessor actionProcessor;
    private final EventQueues eventQueues;

    private ExecutorService executorService;
    private final Map<String, ObservableQueue> eventToQueueMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;
    private final boolean isEventMessageIndexingEnabled;

    @Inject
    public SimpleEventProcessor(ExecutionService executionService,
                                MetadataService metadataService,
                                ActionProcessor actionProcessor,
                                EventQueues eventQueues,
                                JsonUtils jsonUtils,
                                Configuration configuration,
                                ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.metadataService = metadataService;
        this.actionProcessor = actionProcessor;
        this.eventQueues = eventQueues;
        this.objectMapper = objectMapper;
        this.jsonUtils = jsonUtils;

        this.isEventMessageIndexingEnabled = configuration.isEventMessageIndexingEnabled();
        int executorThreadCount = configuration.getIntProperty("workflow.event.processor.thread.count", 2);
        if (executorThreadCount > 0) {
            executorService = Executors.newFixedThreadPool(executorThreadCount);
            refresh();
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::refresh, 60, 60, TimeUnit.SECONDS);
            logger.info("Event Processing is ENABLED. executorThreadCount set to {}", executorThreadCount);
        } else {
            logger.warn("Event processing is DISABLED. executorThreadCount set to {}", executorThreadCount);
        }
    }

    /**
     * @return Returns a map of queues which are active.  Key is event name and value is queue URI
     */
    public Map<String, String> getQueues() {
        Map<String, String> queues = new HashMap<>();
        eventToQueueMap.forEach((key, value) -> queues.put(key, value.getName()));
        return queues;
    }

    public Map<String, Map<String, Long>> getQueueSizes() {
        Map<String, Map<String, Long>> queues = new HashMap<>();
        eventToQueueMap.forEach((key, value) -> {
            Map<String, Long> size = new HashMap<>();
            size.put(value.getName(), value.size());
            queues.put(key, size);
        });
        return queues;
    }

    private void refresh() {
        try {
            Set<String> events = metadataService.getAllEventHandlers().stream()
                    .map(EventHandler::getEvent)
                    .collect(Collectors.toSet());

            List<ObservableQueue> createdQueues = new LinkedList<>();
            events.forEach(event -> eventToQueueMap.computeIfAbsent(event, s -> {
                        ObservableQueue q = eventQueues.getQueue(event);
                        createdQueues.add(q);
                        return q;
                    }
            ));

            // start listening on all of the created queues
            createdQueues.stream()
                    .filter(Objects::nonNull)
                    .forEach(this::listen);

        } catch (Exception e) {
            Monitors.error(className, "refresh");
            logger.error("refresh event queues failed", e);
        }
    }

    private void listen(ObservableQueue queue) {
        queue.observe().subscribe((Message msg) -> handle(queue, msg));
    }

    private void handle(ObservableQueue queue, Message msg) {
        try {
            if (isEventMessageIndexingEnabled) {
                executionService.addMessage(queue.getName(), msg);
            }
            String event = queue.getType() + ":" + queue.getName();
            logger.debug("Evaluating message: {} for event: {}", msg.getId(), event);
            List<EventExecution> transientFailures = executeEvent(event, msg);

            if (transientFailures.isEmpty()) {
                queue.ack(Collections.singletonList(msg));
                logger.debug("Message: {} acked on queue: {}", msg.getId(), queue.getName());
            } else if (queue.rePublishIfNoAck()) {
                // re-submit this message to the queue, to be retried later
                // This is needed for queues with no unack timeout, since messages are removed from the queue
                queue.publish(Collections.singletonList(msg));
                logger.debug("Message: {} published to queue: {}", msg.getId(), queue.getName());
            }
        } catch (Exception e) {
            logger.error("Error handling message: {} on queue:{}", msg, queue.getName(), e);
            Monitors.recordEventQueueMessagesError(queue.getType(), queue.getName());
        } finally {
            Monitors.recordEventQueueMessagesHandled(queue.getType(), queue.getName());
        }
    }

    /**
     * Executes all the actions configured on all the event handlers triggered by the {@link Message} on the queue
     * If any of the actions on an event handler fails due to a transient failure, the execution is not persisted such that it can be retried
     *
     * @return a list of {@link EventExecution} that failed due to transient failures.
     */
    private List<EventExecution> executeEvent(String event, Message msg) throws Exception {
        List<EventHandler> eventHandlerList = metadataService.getEventHandlersForEvent(event, true);
        Object payloadObject = getPayloadObject(msg.getPayload());

        List<EventExecution> transientFailures = new ArrayList<>();
        for (EventHandler eventHandler : eventHandlerList) {
            String condition = eventHandler.getCondition();
            if (StringUtils.isNotEmpty(condition)) {
                logger.debug("Checking condition: {} for event: {}", condition, event);
                Boolean success = ScriptEvaluator.evalBool(condition, jsonUtils.expand(payloadObject));
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
                    logger.debug("Condition: {} not successful for event: {} with payload: {}", condition, eventHandler.getEvent(), msg.getPayload());
                    continue;
                }
            }

            CompletableFuture<List<EventExecution>> future = executeActionsForEventHandler(eventHandler, msg);
            future.whenComplete((result, error) -> result.forEach(eventExecution -> {
                if (error != null || eventExecution.getStatus() == Status.IN_PROGRESS) {
                    executionService.removeEventExecution(eventExecution);
                    transientFailures.add(eventExecution);
                } else {
                    executionService.updateEventExecution(eventExecution);
                }
            })).get();
        }
        return transientFailures;
    }

    /**
     * @param eventHandler the {@link EventHandler} for which the actions are to be executed
     * @param msg          the {@link Message} that triggered the event
     * @return a {@link CompletableFuture} holding a list of {@link EventExecution}s for the {@link Action}s executed in the event handler
     */
    private CompletableFuture<List<EventExecution>> executeActionsForEventHandler(EventHandler eventHandler, Message msg) {
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
                futuresList.add(CompletableFuture.supplyAsync(() -> execute(eventExecution, action, getPayloadObject(msg.getPayload())), executorService));
            } else {
                logger.warn("Duplicate delivery/execution of message: {}", msg.getId());
            }
        }
        return CompletableFutures.allAsList(futuresList);
    }

    /**
     * @param eventExecution the instance of {@link EventExecution}
     * @param action         the {@link Action} to be executed for the event
     * @param payload        the {@link Message#getPayload()}
     * @return the event execution updated with execution output, if the execution is completed/failed with non-transient error
     * the input event execution, if the execution failed due to transient error
     */
    @VisibleForTesting
    EventExecution execute(EventExecution eventExecution, Action action, Object payload) {
        try {
            String methodName = "executeEventAction";
            String description = String.format("Executing action: %s for event: %s with messageId: %s with payload: %s", action.getAction(), eventExecution.getId(), eventExecution.getMessageId(), payload);
            logger.debug(description);

            Map<String, Object> output = new RetryUtil<Map<String, Object>>().retryOnException(() -> actionProcessor.execute(action, payload, eventExecution.getEvent(), eventExecution.getMessageId()),
                    this::isTransientException, null, RETRY_COUNT, description, methodName);
            if (output != null) {
                eventExecution.getOutput().putAll(output);
            }
            eventExecution.setStatus(Status.COMPLETED);
            Monitors.recordEventExecutionSuccess(eventExecution.getEvent(), eventExecution.getName(), eventExecution.getAction().name());
        } catch (RuntimeException e) {
            logger.error("Error executing action: {} for event: {} with messageId: {}", action.getAction(), eventExecution.getEvent(), eventExecution.getMessageId(), e);
            if (!isTransientException(e.getCause())) {
                // not a transient error, fail the event execution
                eventExecution.setStatus(Status.FAILED);
                eventExecution.getOutput().put("exception", e.getMessage());
                Monitors.recordEventExecutionError(eventExecution.getEvent(), eventExecution.getName(), eventExecution.getAction().name(), e.getClass().getSimpleName());
            }
        }
        return eventExecution;
    }

    /**
     * Used to determine if the exception is thrown due to a transient failure
     * and the operation is expected to succeed upon retrying.
     *
     * @param throwableException the exception that is thrown
     * @return true - if the exception is a transient failure
     * false - if the exception is non-transient
     */
    private boolean isTransientException(Throwable throwableException) {
        if (throwableException != null) {
            return !((throwableException instanceof UnsupportedOperationException) ||
                    (throwableException instanceof ApplicationException && ((ApplicationException) throwableException).getCode() != ApplicationException.Code.BACKEND_ERROR));
        }
        return true;
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
