/*
 * Copyright 2024 Conductor Authors.
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
package com.netflix.conductor.client.events.listeners;

import com.netflix.conductor.client.events.dispatcher.EventDispatcher;
import com.netflix.conductor.client.events.task.TaskClientEvent;
import com.netflix.conductor.client.events.task.TaskPayloadUsedEvent;
import com.netflix.conductor.client.events.task.TaskResultPayloadSizeEvent;
import com.netflix.conductor.client.events.taskrunner.PollCompleted;
import com.netflix.conductor.client.events.taskrunner.PollFailure;
import com.netflix.conductor.client.events.taskrunner.PollStarted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionCompleted;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionFailure;
import com.netflix.conductor.client.events.taskrunner.TaskExecutionStarted;
import com.netflix.conductor.client.events.taskrunner.TaskRunnerEvent;
import com.netflix.conductor.client.events.workflow.WorkflowClientEvent;
import com.netflix.conductor.client.events.workflow.WorkflowInputPayloadSizeEvent;
import com.netflix.conductor.client.events.workflow.WorkflowPayloadUsedEvent;
import com.netflix.conductor.client.events.workflow.WorkflowStartedEvent;

public class ListenerRegister {

    public static void register(TaskRunnerEventsListener listener, EventDispatcher<TaskRunnerEvent> dispatcher) {
        dispatcher.register(PollFailure.class, listener::consume);
        dispatcher.register(PollCompleted.class, listener::consume);
        dispatcher.register(PollStarted.class, listener::consume);
        dispatcher.register(TaskExecutionStarted.class, listener::consume);
        dispatcher.register(TaskExecutionCompleted.class, listener::consume);
        dispatcher.register(TaskExecutionFailure.class, listener::consume);
    }

    public static void register(TaskClientListener listener, EventDispatcher<TaskClientEvent> dispatcher) {
        dispatcher.register(TaskResultPayloadSizeEvent.class, listener::consume);
        dispatcher.register(TaskPayloadUsedEvent.class, listener::consume);
    }

    public static void register(WorkflowClientListener listener, EventDispatcher<WorkflowClientEvent> dispatcher) {
        dispatcher.register(WorkflowStartedEvent.class, listener::consume);
        dispatcher.register(WorkflowInputPayloadSizeEvent.class, listener::consume);
        dispatcher.register(WorkflowPayloadUsedEvent.class, listener::consume);
    }
}
