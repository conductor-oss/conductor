/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.sdk.workflow.def.tasks;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

/** Wait task */
public class Wait extends Task<Wait> {

    public static final String DURATION_INPUT = "duration";
    public static final String UNTIL_INPUT = "until";

    public static final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    /**
     * Wait until and external signal completes the task. The external signal can be either an API
     * call (POST /api/task) to update the task status or an event coming from a supported external
     * queue integration like SQS, Kafka, NATS, AMQP etc.
     *
     * <p><br>
     * see <a href=https://netflix.github.io/conductor/reference-docs/wait-task/>
     * https://netflix.github.io/conductor/reference-docs/wait-task</a> for more details
     *
     * @param taskReferenceName
     */
    public Wait(String taskReferenceName) {
        super(taskReferenceName, TaskType.WAIT);
    }

    public Wait(String taskReferenceName, Duration waitFor) {
        super(taskReferenceName, TaskType.WAIT);
        long seconds = waitFor.getSeconds();
        input(DURATION_INPUT, seconds + "s");
    }

    public Wait(String taskReferenceName, ZonedDateTime waitUntil) {
        super(taskReferenceName, TaskType.WAIT);
        String formattedDateTime = waitUntil.format(dateTimeFormatter);
        input(UNTIL_INPUT, formattedDateTime);
    }

    Wait(WorkflowTask workflowTask) {
        super(workflowTask);
    }
}
