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
package org.conductoross.conductor.core.execution.tasks.annotated;

import org.conductoross.conductor.sdk.workflow.task.InputParam;
import org.conductoross.conductor.sdk.workflow.task.WorkerTask;
import org.springframework.stereotype.Component;

@Component
public class SampleWorkers {

    @WorkerTask("HELLO")
    public String hello(@InputParam("name") String name) {
        return "Hello %s, from the sample worker".formatted(name);
    }
}
