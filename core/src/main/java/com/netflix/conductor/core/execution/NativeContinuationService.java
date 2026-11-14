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
package com.netflix.conductor.core.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.execution.evaluators.NativeCodeEvaluator;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Drives the code of a native (code-defined) workflow with the tasks the code asked for and nothing
 * else.
 *
 * <p>The code only ever pauses on a task. That task carries a {@link TaskContinuation} marker:
 * which lane of the code waits on it and which continuation runs when it finishes. When the decider
 * evaluates the finished task, the marker becomes runnable and a message with the task's id goes to
 * the continuation queue, which workers poll with the ordinary task batch poll. The poll leases the
 * marker; the worker replays the code and finishes with the ordinary task update, whose output
 * carries the next piece of the definition. The completion is applied under the workflow's lock:
 * the fragment is appended after the task, variables and recorded values are written onto the
 * marker, and the task is marked not executed so the decider schedules what follows it. The first
 * continuation (before any task exists) and the handler lane (which schedules no tasks) have their
 * markers on the workflow's {@link NativeState}.
 */
@Component
public class NativeContinuationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeContinuationService.class);

    private static final int PROTOCOL = 2;

    /**
     * How a marker's {@code deferred} reason starts while the code does not match its recording.
     */
    private static final String DIVERGED_PREFIX = "diverged: ";

    /** How a marker's {@code deferred} reason starts while the handler lane has to run first. */
    private static final String WAITING_FOR_HANDLERS_PREFIX = "waiting for handlers: ";

    /** The longest wait before a continuation that diverged or failed is offered again. */
    private static final int MAX_RETRY_DELAY_SECONDS = 60;

    /** How many queued messages one handler run receives. */
    private static final int HANDLER_BATCH_SIZE = 10;

    private static final String MAIN_LANE = "main";

    /**
     * Task output key holding how a task failed (FAILED, TIMED_OUT, ...) once its status has become
     * COMPLETED_WITH_ERRORS, so that the code can still be told.
     */
    public static final String OUTPUT_FAILED_STATUS = "conductor.failedStatus";

    private static final String HANDLER_LANE = "handlers";

    /** Entry metadata naming the continuation that appended the entry, as {@code lane#n}. */
    public static final String APPENDED_BY = "appendedBy";

    public static final String OUTPUT_PROTOCOL = "protocol";
    private static final String OUTPUT_FRAGMENT = "fragment";
    private static final String OUTPUT_VARIABLES = "variables";
    private static final String OUTPUT_RESUMED_WITH = "resumedWith";
    private static final String OUTPUT_RECORDED = "recorded";
    private static final String OUTPUT_EPOCH = "epoch";
    private static final String OUTPUT_N = "n";
    private static final String OUTPUT_DONE = "done";
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_ERROR = "error";
    private static final String OUTPUT_RELEASES = "releases";
    private static final String OUTPUT_REPLIES = "replies";
    private static final String OUTPUT_SEEN = "seen";
    private static final String INPUT_MESSAGES = "messages";
    private static final String METADATA_LANE = "lane";
    private static final String METADATA_CONTINUATION = "continuation";
    private static final String SYNTHETIC_SEPARATOR = "|";

    private static final TypeReference<List<WorkflowTask>> FRAGMENT_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ConductorProperties properties;
    private final MetadataMapperService metadataMapperService;
    private final Map<String, TaskMapper> taskMappers;

    private QueueDAO queueDAO;
    private ExecutionDAOFacade executionDAOFacade;
    private WorkflowMessageQueueDAO workflowMessageQueueDAO;

    public NativeContinuationService(
            ObjectMapper objectMapper,
            ConductorProperties properties,
            MetadataMapperService metadataMapperService,
            @Qualifier("taskMappersByTaskType") Map<String, TaskMapper> taskMappers) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metadataMapperService = metadataMapperService;
        this.taskMappers = taskMappers;
    }

    @Autowired
    public void setQueueDAO(QueueDAO queueDAO) {
        this.queueDAO = queueDAO;
    }

    @Autowired
    public void setExecutionDAOFacade(ExecutionDAOFacade executionDAOFacade) {
        this.executionDAOFacade = executionDAOFacade;
    }

    @Autowired(required = false)
    public void setWorkflowMessageQueueDAO(WorkflowMessageQueueDAO workflowMessageQueueDAO) {
        this.workflowMessageQueueDAO = workflowMessageQueueDAO;
    }

    // ------------------------------------------------------------------ identity

    public static boolean isNative(WorkflowDef def) {
        return def != null && def.getMetadata() != null && def.getMetadata().containsKey("native");
    }

    public static boolean isNative(WorkflowModel workflow) {
        return isNative(workflow.getWorkflowDefinition());
    }

    /**
     * The marker an entry the code paused on names ({@code metadata.continuation}: its lane and
     * number), new and waiting for the task; null when it names none or a malformed one.
     */
    public static TaskContinuation markerOf(WorkflowTask entry) {
        Object raw =
                entry.getMetadata() == null ? null : entry.getMetadata().get(METADATA_CONTINUATION);
        return raw instanceof Map<?, ?> m && m.get("lane") != null && m.get("n") instanceof Number n
                ? new TaskContinuation(String.valueOf(m.get("lane")), n.intValue())
                : null;
    }

    private static String laneOf(WorkflowTask entry) {
        Object lane = entry.getMetadata() == null ? null : entry.getMetadata().get(METADATA_LANE);
        return lane == null ? null : lane.toString();
    }

    /** The id of the continuation message of a lane without a task: the bootstrap or handlers. */
    private static String syntheticId(String workflowId, String lane) {
        return workflowId + SYNTHETIC_SEPARATOR + lane;
    }

    public static boolean isSyntheticId(String taskId) {
        return taskId != null && taskId.contains(SYNTHETIC_SEPARATOR);
    }

    private static String workflowIdOf(String syntheticId) {
        return syntheticId.substring(0, syntheticId.indexOf(SYNTHETIC_SEPARATOR));
    }

    private static String laneOfSynthetic(String syntheticId) {
        return syntheticId.substring(syntheticId.indexOf(SYNTHETIC_SEPARATOR) + 1);
    }

    /** The marker of the bootstrap or handler lane a synthetic id names. */
    public static TaskContinuation syntheticMarkerOf(WorkflowModel workflow, String syntheticId) {
        return syntheticMarker(workflow, laneOfSynthetic(syntheticId));
    }

    private static TaskContinuation syntheticMarker(WorkflowModel workflow, String lane) {
        NativeState state = workflow.getNativeState();
        if (state == null) {
            return null;
        }
        return MAIN_LANE.equals(lane) ? state.getBootstrap() : state.getHandlers();
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Sets up a fresh execution: the engine state from the definition's {@code native} metadata,
     * the bootstrap continuation runnable, the handler lane idle. Used at start and restart.
     */
    @SuppressWarnings("unchecked")
    public void initialize(WorkflowModel workflow) {
        // The registered definition may carry the graph the SDK discovered from the code, for
        // the definition view and inventory; the execution's own copy starts empty and holds
        // only what the code actually calls.
        if (workflow.getWorkflowDefinition() != null
                && workflow.getWorkflowDefinition().getTasks() != null
                && !workflow.getWorkflowDefinition().getTasks().isEmpty()) {
            workflow.getWorkflowDefinition().setTasks(new ArrayList<>());
        }
        WorkflowDef def = workflow.getWorkflowDefinition();
        Map<String, Object> meta = (Map<String, Object>) def.getMetadata().get("native");
        NativeState state = new NativeState();
        state.setCodeType(
                meta.get("codeType") != null
                        ? meta.get("codeType").toString()
                        : def.getName() + ".v" + def.getVersion() + ".code");
        state.setContinuationTimeoutSeconds(
                meta.get("continuationTimeoutSeconds") instanceof Number t ? t.intValue() : 30);
        TaskContinuation bootstrap = new TaskContinuation(MAIN_LANE, 1);
        bootstrap.setState(TaskContinuation.RUNNABLE);
        bootstrap.setQueuedAt(System.currentTimeMillis());
        state.setBootstrap(bootstrap);
        if (Boolean.TRUE.equals(meta.get("handlers"))) {
            state.setHandlers(new TaskContinuation(HANDLER_LANE, 1));
        }
        workflow.setNativeState(state);
    }

    /** What a decide must save and push for the native markers of an execution. */
    public static final class Wakeups {
        public final List<TaskModel> touchedTasks = new ArrayList<>();
        public boolean workflowTouched;
        public final List<Offer> offers = new ArrayList<>();
    }

    /** A continuation's message, pushed to its queue once the offer is saved. */
    public record Offer(String messageId, String queue, int delaySeconds) {}

    /**
     * Repairs and offers, on every decide: a waiting marker on an evaluated task becomes runnable
     * (a crash between the evaluation and the save), the handler lane becomes runnable when
     * messages are queued (a push wakes it this way), and the execution's slot goes to the runnable
     * continuation that has waited longest. The slot holder's message is pushed again, which is
     * idempotent and repairs a lost message; a holder offered in a queue no worker polls any more
     * is offered again, at a new epoch, in the queue resolved now, once its lease has run out.
     *
     * <p>Only a decide, under its lock, gives out the slot, so a poll hands out what was offered
     * without a lock or a write.
     */
    public Wakeups repairAndWake(WorkflowModel workflow) {
        Wakeups wakeups = new Wakeups();
        NativeState state = workflow.getNativeState();
        if (state == null) {
            return wakeups;
        }
        long now = System.currentTimeMillis();
        List<TaskModel> latest = latestAttempts(workflow);
        for (TaskModel task : latest) {
            TaskContinuation marker = task.getContinuation();
            if (marker == null) {
                continue;
            }
            if (marker.isWaiting() && task.getStatus().isTerminal() && task.isExecuted()) {
                marker.setState(TaskContinuation.RUNNABLE);
                marker.setQueuedAt(now);
                wakeups.touchedTasks.add(task);
            }
        }
        TaskContinuation handlers = state.getHandlers();
        if (handlers != null
                && handlers.isWaiting()
                && workflowMessageQueueDAO != null
                && workflowMessageQueueDAO.size(workflow.getWorkflowId()) > 0) {
            handlers.setState(TaskContinuation.RUNNABLE);
            handlers.setQueuedAt(now);
            wakeups.workflowTouched = true;
        }

        String active = state.getActiveContinuation();
        if (active != null) {
            TaskModel holderTask = isSyntheticId(active) ? null : taskById(latest, active);
            TaskContinuation holder =
                    isSyntheticId(active)
                            ? syntheticMarker(workflow, laneOfSynthetic(active))
                            : holderTask == null ? null : holderTask.getContinuation();
            if (holder != null && holder.isLeased()) {
                String queue = queueName(workflow);
                if (queue.equals(holder.getQueue()) || now <= state.getActiveUntil()) {
                    // pushed again if lost; a worker that dies leaves it to the queue's redelivery
                    wakeups.offers.add(new Offer(active, holder.getQueue(), 0));
                    return wakeups;
                }
                // offered in a queue nobody polls any more (its domain's workers went away)
                queueDAO.remove(holder.getQueue(), active);
                wakeups.offers.add(offer(workflow, holder, active, 0));
                touch(wakeups, holderTask);
                return wakeups;
            }
            state.setActiveContinuation(null); // its continuation is no longer leased
            state.setActiveUntil(0);
            wakeups.workflowTouched = true;
        }

        boolean handlersOpen = handlers != null && (handlers.isRunnable() || handlers.isLeased());
        TaskModel nextTask = null;
        TaskContinuation next = null;
        String nextId = null;
        for (TaskModel task : latest) {
            TaskContinuation marker = task.getContinuation();
            if (offerable(marker, handlersOpen)
                    && (next == null || marker.getQueuedAt() < next.getQueuedAt())) {
                nextTask = task;
                next = marker;
                nextId = task.getTaskId();
            }
        }
        for (TaskContinuation marker : new TaskContinuation[] {state.getBootstrap(), handlers}) {
            if (offerable(marker, handlersOpen)
                    && (next == null || marker.getQueuedAt() < next.getQueuedAt())) {
                nextTask = null;
                next = marker;
                nextId = syntheticId(workflow.getWorkflowId(), marker.getLane());
            }
        }
        if (next != null) {
            wakeups.offers.add(offer(workflow, next, nextId, 0));
            touch(wakeups, nextTask);
        }
        return wakeups;
    }

    /**
     * Runnable, or leased without holding the slot (left by a crash), and not waiting for the
     * handler lane while that lane has work.
     */
    private static boolean offerable(TaskContinuation marker, boolean handlersOpen) {
        return marker != null
                && (marker.isRunnable() || marker.isLeased())
                && !(handlersOpen && waitsForHandlers(marker));
    }

    private static void touch(Wakeups wakeups, TaskModel task) {
        wakeups.workflowTouched = true; // the slot is on the workflow row
        if (task != null) {
            wakeups.touchedTasks.add(task);
        }
    }

    private static TaskModel taskById(List<TaskModel> tasks, String taskId) {
        for (TaskModel task : tasks) {
            if (taskId.equals(task.getTaskId())) {
                return task;
            }
        }
        return null;
    }

    /**
     * Gives the execution's slot to a continuation: its marker is leased, at a new epoch, from the
     * moment its message is delivered for the continuation timeout, in the queue resolved now. In
     * memory, under the decide's lock; the caller saves the workflow row (and the task, for a
     * task's marker) and then pushes the returned offer.
     */
    public Offer offer(
            WorkflowModel workflow, TaskContinuation marker, String messageId, int delaySeconds) {
        NativeState state = workflow.getNativeState();
        long deliverable = System.currentTimeMillis() + delaySeconds * 1000L;
        String queue = queueName(workflow);
        state.setActiveContinuation(messageId);
        state.setActiveUntil(deliverable + state.getContinuationTimeoutSeconds() * 1000L);
        marker.setState(TaskContinuation.LEASED);
        marker.setEpoch(marker.getEpoch() + 1);
        marker.setWorker(null);
        marker.setLeasedAt(deliverable);
        marker.setQueue(queue);
        marker.setAttempts(marker.getAttempts() + 1);
        // why it was deferred stays until it applies: visible through the backoff, and a
        // retry's wait is not counted as waiting for a worker
        return new Offer(messageId, queue, delaySeconds);
    }

    private static boolean waitsForHandlers(TaskContinuation marker) {
        return marker.getDeferred() != null
                && marker.getDeferred().startsWith(WAITING_FOR_HANDLERS_PREFIX);
    }

    /** Pushes the messages of saved offers, idempotently. */
    public void push(WorkflowModel workflow, List<Offer> offers) {
        if (queueDAO == null) {
            return;
        }
        for (Offer offer : offers) {
            queueDAO.pushIfNotExists(
                    offer.queue(), offer.messageId(), workflow.getPriority(), offer.delaySeconds());
        }
    }

    /** 1, 2, 4, ... seconds by how many times the continuation has been offered, capped. */
    private static int retryDelaySeconds(TaskContinuation marker) {
        int doublings = Math.min(Math.max(marker.getAttempts() - 1, 0), 6);
        return Math.min(1 << doublings, MAX_RETRY_DELAY_SECONDS);
    }

    /**
     * The continuation queue of an execution: the shared queue of its code type, unless the
     * workflow was started with task domains for the code type (or {@code *}), which are resolved
     * as for any task: the first domain with an active poller, with the usual fallback.
     */
    public String queueName(WorkflowModel workflow) {
        NativeState state = workflow.getNativeState();
        String domain = null;
        Map<String, String> taskToDomain = workflow.getTaskToDomain();
        if (executionDAOFacade != null && taskToDomain != null) {
            String domains = taskToDomain.getOrDefault(state.getCodeType(), taskToDomain.get("*"));
            if (domains != null && !domains.isBlank()) {
                long activeSince =
                        System.currentTimeMillis()
                                - properties.getActiveWorkerLastPollTimeout().toMillis();
                domain =
                        org.conductoross.conductor.core.execution.ExecutorUtils.activeDomain(
                                domains.split(","),
                                d ->
                                        executionDAOFacade.getTaskPollDataByDomain(
                                                state.getCodeType(), d),
                                pollData -> pollData.getLastPollTime() > activeSince);
            }
        }
        return QueueUtils.getQueueName(state.getCodeType(), domain, null, null);
    }

    // ------------------------------------------------------------------ poll

    /**
     * Hands out the continuation a popped message names, or returns null when the message is stale.
     * Called from the task poll for continuation queues. Takes no lock and writes nothing: a decide
     * offered the continuation (leased its marker and gave it the slot) before pushing the message,
     * so the poll only checks that the message is the one offered.
     *
     * <p>For a task, the task itself is returned with its marker; for the bootstrap and handler
     * lanes a task that is not persisted is built from the workflow. The message stays
     * unacknowledged for the continuation timeout, so the queue redelivers it if the worker dies;
     * the next worker runs the same offer, and the first completion applies.
     *
     * @param task the task the message names, as the poll read it; null for a synthetic id
     */
    public Task lease(String queueName, String messageId, TaskModel task, String workerId) {
        String workflowId =
                isSyntheticId(messageId) ? workflowIdOf(messageId) : task.getWorkflowInstanceId();
        WorkflowModel workflow = executionDAOFacade.getWorkflowModel(workflowId, false);
        NativeState state = workflow == null ? null : workflow.getNativeState();
        if (state == null || workflow.getStatus().isTerminal()) {
            queueDAO.remove(queueName, messageId);
            return null;
        }
        TaskContinuation marker =
                task != null
                        ? task.getContinuation()
                        : syntheticMarker(workflow, laneOfSynthetic(messageId));
        if (marker == null
                || !marker.isLeased()
                || !messageId.equals(state.getActiveContinuation())
                || !queueName.equals(marker.getQueue())) {
            // not what was offered: a stale wake-up, or a duplicate in another domain's queue
            queueDAO.remove(queueName, messageId);
            return null;
        }
        marker.setWorker(workerId); // in what is handed out; the completion records it
        TaskModel handed =
                task != null ? task : syntheticTask(workflow, marker, messageId, queueName);
        queueDAO.setUnackTimeout(
                queueName, messageId, state.getContinuationTimeoutSeconds() * 1000L);
        return handed.toTask();
    }

    /** The task handed to a worker for a lane that has no task: bootstrap or handlers. */
    private TaskModel syntheticTask(
            WorkflowModel workflow, TaskContinuation marker, String messageId, String queueName) {
        NativeState state = workflow.getNativeState();
        TaskModel task = new TaskModel();
        task.setTaskId(messageId);
        task.setTaskType(state.getCodeType());
        task.setTaskDefName(state.getCodeType());
        task.setReferenceTaskName(marker.getLane());
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setWorkflowType(workflow.getWorkflowName());
        task.setCorrelationId(workflow.getCorrelationId());
        task.setWorkflowPriority(workflow.getPriority());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setScheduledTime(marker.getQueuedAt());
        task.setStartTime(marker.getLeasedAt());
        task.setWorkerId(marker.getWorker());
        task.setDomain(queueDomain(queueName));
        task.setContinuation(marker);
        Map<String, Object> input = new HashMap<>();
        if (HANDLER_LANE.equals(marker.getLane()) && workflowMessageQueueDAO != null) {
            // claimed under the run's number, so a re-run of this continuation gets the same
            // messages and the next run acknowledges them (see JdbcWorkflowMessageQueueDAO)
            var messages =
                    workflowMessageQueueDAO.claim(
                            workflow.getWorkflowId(),
                            HANDLER_BATCH_SIZE,
                            HANDLER_LANE + "#" + marker.getN(),
                            marker.getN());
            input.put(INPUT_MESSAGES, messages);
        }
        task.setInputData(input);
        return task;
    }

    /**
     * The domain of a continuation queue ({@code domain:codeType}), or null for the plain queue.
     */
    public static String queueDomain(String queueName) {
        int colon = queueName == null ? -1 : queueName.indexOf(':');
        return colon < 0 ? null : queueName.substring(0, colon);
    }

    // ------------------------------------------------------------------ complete

    /** What the executor persists and does after a completion was applied in memory. */
    public static final class Completion {
        public final List<TaskModel> touchedTasks = new ArrayList<>();
        public String ackQueue;
        public String ackId;
        public boolean deferred;
        public int retryDelaySeconds;
        public boolean waitsForHandlers;
        public boolean finished;
        public String reason;
        public TaskModel causeTask;
        public TaskModel handed;
    }

    /**
     * Applies a continuation's completion to the execution, in memory, under the workflow's lock.
     * {@code awaited} is the task the code paused on, or null for the bootstrap and handler lanes.
     * The caller saves the workflow row first, then the touched tasks, acknowledges the queue
     * message, and acts on {@link Completion#finished} or {@link Completion#deferred}.
     *
     * @throws ConflictException when the completion is stale: its lease was superseded or the
     *     continuation already applied
     */
    public Completion complete(WorkflowModel workflow, TaskModel awaited, TaskResult result) {
        NativeState state = workflow.getNativeState();
        Map<String, Object> out =
                result.getOutputData() == null ? Map.of() : result.getOutputData();
        String messageId = awaited != null ? awaited.getTaskId() : result.getTaskId();
        TaskContinuation marker =
                awaited != null
                        ? awaited.getContinuation()
                        : syntheticMarker(workflow, laneOfSynthetic(messageId));
        if (state == null || marker == null) {
            throw new ConflictException(
                    "Task %s of workflow %s is not a continuation",
                    messageId, workflow.getWorkflowId());
        }
        long epoch = out.get(OUTPUT_EPOCH) instanceof Number e ? e.longValue() : -1;
        int n = out.get(OUTPUT_N) instanceof Number number ? number.intValue() : -1;
        if (!marker.isLeased()
                || marker.getN() != n
                || marker.getEpoch() != epoch
                // the lease ran out and another continuation has run since: this result was
                // computed without it, so the continuation runs again
                || !messageId.equals(state.getActiveContinuation())) {
            Monitors.recordNativeStaleCompletion(workflow.getWorkflowName());
            throw new ConflictException(
                    "Continuation %s#%d of workflow %s is not leased at epoch %d (%s)",
                    marker.getLane(), n, workflow.getWorkflowId(), epoch, marker);
        }
        state.setActiveContinuation(null); // whatever the outcome, the next one may run
        state.setActiveUntil(0);
        marker.setWorker(result.getWorkerId()); // the poll that handed it out wrote nothing
        Completion completion = new Completion();
        completion.ackQueue = marker.getQueue();
        completion.ackId = messageId;
        completion.handed = awaited;
        String workflowName = workflow.getWorkflowName();
        try {
            if (result.getStatus() == TaskResult.Status.FAILED_WITH_TERMINAL_ERROR) {
                // The code diverged from its recording. Hold the execution rather than fail it:
                // the usual cause is a deploy, and a rollback (or a fixed build) lets it continue.
                Monitors.recordNativeFragmentRejected(workflowName, "diverged");
                completion.retryDelaySeconds = retryDelaySeconds(marker);
                return defer(
                        workflow,
                        marker,
                        completion,
                        DIVERGED_PREFIX + result.getReasonForIncompletion(),
                        "diverged");
            }
            if (result.getStatus() != TaskResult.Status.COMPLETED) {
                // an unexpected worker error: run the continuation again, after a backoff
                completion.retryDelaySeconds = retryDelaySeconds(marker);
                return defer(
                        workflow,
                        marker,
                        completion,
                        "worker reported " + result.getStatus(),
                        "worker_failed");
            }
            validateProtocol(out);
            boolean done = Boolean.TRUE.equals(out.get(OUTPUT_DONE));
            Map<String, Object> error = mapOrNull(out.get(OUTPUT_ERROR));

            Map<String, Object> changedVariables = changedVariables(out);
            String conflict = variableConflict(workflow, out, changedVariables);
            if (conflict != null) {
                return defer(
                        workflow,
                        marker,
                        completion,
                        "variable '" + conflict + "' was changed by another completion",
                        "variable_conflict");
            }
            if (done && error == null && MAIN_LANE.equals(marker.getLane())) {
                String pending = pendingMessages(workflow);
                if (pending != null) {
                    // The handler lane goes first. This continuation is not offered again until
                    // that lane has settled (repairAndWake): competing for the slot, it can
                    // win every time and the signal never runs.
                    completion.waitsForHandlers = true;
                    return defer(
                            workflow,
                            marker,
                            completion,
                            WAITING_FOR_HANDLERS_PREFIX + pending,
                            "waiting_for_handlers");
                }
            }

            // everything is checked before anything is written: a rejected completion changes
            // neither the definition nor the variables
            List<WorkflowTask> fragment = readFragment(out);
            List<Run> runs = runs(fragment, awaited);
            WorkflowDef definition = workflow.getWorkflowDefinition();
            if (!fragment.isEmpty()) {
                validateFragment(
                        definition,
                        fragment,
                        runs,
                        state.getCodeType(),
                        thrownRef(done, error, marker));
            }
            checkVariablesSize(workflow, changedVariables);

            if (!fragment.isEmpty()) {
                // which continuation appended each entry: a rerun undoes a continuation's entries
                for (WorkflowTask entry : fragment) {
                    entry.getMetadata().put(APPENDED_BY, markerKey(marker));
                }
                populateTaskDefinitions(workflowName, fragment);
                for (Run run : runs) {
                    if (run.anchor() == null) {
                        definition.getTasks().addAll(run.entries());
                    } else if (!insertAfter(definition, run.anchor(), run.entries())) {
                        // validateFragment checked every anchor
                        throw new IllegalStateException("anchor " + run.anchor() + " not found");
                    }
                }
            }
            workflow.getVariables().putAll(changedVariables);
            if (out.get(OUTPUT_REPLIES) instanceof Map<?, ?> replies) {
                state.setReplies(objectMapper.convertValue(replies, MAP_TYPE));
            }
            if (out.get(OUTPUT_SEEN) instanceof List<?> seen) {
                state.setSeen(objectMapper.convertValue(seen, STRING_LIST_TYPE));
            }
            release(workflow, out, completion);
            marker.setState(TaskContinuation.APPLIED);
            marker.setStep(state.getStep() + 1);
            state.setStep(marker.getStep());
            marker.setRecorded(mapOrEmpty(out.get(OUTPUT_RECORDED)));
            marker.setResumedWith(mapOrEmpty(out.get(OUTPUT_RESUMED_WITH)));
            marker.setDone(done);
            marker.setResult(mapOrNull(out.get(OUTPUT_RESULT)));
            marker.setError(error);
            marker.setAppliedAt(System.currentTimeMillis());
            marker.setDeferred(null);
            if (HANDLER_LANE.equals(marker.getLane())) {
                // the handler lane runs once per batch of messages; it is never done
                TaskContinuation next = new TaskContinuation(HANDLER_LANE, marker.getN() + 1);
                next.setStep(marker.getStep());
                state.setHandlers(next);
            }
            if (awaited != null) {
                // re-evaluated by the decider, which finds the appended entries after it
                awaited.setExecuted(false);
                completion.touchedTasks.add(awaited);
            }
            // The code returned: the decider completes the workflow, with the marker's result as
            // the output, once everything this fragment appended has run
            // (DeciderService.checkForWorkflowCompletion). The code raised: the workflow fails.
            if (done && error != null && MAIN_LANE.equals(marker.getLane())) {
                completion.finished = true;
                completion.reason = String.valueOf(error.get("message"));
                completion.causeTask = causeTask(workflow, error.get("ref"), completion.reason);
            }
            LOGGER.debug(
                    "Applied continuation {} of workflow {}: {} entries appended",
                    marker,
                    workflow.getWorkflowId(),
                    fragment.size());
            return completion;
        } catch (RejectedFragmentException e) {
            LOGGER.warn(
                    "Rejected continuation {} of workflow {}: {}",
                    marker,
                    workflow.getWorkflowId(),
                    e.getMessage());
            Monitors.recordNativeFragmentRejected(workflowName, e.reason);
            marker.setState(TaskContinuation.APPLIED);
            marker.setDone(true);
            marker.setError(Map.of("message", e.getMessage()));
            marker.setAppliedAt(System.currentTimeMillis());
            if (awaited != null) {
                completion.touchedTasks.add(awaited);
            }
            completion.finished = true;
            completion.reason = e.getMessage();
            return completion;
        }
    }

    /**
     * The task a failed workflow failed at, shown failed. An uncaught task failure names its task,
     * which the SDK made optional so the failure could reach the code: it is put back to the status
     * it failed with. An exception the code threw names the TERMINATE entry the code appended where
     * it threw; that entry becomes a FAILED task with the exception as its reason.
     */
    private TaskModel causeTask(WorkflowModel workflow, Object ref, String reason) {
        if (ref == null) {
            return null;
        }
        TaskModel task = workflow.getTaskByRefName(ref.toString());
        if (task != null) {
            if (task.getStatus() == TaskModel.Status.COMPLETED_WITH_ERRORS) {
                Object failed = task.getOutputData().get(OUTPUT_FAILED_STATUS);
                task.setStatus(
                        failed == null
                                ? TaskModel.Status.FAILED
                                : TaskModel.Status.valueOf(failed.toString()));
            }
            return task;
        }
        WorkflowTask entry = workflow.getWorkflowDefinition().getTaskByRefName(ref.toString());
        if (entry == null || !TaskType.TERMINATE.name().equals(entry.getType())) {
            return null;
        }
        long now = System.currentTimeMillis();
        TaskModel thrown = new TaskModel();
        thrown.setTaskId(java.util.UUID.randomUUID().toString());
        thrown.setReferenceTaskName(entry.getTaskReferenceName());
        thrown.setTaskType(TaskType.TERMINATE.name());
        thrown.setTaskDefName(entry.getName());
        thrown.setWorkflowInstanceId(workflow.getWorkflowId());
        thrown.setWorkflowType(workflow.getWorkflowName());
        thrown.setCorrelationId(workflow.getCorrelationId());
        thrown.setWorkflowPriority(workflow.getPriority());
        thrown.setWorkflowTask(entry);
        thrown.setInputData(new HashMap<>(entry.getInputParameters()));
        thrown.setSeq(workflow.getTasks().stream().mapToInt(TaskModel::getSeq).max().orElse(0) + 1);
        thrown.setScheduledTime(now);
        thrown.setStartTime(now);
        thrown.setEndTime(now);
        thrown.setStatus(TaskModel.Status.FAILED);
        thrown.setReasonForIncompletion(reason);
        workflow.getTasks().add(thrown);
        if (executionDAOFacade != null) {
            executionDAOFacade.createTasks(List.of(thrown));
        }
        return thrown;
    }

    private Completion defer(
            WorkflowModel workflow,
            TaskContinuation marker,
            Completion completion,
            String why,
            String reason) {
        long now = System.currentTimeMillis();
        boolean heldBefore = isHeld(marker);
        boolean heldStill = why.startsWith(DIVERGED_PREFIX);
        marker.setState(TaskContinuation.RUNNABLE);
        if (!(heldBefore && heldStill)) {
            marker.setQueuedAt(now); // while held it stays at the start of the hold
        }
        marker.setDeferred(why);
        marker.setWorker(null);
        if (completion.handed != null) {
            completion.touchedTasks.add(completion.handed);
        }
        completion.deferred = true;
        completion.reason = why;
        Monitors.recordNativeCompletionDeferred(workflow.getWorkflowName(), reason);
        LOGGER.info(
                "Deferring continuation {} of workflow {}: {}",
                marker,
                workflow.getWorkflowId(),
                why);
        return completion;
    }

    /** Whether the code no longer matches this continuation's recording. */
    private static boolean isHeld(TaskContinuation marker) {
        return marker.getDeferred() != null && marker.getDeferred().startsWith(DIVERGED_PREFIX);
    }

    /**
     * Why completing the execution now would drop messages, or null. A main-lane completion that
     * ends the execution successfully must not discard signals or updates the server has
     * acknowledged: messages still queued, or claimed by a handler run that has not finished.
     */
    private String pendingMessages(WorkflowModel workflow) {
        TaskContinuation handlers = workflow.getNativeState().getHandlers();
        if (handlers == null) {
            return null;
        }
        if (handlers.isLeased() || handlers.isRunnable()) {
            return "handler run " + handlers.getN() + " is " + handlers.getState().toLowerCase();
        }
        if (workflowMessageQueueDAO != null) {
            long queued = workflowMessageQueueDAO.size(workflow.getWorkflowId());
            if (queued > 0) {
                return queued + " message(s) are queued for the handler lane";
            }
        }
        return null;
    }

    /** Completes the WAIT tasks a handler run found satisfied ({@code releases}). */
    private void release(WorkflowModel workflow, Map<String, Object> out, Completion completion) {
        if (!(out.get(OUTPUT_RELEASES) instanceof List<?>)) {
            return;
        }
        List<Map<String, Object>> releases =
                objectMapper.convertValue(out.get(OUTPUT_RELEASES), LIST_OF_MAPS_TYPE);
        for (Map<String, Object> release : releases) {
            Object ref = release.get("ref");
            if (ref == null) {
                continue;
            }
            TaskModel task = workflow.getTaskByRefName(ref.toString());
            if (task == null || task.getStatus().isTerminal()) {
                continue;
            }
            task.setStatus(TaskModel.Status.COMPLETED);
            task.setEndTime(System.currentTimeMillis());
            task.setExecuted(false);
            task.setOutputData(mapOrEmpty(release.get("output")));
            completion.touchedTasks.add(task);
        }
    }

    // ------------------------------------------------------------------ joins

    /**
     * Whether every branch of a live-target JOIN has finished saying what it has to say: its last
     * task is terminal and, if the code paused on it, that continuation was applied and ended the
     * lane. A marker still waiting, runnable or leased means the lane has more to append; an
     * applied marker that did not end the lane means the branch's real last entry is not this one
     * yet in the copy of the definition being read.
     */
    public static boolean lanesSettled(WorkflowModel workflow, List<String> tails) {
        for (String ref : tails) {
            TaskModel task = workflow.getTaskByRefName(ref);
            if (task == null || !task.getStatus().isTerminal()) {
                return false;
            }
            TaskContinuation marker = task.getContinuation();
            if (marker != null && !(marker.isApplied() && marker.isDone())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ operator actions

    /**
     * What a rerun from {@code rerunFrom} undoes, decided before anything is removed. The code
     * after the task may have seen its result, so every continuation applied once the task had
     * ended runs again: the entries those continuations appended go, with their tasks. The tasks
     * that follow the task in its own list go too and are scheduled again from their entries, as a
     * rerun of a standard workflow does. What another lane started before the task ended stays.
     *
     * @param dropTaskIds the tasks the rerun removes
     * @param dropEntries the definition entries the rerun removes, by reference name
     * @param resetMarkers the continuations that run again, as {@code lane#n}
     */
    public record RerunPlan(
            Set<String> dropTaskIds, Set<String> dropEntries, Set<String> resetMarkers) {}

    public RerunPlan planRerun(WorkflowModel workflow, TaskModel rerunFrom) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        // what the code did after the task ended may depend on it; a task that never ended had
        // nothing come after it in its lane
        long cut = rerunFrom.getEndTime() > 0 ? rerunFrom.getEndTime() : Long.MAX_VALUE;
        Set<String> resetMarkers = new HashSet<>();
        for (TaskModel task : workflow.getTasks()) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null
                    && !HANDLER_LANE.equals(marker.getLane())
                    && marker.isApplied()
                    && marker.getAppliedAt() >= cut) {
                resetMarkers.add(markerKey(marker));
            }
        }
        Set<String> dropEntries = new HashSet<>();
        for (WorkflowTask entry : def.collectTasks()) {
            if (resetMarkers.contains(entry.getMetadata().get(APPENDED_BY))) {
                entry.collectTasks().forEach(t -> dropEntries.add(t.getTaskReferenceName()));
            }
        }
        String ref =
                com.netflix.conductor.common.utils.TaskUtils.removeIterationFromTaskRefName(
                        rerunFrom.getReferenceTaskName());
        Set<String> dropRefs = new HashSet<>(dropEntries);
        dropRefs.addAll(followingInItsList(def, ref));
        // in a loop holding the task: the rest of its iteration and the later iterations, which
        // the reopened loop schedules again
        for (WorkflowTask loop : def.collectTasks()) {
            if (TaskType.DO_WHILE.name().equals(loop.getType())
                    && !loop.getTaskReferenceName().equals(ref)
                    && loop.has(ref)) {
                for (WorkflowTask body : loop.getLoopOver()) {
                    body.collectTasks().forEach(t -> dropRefs.add(t.getTaskReferenceName()));
                }
            }
        }
        Set<String> dropTaskIds = new HashSet<>();
        for (TaskModel task : workflow.getTasks()) {
            if (task.getSeq() > rerunFrom.getSeq()
                    && dropRefs.contains(
                            com.netflix.conductor.common.utils.TaskUtils
                                    .removeIterationFromTaskRefName(task.getReferenceTaskName()))) {
                dropTaskIds.add(task.getTaskId());
            }
        }
        return new RerunPlan(dropTaskIds, dropEntries, resetMarkers);
    }

    /**
     * After a rerun removed the tasks of its plan: the entries the plan undoes go, and the rerun
     * task's marker and the markers of the continuations that run again wait again, so each runs
     * once more when the decider evaluates its task. A JOIN waits again for a fork holding the
     * rerun task, and when the code after it runs again (the lanes it waited for may run again).
     * Returns those JOINs: the caller saves them and queues them only after the rerun is saved.
     */
    public List<TaskModel> adjustRerun(
            WorkflowModel workflow, TaskModel rerunFrom, RerunPlan plan, List<TaskModel> touched) {
        WorkflowDef def = workflow.getWorkflowDefinition();
        removeAll(def, plan.dropEntries());
        if (rerunFrom.getContinuation() != null) {
            rerunFrom.setContinuation(rerunFrom.getContinuation().reset());
        }
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null
                    && task != rerunFrom
                    && plan.resetMarkers().contains(markerKey(marker))) {
                task.setContinuation(marker.reset());
                touched.add(task);
            }
        }
        NativeState state = workflow.getNativeState();
        if (state != null && def.getTasks().isEmpty()) {
            TaskContinuation bootstrap = new TaskContinuation(MAIN_LANE, 1);
            bootstrap.setState(TaskContinuation.RUNNABLE);
            bootstrap.setQueuedAt(System.currentTimeMillis());
            state.setBootstrap(bootstrap);
        }
        List<TaskModel> runAgain = new ArrayList<>(touched);
        List<TaskModel> joins = new ArrayList<>();
        for (TaskModel task : workflow.getTasks()) {
            if (TaskType.TASK_TYPE_JOIN.equals(task.getTaskType())
                    && task.getStatus() == TaskModel.Status.COMPLETED
                    && task.getWorkflowTask() != null) {
                WorkflowTask fork = previousTask(def, task.getReferenceTaskName());
                if (runAgain.contains(task)
                        || (fork != null
                                && TaskType.FORK_JOIN.name().equals(fork.getType())
                                && fork.has(rerunFrom.getReferenceTaskName()))) {
                    task.setStatus(TaskModel.Status.IN_PROGRESS);
                    task.setExecuted(false);
                    task.setEndTime(0);
                    task.clearOutput();
                    touched.add(task);
                    joins.add(task);
                }
            }
        }
        workflow.setOutput(new HashMap<>());
        return joins;
    }

    /**
     * Before a retry runs the code again, removes from the definition the TERMINATE entry that
     * marks where the code threw, and everything else the continuation that threw drew: the
     * previews of what it would have called next, and an if it went past or a lane it started. The
     * workflow failed in that same completion, so none of it has a task, and the code draws it
     * again. Should one have a task, only the mark and its previews go.
     */
    public void undoThrow(WorkflowModel workflow, TaskModel thrown) {
        LastMain last = lastMain(workflow);
        String key =
                last != null && last.marker().isDone() && last.marker().hasError()
                        ? markerKey(last.marker())
                        : null;
        Set<String> refs = new HashSet<>();
        for (WorkflowTask entry : workflow.getWorkflowDefinition().collectTasks()) {
            if (key != null && key.equals(entry.getMetadata().get(APPENDED_BY))) {
                entry.collectTasks().forEach(t -> refs.add(t.getTaskReferenceName()));
            }
        }
        if (workflow.getTasks().stream().anyMatch(t -> refs.contains(t.getReferenceTaskName()))) {
            refs.clear();
        }
        for (WorkflowTask entry : workflow.getWorkflowDefinition().collectTasks()) {
            if (thrown.getReferenceTaskName().equals(entry.getMetadata().get("previewAfter"))) {
                refs.add(entry.getTaskReferenceName());
            }
        }
        refs.add(thrown.getReferenceTaskName());
        removeAll(workflow.getWorkflowDefinition(), refs);
    }

    /**
     * Retry when the code itself failed (it threw, or diverged from its recording): the main lane's
     * last continuation runs again, and the slot is free for it. Returns it, or null when there is
     * none; the caller saves the workflow, then its holder, then pushes the decider.
     */
    public LastMain rearmLastMain(WorkflowModel workflow) {
        LastMain last = lastMain(workflow);
        if (last == null) {
            return null;
        }
        TaskContinuation marker = last.marker();
        marker.setState(TaskContinuation.RUNNABLE);
        marker.setQueuedAt(System.currentTimeMillis());
        marker.setDeferred("retry");
        marker.setDone(false);
        marker.setError(null);
        workflow.getNativeState().setActiveContinuation(null);
        workflow.getNativeState().setActiveUntil(0);
        return last;
    }

    private static String markerKey(TaskContinuation marker) {
        return marker.getLane() + "#" + marker.getN();
    }

    /**
     * The entries after {@code ref} in the flow of its own list, at any depth, up to the JOIN of
     * the fork or the loop that holds it: what the decider schedules again after the task.
     */
    private static Set<String> followingInItsList(WorkflowDef def, String ref) {
        Set<String> refs = new HashSet<>();
        WorkflowTask next = def.getNextTask(ref);
        while (next != null && !refs.contains(next.getTaskReferenceName())) {
            if (TaskType.DO_WHILE.name().equals(next.getType()) && next.has(ref)) {
                break;
            }
            if (TaskType.TASK_TYPE_JOIN.equals(next.getType())) {
                WorkflowTask fork = previousTask(def, next.getTaskReferenceName());
                if (fork != null && fork.has(ref)) {
                    break;
                }
            }
            next.collectTasks().forEach(t -> refs.add(t.getTaskReferenceName()));
            next = def.getNextTask(next.getTaskReferenceName());
        }
        return refs;
    }

    /** The main lane's last continuation, and the task it is on (null for the bootstrap). */
    public record LastMain(TaskContinuation marker, TaskModel holder) {}

    /** The marker whose continuation the main lane ran last: on a task, or the bootstrap. */
    public LastMain lastMain(WorkflowModel workflow) {
        TaskModel best = null;
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null
                    && MAIN_LANE.equals(marker.getLane())
                    && (best == null || marker.getN() > best.getContinuation().getN())) {
                best = task;
            }
        }
        if (best != null) {
            return new LastMain(best.getContinuation(), best);
        }
        NativeState state = workflow.getNativeState();
        return state == null || state.getBootstrap() == null
                ? null
                : new LastMain(state.getBootstrap(), null);
    }

    /** Whether any task's lane has code still to run: a continuation not yet applied. */
    public boolean hasUnappliedLane(WorkflowModel workflow) {
        for (TaskModel task : latestAttempts(workflow)) {
            TaskContinuation marker = task.getContinuation();
            if (marker != null && !marker.isApplied()) {
                return true;
            }
        }
        return false;
    }

    /** The latest attempt of every task reference. */
    private static List<TaskModel> latestAttempts(WorkflowModel workflow) {
        Map<String, TaskModel> latest = new LinkedHashMap<>();
        for (TaskModel task : workflow.getTasks()) {
            TaskModel previous = latest.get(task.getReferenceTaskName());
            if (previous == null || task.getSeq() > previous.getSeq()) {
                latest.put(task.getReferenceTaskName(), task);
            }
        }
        return new ArrayList<>(latest.values());
    }

    // ------------------------------------------------------------------ fragment rules

    private void validateProtocol(Map<String, Object> output) {
        Object protocol = output.get(OUTPUT_PROTOCOL);
        if (!(protocol instanceof Number)) {
            throw new RejectedFragmentException(
                    "protocol",
                    "a continuation completion must declare a numeric protocol version");
        }
        if (((Number) protocol).intValue() != PROTOCOL) {
            throw new RejectedFragmentException(
                    "protocol",
                    "protocol version " + protocol + " is not supported; expected " + PROTOCOL);
        }
    }

    /** A run of fragment entries and the entry it goes after (null: at the end of the list). */
    private record Run(String anchor, List<WorkflowTask> entries) {}

    /**
     * The runs a fragment is inserted as. The fragment goes after the task the code paused on; an
     * entry that names another anchor ({@code metadata.after}) starts a run that goes there
     * instead: code that left a switch case appends after the switch, not inside the case where it
     * paused, and what it drew in the case before leaving stays in the case.
     */
    private static List<Run> runs(List<WorkflowTask> fragment, TaskModel awaited) {
        List<Run> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= fragment.size(); i++) {
            if (i < fragment.size() && anchorOf(fragment.subList(i, i + 1), null) == null) {
                continue;
            }
            List<WorkflowTask> run = new ArrayList<>(fragment.subList(start, i));
            runs.add(new Run(anchorOf(run, start == 0 ? awaited : null), run));
            start = i;
        }
        return runs;
    }

    /**
     * Where a fragment is inserted: its first entry's {@code metadata.after}, else the awaited
     * task.
     */
    private static String anchorOf(List<WorkflowTask> fragment, TaskModel awaited) {
        if (!fragment.isEmpty() && fragment.get(0).getMetadata() != null) {
            Object after = fragment.get(0).getMetadata().get("after");
            if (after != null && !after.toString().isBlank()) {
                return after.toString();
            }
        }
        return awaited == null ? null : awaited.getReferenceTaskName();
    }

    /**
     * Whether the decider must revisit a native workflow soon: a continuation is queued or leased
     * and may need moving to a live worker's queue, or a task is scheduled and its queue message
     * may have been lost to a storage failure (only the sweeper repairs that).
     */
    public static boolean needsPromptRevisit(WorkflowModel workflow) {
        NativeState state = workflow.getNativeState();
        if (state == null) {
            return false;
        }
        for (TaskContinuation marker :
                new TaskContinuation[] {state.getBootstrap(), state.getHandlers()}) {
            if (marker != null && (marker.isRunnable() || marker.isLeased())) {
                return true;
            }
        }
        for (TaskModel task : workflow.getTasks()) {
            TaskContinuation marker = task.getContinuation();
            if (task.getStatus() == TaskModel.Status.SCHEDULED
                    || (marker != null && (marker.isRunnable() || marker.isLeased()))) {
                return true;
            }
        }
        return false;
    }

    private List<WorkflowTask> readFragment(Map<String, Object> output) {
        Object raw = output.get(OUTPUT_FRAGMENT);
        if (raw == null) {
            return List.of();
        }
        try {
            List<WorkflowTask> fragment = objectMapper.convertValue(raw, FRAGMENT_TYPE);
            return fragment == null ? List.of() : fragment;
        } catch (IllegalArgumentException | JacksonException e) {
            throw new RejectedFragmentException(
                    "fragment", "fragment is not a list of workflow tasks: " + e.getMessage());
        }
    }

    /**
     * The reference of the entry that marks where the code threw, when this completion ends the
     * main lane with an exception: the only TERMINATE a fragment may hold, because the workflow
     * fails in this same completion and the entry is never scheduled.
     */
    private static String thrownRef(
            boolean done, Map<String, Object> error, TaskContinuation marker) {
        if (!done || error == null || !MAIN_LANE.equals(marker.getLane())) {
            return null;
        }
        Object ref = error.get("ref");
        return ref == null ? null : ref.toString();
    }

    private void validateFragment(
            WorkflowDef definition,
            List<WorkflowTask> fragment,
            List<Run> runs,
            String codeType,
            String thrownRef) {
        int count = 0; // nested entries count
        for (WorkflowTask task : fragment) {
            count += task.collectTasks().size();
        }
        List<WorkflowTask> current = definition.collectTasks();
        int total = current.size() + count;
        int maxTotal = properties.getNativeWorkflow().getMaxDefinitionTasks();
        if (total > maxTotal) {
            throw new RejectedFragmentException(
                    "size",
                    "definition would have "
                            + total
                            + " tasks, more than the limit of "
                            + maxTotal);
        }
        long bytes = serializedSize(fragment);
        long maxBytes = properties.getNativeWorkflow().getMaxFragmentBytes().toBytes();
        if (bytes > maxBytes) {
            throw new RejectedFragmentException(
                    "size", "fragment is " + bytes + " bytes, more than the limit of " + maxBytes);
        }
        Set<String> existing = new HashSet<>();
        current.forEach(t -> existing.add(t.getTaskReferenceName()));
        // each run's anchor is in the definition, or in a run inserted before it
        Set<String> placed = new HashSet<>(existing);
        for (Run run : runs) {
            if (run.anchor() != null && !placed.contains(run.anchor())) {
                throw new RejectedFragmentException(
                        "fragment",
                        "task " + run.anchor() + " is not in the execution's definition");
            }
            run.entries()
                    .forEach(
                            e ->
                                    e.collectTasks()
                                            .forEach(t -> placed.add(t.getTaskReferenceName())));
        }
        Set<String> seen = new HashSet<>();
        Map<String, Integer> markersPerLane = new HashMap<>();
        for (WorkflowTask task : fragment) {
            for (WorkflowTask nested : task.collectTasks()) {
                String ref = nested.getTaskReferenceName();
                String type = nested.getType();
                if (codeType != null && codeType.equals(nested.getName())) {
                    // its queue messages could not be told from the code's own
                    throw new RejectedFragmentException(
                            "reference", "a task cannot be named after the code type " + codeType);
                }
                if (ref == null || ref.isBlank()) {
                    throw new RejectedFragmentException(
                            "reference", "every fragment task needs a taskReferenceName");
                }
                if (existing.contains(ref) || !seen.add(ref)) {
                    throw new RejectedFragmentException(
                            "reference", "taskReferenceName " + ref + " is already used");
                }
                if (type == null || !taskMappers.containsKey(type)) {
                    throw new RejectedFragmentException(
                            "type", "task " + ref + " has unsupported type " + type);
                }
                if (TaskType.TERMINATE.name().equals(type) && !ref.equals(thrownRef)) {
                    throw new RejectedFragmentException(
                            "type",
                            "a native workflow ends through its code, not a TERMINATE task");
                }
                if (laneOf(nested) == null) {
                    throw new RejectedFragmentException(
                            "lane", "task " + ref + " does not name its lane in metadata");
                }
                if (TaskType.SWITCH.name().equals(type)
                        && !NativeCodeEvaluator.NAME.equals(nested.getEvaluatorType())) {
                    throw new RejectedFragmentException(
                            "type",
                            "switch "
                                    + ref
                                    + " is decided by the code: its evaluatorType must be "
                                    + NativeCodeEvaluator.NAME);
                }
                if (nested.getMetadata().get(METADATA_CONTINUATION) != null) {
                    TaskContinuation marker = markerOf(nested);
                    if (marker == null) {
                        throw new RejectedFragmentException(
                                "continuation",
                                "task " + ref + " has a malformed continuation marker");
                    }
                    markersPerLane.merge(marker.getLane(), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> e : markersPerLane.entrySet()) {
            if (e.getValue() > 1) {
                throw new RejectedFragmentException(
                        "continuation",
                        "lane " + e.getKey() + " pauses more than once in one fragment");
            }
        }
    }

    private void populateTaskDefinitions(String workflowName, List<WorkflowTask> fragment) {
        WorkflowDef holder = new WorkflowDef();
        holder.setName(workflowName);
        holder.setTasks(fragment);
        try {
            // a worker that dies holding a native task: the response timeout reschedules it
            metadataMapperService.populateTaskDefinitions(holder, TaskDef.TimeoutPolicy.RETRY);
        } catch (IllegalArgumentException e) {
            throw new RejectedFragmentException("definition", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ variables

    /**
     * The variables this completion changed, checked: an object, within the size limit with the
     * current variables. Empty when it changed none.
     */
    /**
     * The variables this completion changed: those it wrote that differ from what its continuation
     * read ({@code resumedWith}), so a value another completion wrote since is kept.
     */
    private Map<String, Object> changedVariables(Map<String, Object> output) {
        Object raw = output.get(OUTPUT_VARIABLES);
        if (raw == null) {
            return Map.of();
        }
        Map<String, Object> changed;
        try {
            changed = objectMapper.convertValue(raw, MAP_TYPE);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new RejectedFragmentException("variables", "variables must be an object");
        }
        if (changed == null) {
            return Map.of();
        }
        Map<String, Object> base = mapOrNull(output.get(OUTPUT_RESUMED_WITH));
        if (base != null) {
            changed.entrySet().removeIf(e -> sameJson(e.getValue(), base.get(e.getKey())));
        }
        return changed;
    }

    private void checkVariablesSize(WorkflowModel workflow, Map<String, Object> changed) {
        if (changed.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(workflow.getVariables());
        merged.putAll(changed);
        long maxBytes = properties.getMaxWorkflowVariablesPayloadSizeThreshold().toBytes();
        long size = serializedSize(merged);
        if (size > maxBytes) {
            throw new RejectedFragmentException(
                    "variables",
                    "variables payload of "
                            + size
                            + " bytes is greater than the permissible limit of "
                            + maxBytes
                            + " bytes");
        }
    }

    /**
     * The first variable this completion changed that another completion has changed since this
     * continuation read the variables, or null. Two continuations of one execution can run at the
     * same time on different workers; the later completion would otherwise overwrite the earlier
     * one's writes with its stale copy. The caller runs a conflicting continuation again.
     */
    private String variableConflict(
            WorkflowModel workflow, Map<String, Object> output, Map<String, Object> changed) {
        Map<String, Object> base = mapOrNull(output.get(OUTPUT_RESUMED_WITH));
        if (base == null) {
            return null;
        }
        for (String key : changed.keySet()) {
            if (!sameJson(base.get(key), workflow.getVariables().get(key))) {
                return key;
            }
        }
        return null;
    }

    /** JSON equality that ignores numeric representation (1 and 1L are the same value). */
    private boolean sameJson(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        JsonNode x = objectMapper.valueToTree(a);
        JsonNode y = objectMapper.valueToTree(b);
        return x.equals(
                (n1, n2) -> {
                    if (n1.isNumber() && n2.isNumber()) {
                        return n1.decimalValue().compareTo(n2.decimalValue());
                    }
                    return n1.equals(n2) ? 0 : 1;
                },
                y);
    }

    private Map<String, Object> mapOrNull(Object value) {
        if (!(value instanceof Map)) {
            return null;
        }
        return objectMapper.convertValue(value, MAP_TYPE);
    }

    private Map<String, Object> mapOrEmpty(Object value) {
        Map<String, Object> m = mapOrNull(value);
        return m == null ? new HashMap<>() : m;
    }

    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JacksonException e) {
            throw new RejectedFragmentException(
                    "fragment", "payload cannot be serialized: " + e.getMessage());
        }
    }

    /** A completion that breaks a rule; the reason names the rule for metrics. */
    static final class RejectedFragmentException extends RuntimeException {
        final String reason;

        RejectedFragmentException(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }

    // ------------------------------------------------------------------ the execution's definition

    /**
     * Inserts entries right after the entry {@code ref}, in whichever list holds it: the top level,
     * a fork branch, a switch case or a loop body. False, changing nothing, when there is no such
     * entry.
     */
    static boolean insertAfter(WorkflowDef def, String ref, List<WorkflowTask> entries) {
        return insertAfter(def.getTasks(), ref, entries);
    }

    private static boolean insertAfter(
            List<WorkflowTask> list, String ref, List<WorkflowTask> entries) {
        for (int i = 0; i < list.size(); i++) {
            if (ref.equals(list.get(i).getTaskReferenceName())) {
                list.addAll(i + 1, entries);
                return true;
            }
            for (List<WorkflowTask> child : children(list.get(i))) {
                if (insertAfter(child, ref, entries)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The entry right before {@code ref} in the list that holds it; null when it is first. */
    public static WorkflowTask previousTask(WorkflowDef def, String ref) {
        return previous(def.getTasks(), ref);
    }

    private static WorkflowTask previous(List<WorkflowTask> list, String ref) {
        for (int i = 0; i < list.size(); i++) {
            if (ref.equals(list.get(i).getTaskReferenceName())) {
                return i == 0 ? null : list.get(i - 1);
            }
            for (List<WorkflowTask> child : children(list.get(i))) {
                WorkflowTask found = previous(child, ref);
                if (found != null) {
                    return found;
                }
                if (child.stream().anyMatch(t -> ref.equals(t.getTaskReferenceName()))) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Removes the entries named in {@code refs}, at any depth, with what they hold. */
    static int removeAll(WorkflowDef def, Set<String> refs) {
        return removeAll(def.getTasks(), refs);
    }

    private static int removeAll(List<WorkflowTask> list, Set<String> refs) {
        int removed = 0;
        for (java.util.Iterator<WorkflowTask> it = list.iterator(); it.hasNext(); ) {
            WorkflowTask task = it.next();
            if (refs.contains(task.getTaskReferenceName())) {
                it.remove();
                removed++;
            } else {
                for (List<WorkflowTask> child : children(task)) {
                    removed += removeAll(child, refs);
                }
            }
        }
        return removed;
    }

    /** The lists an entry holds, live: switch cases and the default, fork branches, loop body. */
    private static List<List<WorkflowTask>> children(WorkflowTask task) {
        List<List<WorkflowTask>> lists = new ArrayList<>();
        switch (TaskType.of(task.getType())) {
            case DECISION, SWITCH -> {
                lists.addAll(task.getDecisionCases().values());
                lists.add(task.getDefaultCase());
            }
            case FORK_JOIN -> lists.addAll(task.getForkTasks());
            case DO_WHILE -> lists.add(task.getLoopOver());
            default -> {}
        }
        return lists;
    }
}
