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
package com.netflix.conductor.sdk.workflow.def.tasks;

import com.netflix.conductor.common.metadata.tasks.TaskType;

public class Python extends Task<Python> {
    public Python(String taskReferenceName, String script) {
        super(taskReferenceName, TaskType.INLINE);
        if (script == null || script.isEmpty()) {
            throw new IllegalArgumentException("Python script cannot be null or empty");
        }
        super.input("evaluatorType", "python");
        super.input("expression", script);
    }
}
