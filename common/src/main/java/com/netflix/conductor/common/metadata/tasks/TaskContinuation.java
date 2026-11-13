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

    @ProtoField(id = 4)
    private long epoch;

    @ProtoField(id = 5)
    private String worker;

    @ProtoField(id = 6)
    private String queue;

    @ProtoField(id = 7)
    private long queuedAt;

    @ProtoField(id = 8)
    private long leasedAt;

    @ProtoField(id = 9)
    private long appliedAt;

    @ProtoField(id = 10)
    private int attempts;

    @ProtoField(id = 11)
    private String deferred;

    @ProtoField(id = 12)
    private long step;

    @ProtoField(id = 13)
    private Map<String, Object> recorded = new HashMap<>();

    @ProtoField(id = 14)
    private Map<String, Object> resumedWith = new HashMap<>();

    @ProtoField(id = 15)
    private boolean done;

    // never null: an empty map is no result, or no error (the generated proto mapper copies them)
    @ProtoField(id = 16)
    private Map<String, Object> result = new HashMap<>();

    @ProtoField(id = 17)
    private Map<String, Object> error = new HashMap<>();

    public TaskContinuation() {}

    public TaskContinuation(String lane, int n) {
        this.lane = lane;
        this.n = n;
    }

    /** A fresh marker for the same lane and continuation, as a retried attempt carries. */
    public TaskContinuation reset() {
        return new TaskContinuation(lane, n);
    }

    public boolean isWaiting() {
        return WAITING.equals(state);
    }

    public boolean isRunnable() {
        return RUNNABLE.equals(state);
    }

    public boolean isLeased() {
        return LEASED.equals(state);
    }

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

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public String getWorker() {
        return worker;
    }

    public void setWorker(String worker) {
        this.worker = worker;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public long getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(long queuedAt) {
        this.queuedAt = queuedAt;
    }

    public long getLeasedAt() {
        return leasedAt;
    }

    public void setLeasedAt(long leasedAt) {
        this.leasedAt = leasedAt;
    }

    public long getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(long appliedAt) {
        this.appliedAt = appliedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getDeferred() {
        return deferred;
    }

    public void setDeferred(String deferred) {
        this.deferred = deferred;
    }

    public long getStep() {
        return step;
    }

    public void setStep(long step) {
        this.step = step;
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

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result == null ? new HashMap<>() : result;
    }

    public Map<String, Object> getError() {
        return error;
    }

    /** Whether the code raised: the continuation ended its lane with an error. */
    public boolean hasError() {
        return !error.isEmpty();
    }

    public void setError(Map<String, Object> error) {
        this.error = error == null ? new HashMap<>() : error;
    }

    @Override
    public String toString() {
        return "TaskContinuation{"
                + lane
                + "#"
                + n
                + " "
                + state
                + (epoch > 0 ? " epoch " + epoch : "")
                + "}";
    }
}
