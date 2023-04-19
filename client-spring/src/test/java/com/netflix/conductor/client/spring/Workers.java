/*
 * Copyright 2023 Netflix, Inc.
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
package com.netflix.conductor.client.spring;

import java.util.Date;

import org.springframework.stereotype.Component;

import com.netflix.conductor.sdk.workflow.executor.task.TaskContext;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

@Component
public class Workers {

    @WorkerTask(value = "hello", threadCount = 3)
    public String helloWorld(@InputParam("name") String name) {
        TaskContext context = TaskContext.get();
        System.out.println(new Date() + ":: Poll count: " + context.getPollCount());
        if (context.getPollCount() < 5) {
            context.addLog("Not ready yet, poll count is only " + context.getPollCount());
            context.setCallbackAfter(1);
        }

        return "Hello, " + name;
    }

    @WorkerTask(value = "hello_again", pollingInterval = 333)
    public String helloAgain(@InputParam("name") String name) {
        TaskContext context = TaskContext.get();
        System.out.println(new Date() + ":: Poll count: " + context.getPollCount());
        if (context.getPollCount() < 5) {
            context.addLog("Not ready yet, poll count is only " + context.getPollCount());
            context.setCallbackAfter(1);
        }

        return "Hello (again), " + name;
    }
}
