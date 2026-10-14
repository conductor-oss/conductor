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
package org.conductoross.conductor.common.run;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Admission of a root execution, alongside its workflow status. A QUEUED root reports workflow
 * status RUNNING or PAUSED but has dispatched no tasks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Admission {

    public enum State {
        QUEUED,
        ADMITTED,
        CANCELED,
        EXPIRED,
        FAILED
    }

    private State state;

    /** Epoch milliseconds. */
    private Long acceptedAt;

    /** Epoch milliseconds. */
    private Long admittedAt;

    private String reason;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Long getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Long acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public Long getAdmittedAt() {
        return admittedAt;
    }

    public void setAdmittedAt(Long admittedAt) {
        this.admittedAt = admittedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
