/*
 * Copyright 2021 Conductor Authors.
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
package com.netflix.conductor.common.metadata.tasks;

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The continuation of a native (code-defined) workflow that runs when this task finishes.
 *
 * <p>Native workflow code pauses only on a task. The task it paused on carries this marker: which
 * lane of the code waits on it, which continuation of that lane runs next, whether that
 * continuation is waiting for the task, queued for a worker, leased by one, or applied. Once
 * applied, the marker is the record of what the continuation produced, which a worker replays from.
 * The workflow's first continuation and its handler runs have no task to wait on; the workflow
 * record carries their markers (see {@code NativeState}).
 */
@ProtoMessage
public class TaskContinuation {

    public static final String WAITING = "WAITING";
    public static final String RUNNABLE = "RUNNABLE";
    public static final String LEASED = "LEASED";
    public static final String APPLIED = "APPLIED";

    @ProtoField(id = 1)
    private String lane;

    @ProtoField(id = 2)
    private int n;

    @ProtoField(id = 3)
    private String state = WAITING;

    /**
     * How many times this continuation was offered again: 0 on its first offer, one more after each
     * attempt that did not apply (deferred, retried, rerun), as a task's retry count. A completion
     * names the count it was offered at, so one from an earlier attempt never applies.
     */
    @ProtoField(id = 4)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int retryCount;

    /** When it became runnable; the oldest runnable continuation is offered first. */
    @ProtoField(id = 5)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long scheduledTime;

    /**
     * When it applied, on a task's marker: later than every earlier apply in the run, the order
     * replay follows. The workflow's first continuation always applies first and does not need it.
     */
    @ProtoField(id = 6)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long endTime;

    /**
     * Why its last completion did not apply, until one does: shown to people. A hold (a reason
     * starting {@code diverged: }) keeps the time it became runnable while it lasts.
     */
    @ProtoField(id = 7)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String reasonForIncompletion;

    @ProtoField(id = 8)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> recorded = new HashMap<>();

    @ProtoField(id = 9)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> resumedWith = new HashMap<>();

    @ProtoField(id = 10)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean done;

    public TaskContinuation() {}

    public TaskContinuation(String lane, int n) {
        this.lane = lane;
        this.n = n;
    }

    /**
     * A fresh marker for the same lane and continuation, as a rerun task or a restarted run
     * carries: another attempt, so its retry count goes up and a completion of an offer made before
     * the reset can never match it.
     */
    public TaskContinuation reset() {
        TaskContinuation fresh = new TaskContinuation(lane, n);
        fresh.retryCount = retryCount + 1;
        return fresh;
    }

    @JsonIgnore // derived from state, not stored
    public boolean isWaiting() {
        return WAITING.equals(state);
    }

    @JsonIgnore
    public boolean isRunnable() {
        return RUNNABLE.equals(state);
    }

    @JsonIgnore
    public boolean isLeased() {
        return LEASED.equals(state);
    }

    @JsonIgnore
    public boolean isApplied() {
        return APPLIED.equals(state);
    }

    public String getLane() {
        return lane;
    }

    public void setLane(String lane) {
        this.lane = lane;
    }

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(long scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public String getReasonForIncompletion() {
        return reasonForIncompletion;
    }

    public void setReasonForIncompletion(String reasonForIncompletion) {
        this.reasonForIncompletion = reasonForIncompletion;
    }

    public Map<String, Object> getRecorded() {
        return recorded;
    }

    public void setRecorded(Map<String, Object> recorded) {
        this.recorded = recorded;
    }

    public Map<String, Object> getResumedWith() {
        return resumedWith;
    }

    public void setResumedWith(Map<String, Object> resumedWith) {
        this.resumedWith = resumedWith;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    @Override
    public String toString() {
        return "TaskContinuation{"
                + lane
                + "#"
                + n
                + " "
                + state
                + (retryCount > 0 ? " retry " + retryCount : "")
                + "}";
    }
}
