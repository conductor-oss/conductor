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

import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;

import com.google.common.base.Strings;

/** Task to publish Events to external queuing systems like SQS, NATS, AMQP etc. */
public class Event extends Task<Event> {

    private static final String SINK_PARAMETER = "sink";

    /**
     * @param taskReferenceName Unique reference name within the workflow
     * @param eventSink qualified name of the event sink where the message is published. Using the
     *     format sink_type:location e.g. sqs:sqs_queue_name, amqp_queue:queue_name,
     *     amqp_exchange:queue_name, nats:queue_name
     */
    public Event(String taskReferenceName, String eventSink) {
        super(taskReferenceName, TaskType.EVENT);
        if (Strings.isNullOrEmpty(eventSink)) {
            throw new AssertionError("Null/Empty eventSink");
        }
        super.input(SINK_PARAMETER, eventSink);
    }

    Event(WorkflowTask workflowTask) {
        super(workflowTask);
    }

    public String getSink() {
        return (String) getInput().get(SINK_PARAMETER);
    }
}
