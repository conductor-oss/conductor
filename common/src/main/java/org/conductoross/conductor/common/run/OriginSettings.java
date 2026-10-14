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

/** The origin domain's settings, captured when the root execution was accepted. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OriginSettings {

    /** What a root start does when the start budget is full. */
    public enum BudgetFullBehavior {
        QUEUE,
        REJECT
    }

    private Integer priority;
    private BudgetFullBehavior budgetFullBehavior;
    private Integer maxQueuedRoots;
    private Integer maxQueueWaitSeconds;
    private Integer retentionDays;

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public BudgetFullBehavior getBudgetFullBehavior() {
        return budgetFullBehavior;
    }

    public void setBudgetFullBehavior(BudgetFullBehavior budgetFullBehavior) {
        this.budgetFullBehavior = budgetFullBehavior;
    }

    public Integer getMaxQueuedRoots() {
        return maxQueuedRoots;
    }

    public void setMaxQueuedRoots(Integer maxQueuedRoots) {
        this.maxQueuedRoots = maxQueuedRoots;
    }

    public Integer getMaxQueueWaitSeconds() {
        return maxQueueWaitSeconds;
    }

    public void setMaxQueueWaitSeconds(Integer maxQueueWaitSeconds) {
        this.maxQueueWaitSeconds = maxQueueWaitSeconds;
    }

    public Integer getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(Integer retentionDays) {
        this.retentionDays = retentionDays;
    }
}
