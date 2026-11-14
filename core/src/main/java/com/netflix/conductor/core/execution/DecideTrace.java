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
package com.netflix.conductor.core.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.databind.ObjectMapper;

/**
 * A trace of what each decide left a workflow in, for checking runs step by step against the
 * specification ({@code proposals/workflow-semantics}). Off unless the system property {@code
 * conductor.trace.dir} names a directory: then each decide appends one line to {@code
 * <dir>/<workflowId>.jsonl} with the workflow's status and each task's reference, type, seq, status
 * and retry count. Only for tests; it changes nothing the server does.
 */
public final class DecideTrace {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecideTrace.class);
    private static final String DIR = System.getProperty("conductor.trace.dir");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DecideTrace() {}

    public static boolean enabled() {
        return DIR != null;
    }

    /** Appends what the workflow is now; the caller holds the workflow's lock. */
    public static void record(WorkflowModel workflow) {
        if (DIR == null || workflow == null) {
            return;
        }
        try {
            List<Object> tasks = new ArrayList<>();
            for (TaskModel t : workflow.getTasks()) {
                tasks.add(
                        List.of(
                                t.getReferenceTaskName(),
                                t.getTaskType(),
                                t.getSeq(),
                                t.getStatus().name(),
                                t.getRetryCount()));
            }
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("at", System.currentTimeMillis());
            line.put("status", workflow.getStatus().name());
            line.put("tasks", tasks);
            Files.writeString(
                    Path.of(DIR, workflow.getWorkflowId() + ".jsonl"),
                    MAPPER.writeValueAsString(line) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not trace workflow {}", workflow.getWorkflowId(), e);
        }
    }
}
