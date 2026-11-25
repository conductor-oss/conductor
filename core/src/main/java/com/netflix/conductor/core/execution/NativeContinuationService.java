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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskContinuation;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.NativeState;
import com.netflix.conductor.common.utils.TaskUtils;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.dal.ExecutionDAOFacade;
import com.netflix.conductor.core.exception.ConflictException;
import com.netflix.conductor.core.execution.NativeFragmentValidator.RejectedFragmentException;
import com.netflix.conductor.core.execution.NativeFragmentValidator.Run;
import com.netflix.conductor.core.execution.NativeWorkflows.Id;
import com.netflix.conductor.core.utils.QueueUtils;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.dao.WorkflowMessageQueueDAO;
import com.netflix.conductor.metrics.Monitors;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static com.netflix.conductor.core.execution.NativeFragmentValidator.runs;
import static com.netflix.conductor.core.execution.NativeFragmentValidator.thrownRef;
import static com.netflix.conductor.core.execution.NativeWorkflows.APPENDED_BY;
import static com.netflix.conductor.core.execution.NativeWorkflows.HANDLER_LANE;
import static com.netflix.conductor.core.execution.NativeWorkflows.MAIN_LANE;
import static com.netflix.conductor.core.execution.NativeWorkflows.METADATA_CONTINUATION;
import static com.netflix.conductor.core.execution.NativeWorkflows.METADATA_LANE;
import static com.netflix.conductor.core.execution.NativeWorkflows.OUTPUT_FAILED_STATUS;
import static com.netflix.conductor.core.execution.NativeWorkflows.codeType;
import static com.netflix.conductor.core.execution.NativeWorkflows.idOf;
import static com.netflix.conductor.core.execution.NativeWorkflows.isAwaited;
import static com.netflix.conductor.core.execution.NativeWorkflows.isSyntheticId;
import static com.netflix.conductor.core.execution.NativeWorkflows.laneOfSynthetic;
import static com.netflix.conductor.core.execution.NativeWorkflows.latestAttempts;
import static com.netflix.conductor.core.execution.NativeWorkflows.markerOf;
import static com.netflix.conductor.core.execution.NativeWorkflows.syntheticId;
import static com.netflix.conductor.core.execution.NativeWorkflows.syntheticMarker;
import static com.netflix.conductor.core.execution.NativeWorkflows.workflowIdOf;

/**
 * Drives the code of a native (code-defined) workflow with the tasks the code asked for and nothing
 * else.
 *
 * <p>The code only ever pauses on a task. Its entry names the continuation that runs when it
 * finishes (the lane of the code and its number), and the task carries a {@link TaskContinuation}
 * marker of that continuation's progress. Once the decider has evaluated the finished task, a
 * decide leases the marker and a message with the task's id goes to the continuation queue, which
 * workers poll with the ordinary task batch poll. The worker replays the code and finishes with the
 * ordinary task update, whose output carries the next piece of the definition. The completion is
 * applied under the workflow's lock: the fragment is appended after the task, variables and
 * recorded values are written onto the marker, and the task is marked not executed so the decider
 * schedules what follows it. The first continuation (before any task exists) and the handler lane
 * (which schedules no tasks) have their markers on the workflow's {@link NativeState}.
 */
