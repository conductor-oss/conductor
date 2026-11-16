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
 * <p>Native workflow code pauses only on a task. The task it paused on carries this marker: whether
 * the continuation is leased to a worker, how many times it was offered again, and, once applied,
 * what it produced, which a worker replays from. Which continuation it is (the lane of the code and
 * its number) is the task's entry's {@code metadata}: {@code lane} and {@code continuation}.
 * Whether it can run follows from the task: once the task has ended and the decider has evaluated
 * it. The workflow's first continuation and its handler runs have no task to wait on; the workflow
 * record carries their markers (see {@code NativeState}).
 */
@ProtoMessage
public class TaskContinuation {

    /**
     * Leased to a worker: offered, and not yet applied or handed back. At most one continuation of
     * an execution is leased at a time.
     */
    @ProtoField(id = 1)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean leased;

    /**
     * How many times this continuation was offered again: 0 on its first offer, one more after each
     * attempt that did not apply (deferred, retried, rerun), as a task's retry count. A completion
     * names the count it was offered at, so one from an earlier attempt never applies.
     */
    @ProtoField(id = 2)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int retryCount;

    /**
     * When it applied, 0 until then: later than every earlier apply in the run, the order replay
     * follows.
     */
    @ProtoField(id = 3)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private long endTime;

    /** Whether it ended its lane: the code returned or raised there. */
    @ProtoField(id = 4)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean done;

    /** The values the code recorded while it ran ({@code once}, {@code now}, {@code random}). */
    @ProtoField(id = 5)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> recorded = new HashMap<>();

    /** The workflow variables it started from, which a replay restores before it. */
    @ProtoField(id = 6)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> resumedWith = new HashMap<>();

    /**
     * The handler lane's run number, from 1; 0 on every other marker, whose number is its entry's.
     * Every handler run has the same message id, so this tells a run from the one before it.
     */
    @ProtoField(id = 7)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private int n;

    public TaskContinuation() {}

    /** A new marker of a handler run. */
    public static TaskContinuation handlerRun(int n) {
        TaskContinuation marker = new TaskContinuation();
        marker.n = n;
        return marker;
    }

    /**
     * A fresh marker for the same continuation, as a rerun task or a restarted run carries: another
     * attempt, so its retry count goes up and a completion of an offer made before the reset can
     * never match it.
     */
    public TaskContinuation reset() {
        TaskContinuation fresh = new TaskContinuation();
        fresh.n = n;
        fresh.retryCount = retryCount + 1;
        return fresh;
    }

    @JsonIgnore // derived from endTime, not stored
    public boolean isApplied() {
        return endTime > 0;
    }

    public boolean isLeased() {
        return leased;
    }

    public void setLeased(boolean leased) {
        this.leased = leased;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
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

    public int getN() {
        return n;
    }

    public void setN(int n) {
        this.n = n;
    }

    @Override
    public String toString() {
        return "TaskContinuation{"
                + (n > 0 ? "run " + n + " " : "")
                + (leased ? "leased" : endTime > 0 ? "applied" : "not applied")
                + (retryCount > 0 ? " retry " + retryCount : "")
                + "}";
    }
}
