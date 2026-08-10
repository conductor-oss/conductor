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
package io.orkes.conductor.scheduler.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.orkes.conductor.scheduler.model.WorkflowSchedule;

/** Stub listener default implementation. Logs each schedule change at debug level. */
public class ScheduleChangeListenerStub implements ScheduleChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleChangeListenerStub.class);

    @Override
    public void onScheduleRegistered(WorkflowSchedule schedule) {
        LOGGER.debug("Schedule {} registered", schedule.getName());
    }

    @Override
    public void onScheduleUpdated(WorkflowSchedule schedule) {
        LOGGER.debug("Schedule {} updated", schedule.getName());
    }

    @Override
    public void onScheduleDeleted(String name) {
        LOGGER.debug("Schedule {} deleted", name);
    }

    @Override
    public void onSchedulePaused(WorkflowSchedule schedule) {
        LOGGER.debug("Schedule {} paused", schedule.getName());
    }

    @Override
    public void onScheduleResumed(WorkflowSchedule schedule) {
        LOGGER.debug("Schedule {} resumed", schedule.getName());
    }
}