@Component
public class NativeContinuationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NativeContinuationService.class);

    /** How the run's reason for incompletion starts while the code does not match its recording. */
    private static final String DIVERGED_PREFIX = "diverged: ";

    /** How the run's reason for incompletion starts while the handler lane has to run first. */
    private static final String WAITING_FOR_HANDLERS_PREFIX = "waiting for handlers: ";

    /** The longest wait before a continuation that diverged or failed is offered again. */
    private static final int MAX_RETRY_DELAY_SECONDS = 60;

    /** How many queued messages one handler run receives. */
    private static final int HANDLER_BATCH_SIZE = 10;

    public static final String OUTPUT_PROTOCOL = "protocol";
    private static final String OUTPUT_VARIABLES = "variables";
    private static final String OUTPUT_RESUMED_WITH = "resumedWith";
    private static final String OUTPUT_RECORDED = "recorded";
    private static final String OUTPUT_RETRY_COUNT = "retryCount";
    private static final String OUTPUT_N = "n";
    private static final String OUTPUT_DONE = "done";
    private static final String OUTPUT_RESULT = "result";
    private static final String OUTPUT_ERROR = "error";
    private static final String OUTPUT_RELEASES = "releases";
    private static final String OUTPUT_REPLIES = "replies";
    private static final String OUTPUT_PIN = "pin";
    private static final String PIN_DOMAIN = "domain";
    private static final String PIN_STAMP = "stamp";

    /** What a worker may name as its domain: it becomes part of a queue name. */
    private static final Pattern PIN_DOMAIN_FORMAT = Pattern.compile("[A-Za-z0-9_.-]{1,100}");

    private static final String INPUT_MESSAGES = "messages";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ConductorProperties properties;
    private final NativeFragmentValidator fragmentValidator;

    private QueueDAO queueDAO;
    private ExecutionDAOFacade executionDAOFacade;
    private WorkflowMessageQueueDAO workflowMessageQueueDAO;

    public NativeContinuationService(
            ObjectMapper objectMapper,
            ConductorProperties properties,
            NativeFragmentValidator fragmentValidator) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.fragmentValidator = fragmentValidator;
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
        Map<String, Object> meta = nativeMetadata(workflow);
        // on a restart the lanes without a task keep their ids, so their markers count the
        // restart as a retry (reset), and a completion of an offer made before it cannot match
        NativeState before = workflow.getNativeState();
        NativeState state = new NativeState();
        state.setBootstrap(
                before != null && before.getBootstrap() != null
                        ? before.getBootstrap().reset()
                        : new TaskContinuation());
        if (Boolean.TRUE.equals(meta.get("handlers"))) {
            state.setHandlers(
                    before != null && before.getHandlers() != null
                            ? before.getHandlers().reset()
                            : TaskContinuation.handlerRun(1));
        }
        workflow.setNativeState(state);
    }

    /**
     * Gives every task the code waits on its marker, as it is scheduled: new and not run, or for a
     * retried attempt, the marker of the attempt before, reset (the code waits on whichever attempt
     * finishes). Returns whether one was added.
     */
    private static boolean addMarkers(WorkflowModel workflow) {
        Map<String, TaskContinuation> markers = workflow.getNativeState().getMarkers();
        boolean added = false;
        for (TaskModel task : workflow.getTasks()) {
            if (!isAwaited(task) || markers.containsKey(task.getTaskId())) {
                continue;
            }
            TaskContinuation before =
                    task.getRetriedTaskId() == null ? null : markers.get(task.getRetriedTaskId());
            markers.put(
                    task.getTaskId(),
                    before != null ? before.reset() : markerOf(task.getWorkflowTask()));
            added = true;
        }
        return added;
    }

    /** What a decide must save and push for the native markers of an execution. */
    public static final class Wakeups {
        public boolean workflowTouched;
        public final List<Offer> offers = new ArrayList<>();
    }

    /**
     * A continuation's message, pushed to its queue once the offer is saved. An offer made to the
     * queue of the worker that holds the execution in memory also has a fallback: the same message
     * on the shared queue, visible only after the pin timeout, so that any worker continues the
     * execution if that worker does not take it in time.
     */
    public record Offer(
            String messageId,
            String queue,
            int delaySeconds,
            String fallbackQueue,
            int fallbackDelaySeconds) {

        public Offer(String messageId, String queue, int delaySeconds) {
            this(messageId, queue, delaySeconds, null, 0);
        }
    }

    /**
     * Offers, on every decide: the execution's slot goes to the handler lane when messages are
     * queued (a push wakes it this way), else to the runnable continuation that has waited longest.
     * A continuation can run once the task it waits for has ended and been evaluated; the first
     * one, until it applied. The slot holder's message is pushed again, which is idempotent and
     * repairs a lost message.
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
        // tasks this decide scheduled: their markers, saved with the workflow row below
        wakeups.workflowTouched = addMarkers(workflow);
        List<TaskModel> latest = latestAttempts(workflow);
        // the slot is the leased continuation: while there is one, nothing else is offered
        String holder = holder(workflow, latest);
        if (holder != null) {
            // pushed again if lost; a worker that dies leaves it to the queue's redelivery. Always
            // to the shared queue, where every offer has a message until it applies (its own, or
            // the fallback of one made to a pinned queue): a push to the pinned queue here could
            // add a second message for an offer a worker is already running
            wakeups.offers.add(new Offer(holder, queueName(workflow), 0));
            return wakeups;
        }

        TaskModel nextTask = null;
        TaskContinuation next = null;
        String nextId = null;
        TaskContinuation bootstrap = state.getBootstrap();
        if (hasQueuedMessages(workflow)) {
            // messages are handled before the code continues
            next = state.getHandlers();
            nextId = syntheticId(workflow.getWorkflowId(), HANDLER_LANE);
        } else if (bootstrap != null && !bootstrap.isApplied()) {
            // the first continuation: nothing else can run before it
            next = bootstrap;
            nextId = syntheticId(workflow.getWorkflowId(), MAIN_LANE);
        } else {
            // the one whose task ended first
            for (TaskModel task : latest) {
                if (isRunnable(workflow, task)
                        && (nextTask == null || task.getEndTime() < nextTask.getEndTime())) {
                    nextTask = task;
                }
            }
            if (nextTask != null) {
                next = markerOf(workflow, nextTask);
                nextId = nextTask.getTaskId();
            }
        }
        if (next != null) {
            wakeups.offers.add(offer(workflow, next, nextId, 0));
            wakeups.workflowTouched = true; // the slot is on the workflow row
        }
        return wakeups;
    }

    /** Whether a task's continuation can run: not run yet, and its task ended and evaluated. */
    private static boolean isRunnable(WorkflowModel workflow, TaskModel task) {
        TaskContinuation marker = markerOf(workflow, task);
        return marker != null
                && !marker.isApplied()
                && !marker.isLeased()
                && task.getStatus().isTerminal()
                && task.isExecuted();
    }

    /** Whether messages are queued for the execution's handler lane. */
    private boolean hasQueuedMessages(WorkflowModel workflow) {
        return workflow.getNativeState().getHandlers() != null
                && workflowMessageQueueDAO != null
                && workflowMessageQueueDAO.size(workflow.getWorkflowId()) > 0;
    }

    /**
     * The message id of the continuation leased right now, or null. One runs at a time: an offer is
     * made only while none is leased, so the leased marker is the execution's slot.
     */
    private static String holder(WorkflowModel workflow, List<TaskModel> latest) {
        for (TaskModel task : latest) {
            TaskContinuation marker = markerOf(workflow, task);
            if (marker != null && marker.isLeased()) {
                return task.getTaskId();
            }
        }
        NativeState state = workflow.getNativeState();
        if (state.getBootstrap() != null && state.getBootstrap().isLeased()) {
            return syntheticId(workflow.getWorkflowId(), MAIN_LANE);
        }
        if (state.getHandlers() != null && state.getHandlers().isLeased()) {
            return syntheticId(workflow.getWorkflowId(), HANDLER_LANE);
        }
        return null;
    }

    /**
     * Gives the execution's slot to a continuation: its marker is leased, at its retry count, from
     * the moment its message is delivered for the continuation timeout, in its code type's queue.
     * In memory, under the decide's lock; the caller saves the workflow row, which holds every
     * marker, and then pushes the returned offer.
     */
    public Offer offer(
            WorkflowModel workflow, TaskContinuation marker, String messageId, int delaySeconds) {
        marker.setLeased(true); // the execution's slot until it applies
        return offerTo(workflow, messageId, delaySeconds);
    }

    /**
     * Where a continuation's message goes. An execution pinned to a worker that polled its queue
     * lately is offered there, where the code is parked in memory and continues without a replay;
     * the same message becomes visible on the shared queue once the pin timeout has passed, so a
     * worker that died or stalled delays the execution by that long at most. Any other execution
     * goes to the shared queue of its code type.
     */
    private Offer offerTo(WorkflowModel workflow, String messageId, int delaySeconds) {
        String shared = queueName(workflow);
        String pinned = pinnedQueue(workflow);
        if (pinned == null || !pinnedWorkerPolls(workflow)) {
            return new Offer(messageId, shared, delaySeconds);
        }
        int pinTimeout = (int) properties.getNativeWorkflow().getPinTimeout().toSeconds();
        return new Offer(messageId, pinned, delaySeconds, shared, delaySeconds + pinTimeout);
    }

    /** Pushes the messages of saved offers, idempotently. */
    public void push(WorkflowModel workflow, List<Offer> offers) {
        if (queueDAO == null) {
            return;
        }
        for (Offer offer : offers) {
            queueDAO.pushIfNotExists(
                    offer.queue(), offer.messageId(), workflow.getPriority(), offer.delaySeconds());
            if (offer.fallbackQueue() != null) {
                queueDAO.pushIfNotExists(
                        offer.fallbackQueue(),
                        offer.messageId(),
                        workflow.getPriority(),
                        offer.fallbackDelaySeconds());
            }
        }
    }

    /** 1, 2, 4, ... seconds by how many times the continuation has been retried, capped. */
    private static int retryDelaySeconds(TaskContinuation marker) {
        int doublings = Math.min(marker.getRetryCount(), 6);
        return Math.min(1 << doublings, MAX_RETRY_DELAY_SECONDS);
    }

    /**
     * The continuation queue of an execution: the shared queue of its code type. The task domains a
     * run was started with do not apply to workflow code: any host of the code type may process any
     * continuation. The one domain a continuation is ever offered in is the pin's (see {@link
     * #pinnedQueue}), which a worker names for itself.
     */
    public String queueName(WorkflowModel workflow) {
        return codeType(workflow);
    }

    /**
     * The queue of the worker that holds the execution in memory: the code type in that worker's
     * domain. Null when no worker does.
     */
    public String pinnedQueue(WorkflowModel workflow) {
        NativeState state = workflow.getNativeState();
        return state == null || state.getPinnedTo() == null
                ? null
                : QueueUtils.getQueueName(codeType(workflow), state.getPinnedTo(), null, null);
    }

    /**
     * Whether the worker an execution is pinned to polled its queue lately, as a task domain counts
     * as active: within {@code conductor.app.activeWorkerLastPollTimeout}.
     */
    private boolean pinnedWorkerPolls(WorkflowModel workflow) {
        if (executionDAOFacade == null) {
            return false;
        }
        PollData last =
                executionDAOFacade.getTaskPollDataByDomain(
                        codeType(workflow), workflow.getNativeState().getPinnedTo());
        return last != null
                && last.getLastPollTime()
                        > System.currentTimeMillis()
                                - properties.getActiveWorkerLastPollTimeout().toMillis();
    }

    /**
     * After a continuation of the code applied: the worker that completed it says whether it keeps
     * the code parked ({@code pin}: its domain and a stamp of its choosing), and the execution is
     * pinned to it; otherwise the execution is pinned to no one. Either way a worker that parked
     * the code earlier finds another stamp, and replays.
     */
    private String pin(WorkflowModel workflow, Map<String, Object> out, String workerId) {
        NativeState state = workflow.getNativeState();
        Map<String, Object> pin = mapOrNull(out.get(OUTPUT_PIN));
        Object domain = pin == null ? null : pin.get(PIN_DOMAIN);
        Object stamp = pin == null ? null : pin.get(PIN_STAMP);
        if (domain == null
                || stamp == null
                || !PIN_DOMAIN_FORMAT.matcher(domain.toString()).matches()) {
            unpin(workflow);
            return null;
        }
        state.setPinnedTo(domain.toString());
        state.setStamp(stamp.toString());
        if (executionDAOFacade != null) {
            // the worker has just answered, which counts as polling its queue: the decide that
            // follows this completion offers the next continuation there
            executionDAOFacade.updateTaskLastPoll(codeType(workflow), domain.toString(), workerId);
        }
        return domain.toString();
    }

    /**
     * What the code has done is no longer what a worker may have parked (a rerun, a retry of the
     * code, a rejected fragment): no stamp matches, so the next continuation is replayed.
     */
    static void unpin(WorkflowModel workflow) {
        NativeState state = workflow.getNativeState();
        if (state != null) {
            state.setPinnedTo(null);
            state.setStamp(null);
        }
    }

    /** The definition's {@code native} metadata, which the SDK registers with the workflow. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> nativeMetadata(WorkflowModel workflow) {
        return (Map<String, Object>) workflow.getWorkflowDefinition().getMetadata().get("native");
    }

    /** How long a worker holds a continuation before it is offered again. */
    private static long continuationTimeoutMillis(WorkflowModel workflow) {
        Object seconds = nativeMetadata(workflow).get("continuationTimeoutSeconds");
        return (seconds instanceof Number t ? t.longValue() : 30) * 1000L;
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
                        ? markerOf(workflow, task)
                        : syntheticMarker(workflow, laneOfSynthetic(messageId));
        String shared = queueName(workflow);
        String pinned = pinnedQueue(workflow);
        if (marker == null
                || !marker.isLeased()
                || !(queueName.equals(shared) || queueName.equals(pinned))) {
            // not what was offered: a stale wake-up
            queueDAO.remove(queueName, messageId);
            return null;
        }
        TaskModel handed =
                task != null
                        ? task
                        : syntheticTask(
                                workflow,
                                marker,
                                idOf(workflow, laneOfSynthetic(messageId)),
                                messageId,
                                workerId);
        long timeout = continuationTimeoutMillis(workflow);
        queueDAO.setUnackTimeout(queueName, messageId, timeout);
        if (pinned != null && queueName.equals(pinned)) {
            // The worker that holds the execution took it. Continuing code that is in memory
            // takes it milliseconds, so it gets the pin timeout again to answer, not the whole
            // continuation timeout: a worker killed with its poll still open on the server takes
            // the message without ever seeing it, and any worker must be able to step in as soon
            // as for one that never polled. A worker that is alive and slower than that is run
            // twice; the first completion applies, as for any redelivery.
            queueDAO.setUnackTimeout(
                    shared,
                    messageId,
                    Math.min(timeout, properties.getNativeWorkflow().getPinTimeout().toMillis()));
        } else if (pinned != null) {
            // its turn passed to any worker: it is not run from the pinned queue as well
            queueDAO.remove(pinned, messageId);
        }
        Task leased = handed.toTask();
        // what a worker that kept the code parked needs to continue it without a fetch: whether
        // its copy is current (the stamp), and the variables this continuation starts from
        leased.setContinuation(
                marker.handedOut(state.getStamp(), new HashMap<>(workflow.getVariables())));
        return leased;
    }

    /**
     * The task handed to a worker for a lane that has no task: bootstrap or handlers. Its entry
     * names the continuation as a task's entry does.
     */
    private TaskModel syntheticTask(
            WorkflowModel workflow,
            TaskContinuation marker,
            Id id,
            String messageId,
            String workerId) {
        WorkflowTask entry = new WorkflowTask();
        entry.setName(codeType(workflow));
        entry.setTaskReferenceName(id.lane());
        entry.setType(codeType(workflow));
        entry.getMetadata().put(METADATA_LANE, id.lane());
        entry.getMetadata().put(METADATA_CONTINUATION, id.n());
        TaskModel task = new TaskModel();
        task.setWorkflowTask(entry);
        task.setTaskId(messageId);
        task.setTaskType(codeType(workflow));
        task.setTaskDefName(codeType(workflow));
        task.setReferenceTaskName(id.lane());
        task.setWorkflowInstanceId(workflow.getWorkflowId());
        task.setWorkflowType(workflow.getWorkflowName());
        task.setCorrelationId(workflow.getCorrelationId());
        task.setWorkflowPriority(workflow.getPriority());
        task.setStatus(TaskModel.Status.IN_PROGRESS);
        task.setScheduledTime(System.currentTimeMillis());
        task.setStartTime(task.getScheduledTime());
        task.setWorkerId(workerId);
        Map<String, Object> input = new HashMap<>();
        if (id.isHandlers() && workflowMessageQueueDAO != null) {
            // claimed under the lane, with the run's number as the ordinal, so a re-run of this
            // continuation gets the same messages and the next run deletes them (see
            // JdbcWorkflowMessageQueueDAO)
            var messages =
                    workflowMessageQueueDAO.claim(
                            workflow.getWorkflowId(), HANDLER_BATCH_SIZE, id.lane(), id.n());
            input.put(INPUT_MESSAGES, messages);
        }
        task.setInputData(input);
        return task;
    }

    // ------------------------------------------------------------------ complete

    /** What the executor persists and does after a completion was applied in memory. */
    public static final class Completion {
        public final List<TaskModel> touchedTasks = new ArrayList<>();
        public String ackQueue;

        /** The queue of the worker the execution was pinned to when offered, or null. */
        public String ackPinnedQueue;

        /** The domain of the completing worker, when it now holds the execution in memory. */
        public String pinnedTo;

        public String ackId;
        public boolean deferred;
        public int retryDelaySeconds;
        public boolean waitsForHandlers;
        public boolean finished;
        public String reason;
        public TaskModel causeTask;
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
                        ? markerOf(workflow, awaited)
                        : syntheticMarker(workflow, laneOfSynthetic(messageId));
        if (state == null || marker == null) {
            throw new ConflictException(
                    "Task %s of workflow %s is not a continuation",
                    messageId, workflow.getWorkflowId());
        }
        Id id = awaited != null ? idOf(awaited) : idOf(workflow, laneOfSynthetic(messageId));
        int retryCount = out.get(OUTPUT_RETRY_COUNT) instanceof Number c ? c.intValue() : -1;
        int n = out.get(OUTPUT_N) instanceof Number number ? number.intValue() : -1;
        if (!marker.isLeased() || id.n() != n || marker.getRetryCount() != retryCount) {
            Monitors.recordNativeStaleCompletion(workflow.getWorkflowName());
            throw new ConflictException(
                    "Continuation %s#%d of workflow %s is not leased at retry %d (%s)",
                    id.lane(), n, workflow.getWorkflowId(), retryCount, marker);
        }
        Completion completion = new Completion();
        completion.ackQueue = queueName(workflow);
        completion.ackPinnedQueue = pinnedQueue(workflow); // as offered: applying may change it
        completion.ackId = messageId;
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
            fragmentValidator.validateProtocol(out);
            boolean done = Boolean.TRUE.equals(out.get(OUTPUT_DONE));
            Map<String, Object> error = mapOrNull(out.get(OUTPUT_ERROR));

            Map<String, Object> changedVariables = variablesOf(out);
            if (done && error == null && id.isMain()) {
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
            List<WorkflowTask> fragment = fragmentValidator.readFragment(out);
            List<Run> runs = runs(fragment, awaited);
            WorkflowDef definition = workflow.getWorkflowDefinition();
            if (!fragment.isEmpty()) {
                fragmentValidator.validateFragment(
                        definition, fragment, runs, codeType(workflow), thrownRef(done, error, id));
            }
            fragmentValidator.checkVariablesSize(workflow, changedVariables);

            if (!fragment.isEmpty()) {
                // which continuation appended each entry: a rerun undoes a continuation's entries
                for (WorkflowTask entry : fragment) {
                    entry.getMetadata().put(APPENDED_BY, id.key());
                }
                fragmentValidator.populateTaskDefinitions(workflowName, fragment);
                for (Run run : runs) {
                    if (run.anchor() == null) {
                        definition.getTasks().addAll(run.entries());
                    } else if (!NativeDefinitionEdits.insertAfter(
                            definition, run.anchor(), run.entries())) {
                        // validateFragment checked every anchor
                        throw new IllegalStateException("anchor " + run.anchor() + " not found");
                    }
                }
            }
            workflow.getVariables().putAll(changedVariables);
            // every handled message, by the sender's id: an update's reply, or just when it ran
            if (out.get(OUTPUT_REPLIES) instanceof Map<?, ?> replies) {
                state.setReplies(objectMapper.convertValue(replies, MAP_TYPE));
            }
            release(workflow, out, completion);
            marker.setEndTime(endTimeNow(workflow));
            marker.setLeased(false);
            marker.setRecorded(mapOrEmpty(out.get(OUTPUT_RECORDED)));
            marker.setResumedWith(mapOrEmpty(out.get(OUTPUT_RESUMED_WITH)));
            marker.setDone(done);
            // what held the code back no longer does
            workflow.setReasonForIncompletion(null);
            if (done && error == null && id.isMain()) {
                // the code returned: its value is the run's output, which the decider completes
                // the run with once everything this continuation appended has run
                workflow.setOutput(mapOrEmpty(out.get(OUTPUT_RESULT)));
            }
            if (id.isHandlers()) {
                // the handler lane runs once per batch of messages; it is never done. It changes
                // variables, not what the code has done: parked code stays as current as it was
                state.setHandlers(TaskContinuation.handlerRun(id.n() + 1));
            } else {
                completion.pinnedTo = pin(workflow, out, result.getWorkerId());
            }
            if (awaited != null) {
                // re-evaluated by the decider, which finds the appended entries after it
                awaited.setExecuted(false);
                completion.touchedTasks.add(awaited);
            }
            // The code returned: the decider completes the workflow, with the marker's result as
            // the output, once everything this fragment appended has run
            // (DeciderService.checkForWorkflowCompletion). The code raised: the workflow fails.
            if (done && error != null && id.isMain()) {
                completion.finished = true;
                completion.reason = String.valueOf(error.get("message"));
                // saved with the marker, so the run reads as failed before it is ended
                workflow.setReasonForIncompletion(completion.reason);
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
            unpin(workflow); // the worker believes its fragment was appended
            marker.setEndTime(endTimeNow(workflow));
            marker.setLeased(false);
            marker.setDone(true);
            workflow.setReasonForIncompletion(e.getMessage());
            completion.finished = true;
            completion.reason = e.getMessage();
            return completion;
        }
    }

    /** The latest iteration of a loop's task that did not succeed, or null. */
    private static TaskModel latestFailedIteration(WorkflowModel workflow, String ref) {
        TaskModel latest = null;
        for (TaskModel task : workflow.getTasks()) {
            boolean failed =
                    !task.getStatus().isSuccessful()
                            || task.getStatus() == TaskModel.Status.COMPLETED_WITH_ERRORS;
            if (task.isLoopOverTask()
                    && failed
                    && ref.equals(
                            TaskUtils.removeIterationFromTaskRefName(task.getReferenceTaskName()))
                    && (latest == null || task.getSeq() > latest.getSeq())) {
                latest = task;
            }
        }
        return latest;
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
        if (task == null) {
            // a task in a loop is named without its iteration: the latest that failed
            task = latestFailedIteration(workflow, ref.toString());
        }
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
        thrown.setTaskId(UUID.randomUUID().toString());
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
        marker.setLeased(false);
        marker.setRetryCount(marker.getRetryCount() + 1);
        // one continuation runs at a time, so why it did not apply is why the run waits; shown
        // until a continuation applies
        workflow.setReasonForIncompletion(why);
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
        if (handlers.isLeased()) {
            return "handler run " + handlers.getN() + " is leased";
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

    /**
     * When a task's continuation applies: now, and always after the run's last applied
     * continuation, so these times are the order replay follows. Applies are serialized by the
     * run's lock.
     */
    private static long endTimeNow(WorkflowModel workflow) {
        TaskContinuation bootstrap = workflow.getNativeState().getBootstrap();
        long latest = bootstrap == null ? 0 : bootstrap.getEndTime();
        for (TaskContinuation marker : workflow.getNativeState().getMarkers().values()) {
            latest = Math.max(latest, marker.getEndTime());
        }
        return Math.max(System.currentTimeMillis(), latest + 1);
    }

    // ------------------------------------------------------------------ variables

    /** The variables the completion wrote. */
    private Map<String, Object> variablesOf(Map<String, Object> output) {
        Object raw = output.get(OUTPUT_VARIABLES);
        if (raw == null) {
            return Map.of();
        }
        Map<String, Object> variables;
        try {
            variables = objectMapper.convertValue(raw, MAP_TYPE);
        } catch (IllegalArgumentException | JacksonException e) {
            throw new RejectedFragmentException("variables", "variables must be an object");
        }
        return variables == null ? Map.of() : variables;
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
}
