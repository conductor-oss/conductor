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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.execution.NativeWorkflows.Id;
import com.netflix.conductor.core.execution.evaluators.NativeCodeEvaluator;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static com.netflix.conductor.core.execution.NativeContinuationService.OUTPUT_PROTOCOL;
import static com.netflix.conductor.core.execution.NativeWorkflows.METADATA_CONTINUATION;
import static com.netflix.conductor.core.execution.NativeWorkflows.idOf;
import static com.netflix.conductor.core.execution.NativeWorkflows.laneOf;

/**
 * The rules a continuation's completion must keep before anything of it is written: its protocol
 * version, what its fragment may hold and where it goes, and the size limits of the definition, the
 * fragment and the variables. A completion that breaks a rule is rejected with a {@link
 * RejectedFragmentException}, and changes neither the definition nor the variables.
 */
@Component
public class NativeFragmentValidator {

    private static final int PROTOCOL = 2;
    private static final String OUTPUT_FRAGMENT = "fragment";

    private static final TypeReference<List<WorkflowTask>> FRAGMENT_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ConductorProperties properties;
    private final MetadataMapperService metadataMapperService;
    private final Map<String, TaskMapper> taskMappers;

    public NativeFragmentValidator(
            ObjectMapper objectMapper,
            ConductorProperties properties,
            MetadataMapperService metadataMapperService,
            @Qualifier("taskMappersByTaskType") Map<String, TaskMapper> taskMappers) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.metadataMapperService = metadataMapperService;
        this.taskMappers = taskMappers;
    }

    void validateProtocol(Map<String, Object> output) {
        Object protocol = output.get(OUTPUT_PROTOCOL);
        if (!(protocol instanceof Number)) {
            throw new RejectedFragmentException(
                    "protocol",
                    "a continuation completion must declare a numeric protocol version");
        }
        if (((Number) protocol).intValue() != PROTOCOL) {
            throw new RejectedFragmentException(
                    "protocol",
                    "protocol version " + protocol + " is not supported; expected " + PROTOCOL);
        }
    }

    /** A run of fragment entries and the entry it goes after (null: at the end of the list). */
    record Run(String anchor, List<WorkflowTask> entries) {}

    /**
     * The runs a fragment is inserted as. The fragment goes after the task the code paused on; an
     * entry that names another anchor ({@code metadata.after}) starts a run that goes there
     * instead: code that left a switch case appends after the switch, not inside the case where it
     * paused, and what it drew in the case before leaving stays in the case.
     */
    static List<Run> runs(List<WorkflowTask> fragment, TaskModel awaited) {
        List<Run> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= fragment.size(); i++) {
            if (i < fragment.size() && anchorOf(fragment.subList(i, i + 1), null) == null) {
                continue;
            }
            List<WorkflowTask> run = new ArrayList<>(fragment.subList(start, i));
            runs.add(new Run(anchorOf(run, start == 0 ? awaited : null), run));
            start = i;
        }
        return runs;
    }

    /**
     * Where a fragment is inserted: its first entry's {@code metadata.after}, else the awaited
     * task.
     */
    private static String anchorOf(List<WorkflowTask> fragment, TaskModel awaited) {
        if (!fragment.isEmpty() && fragment.get(0).getMetadata() != null) {
            Object after = fragment.get(0).getMetadata().get("after");
            if (after != null && !after.toString().isBlank()) {
                return after.toString();
            }
        }
        return awaited == null ? null : awaited.getReferenceTaskName();
    }

    List<WorkflowTask> readFragment(Map<String, Object> output) {
        Object raw = output.get(OUTPUT_FRAGMENT);
        if (raw == null) {
            return List.of();
        }
        try {
            List<WorkflowTask> fragment = objectMapper.convertValue(raw, FRAGMENT_TYPE);
            return fragment == null ? List.of() : fragment;
        } catch (IllegalArgumentException | JacksonException e) {
            throw new RejectedFragmentException(
                    "fragment", "fragment is not a list of workflow tasks: " + e.getMessage());
        }
    }

    /**
     * The reference of the entry that marks where the code threw, when this completion ends the
     * main lane with an exception: the only TERMINATE a fragment may hold, because the workflow
     * fails in this same completion and the entry is never scheduled.
     */
    static String thrownRef(boolean done, Map<String, Object> error, Id id) {
        if (!done || error == null || !id.isMain()) {
            return null;
        }
        Object ref = error.get("ref");
        return ref == null ? null : ref.toString();
    }

    void validateFragment(
            WorkflowDef definition,
            List<WorkflowTask> fragment,
            List<Run> runs,
            String codeType,
            String thrownRef) {
        int count = 0; // nested entries count
        for (WorkflowTask task : fragment) {
            count += task.collectTasks().size();
        }
        List<WorkflowTask> current = definition.collectTasks();
        int total = current.size() + count;
        int maxTotal = properties.getNativeWorkflow().getMaxDefinitionTasks();
        if (total > maxTotal) {
            throw new RejectedFragmentException(
                    "size",
                    "definition would have "
                            + total
                            + " tasks, more than the limit of "
                            + maxTotal);
        }
        long bytes = serializedSize(fragment);
        long maxBytes = properties.getNativeWorkflow().getMaxFragmentBytes().toBytes();
        if (bytes > maxBytes) {
            throw new RejectedFragmentException(
                    "size", "fragment is " + bytes + " bytes, more than the limit of " + maxBytes);
        }
        Set<String> existing = new HashSet<>();
        current.forEach(t -> existing.add(t.getTaskReferenceName()));
        // each run's anchor is in the definition, or in a run inserted before it
        Set<String> placed = new HashSet<>(existing);
        for (Run run : runs) {
            if (run.anchor() != null && !placed.contains(run.anchor())) {
                throw new RejectedFragmentException(
                        "fragment",
                        "task " + run.anchor() + " is not in the execution's definition");
            }
            run.entries()
                    .forEach(
                            e ->
                                    e.collectTasks()
                                            .forEach(t -> placed.add(t.getTaskReferenceName())));
        }
        Set<String> seen = new HashSet<>();
        Map<String, Integer> markersPerLane = new HashMap<>();
        for (WorkflowTask task : fragment) {
            for (WorkflowTask nested : task.collectTasks()) {
                String ref = nested.getTaskReferenceName();
                String type = nested.getType();
                if (codeType != null && codeType.equals(nested.getName())) {
                    // its queue messages could not be told from the code's own
                    throw new RejectedFragmentException(
                            "reference", "a task cannot be named after the code type " + codeType);
                }
                if (ref == null || ref.isBlank()) {
                    throw new RejectedFragmentException(
                            "reference", "every fragment task needs a taskReferenceName");
                }
                if (existing.contains(ref) || !seen.add(ref)) {
                    throw new RejectedFragmentException(
                            "reference", "taskReferenceName " + ref + " is already used");
                }
                if (type == null || !taskMappers.containsKey(type)) {
                    throw new RejectedFragmentException(
                            "type", "task " + ref + " has unsupported type " + type);
                }
                if (TaskType.TERMINATE.name().equals(type) && !ref.equals(thrownRef)) {
                    throw new RejectedFragmentException(
                            "type",
                            "a native workflow ends through its code, not a TERMINATE task");
                }
                if (laneOf(nested) == null) {
                    throw new RejectedFragmentException(
                            "lane", "task " + ref + " does not name its lane in metadata");
                }
                if (TaskType.SWITCH.name().equals(type)
                        && !NativeCodeEvaluator.NAME.equals(nested.getEvaluatorType())) {
                    throw new RejectedFragmentException(
                            "type",
                            "switch "
                                    + ref
                                    + " is decided by the code: its evaluatorType must be "
                                    + NativeCodeEvaluator.NAME);
                }
                if (nested.getMetadata().get(METADATA_CONTINUATION) != null) {
                    Id id = idOf(nested);
                    if (id == null) {
                        throw new RejectedFragmentException(
                                "continuation",
                                "task " + ref + " has a malformed continuation marker");
                    }
                    markersPerLane.merge(id.lane(), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> e : markersPerLane.entrySet()) {
            if (e.getValue() > 1) {
                throw new RejectedFragmentException(
                        "continuation",
                        "lane " + e.getKey() + " pauses more than once in one fragment");
            }
        }
    }

    void populateTaskDefinitions(String workflowName, List<WorkflowTask> fragment) {
        WorkflowDef holder = new WorkflowDef();
        holder.setName(workflowName);
        holder.setTasks(fragment);
        try {
            // a worker that dies holding a native task: the response timeout reschedules it
            metadataMapperService.populateTaskDefinitions(holder, TaskDef.TimeoutPolicy.RETRY);
        } catch (IllegalArgumentException e) {
            throw new RejectedFragmentException("definition", e.getMessage());
        }
    }

    void checkVariablesSize(WorkflowModel workflow, Map<String, Object> changed) {
        if (changed.isEmpty()) {
            return;
        }
        Map<String, Object> merged = new HashMap<>(workflow.getVariables());
        merged.putAll(changed);
        long maxBytes = properties.getMaxWorkflowVariablesPayloadSizeThreshold().toBytes();
        long size = serializedSize(merged);
        if (size > maxBytes) {
            throw new RejectedFragmentException(
                    "variables",
                    "variables payload of "
                            + size
                            + " bytes is greater than the permissible limit of "
                            + maxBytes
                            + " bytes");
        }
    }

    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JacksonException e) {
            throw new RejectedFragmentException(
                    "fragment", "payload cannot be serialized: " + e.getMessage());
        }
    }

    /** A completion that breaks a rule; the reason names the rule for metrics. */
    static final class RejectedFragmentException extends RuntimeException {
        final String reason;

        RejectedFragmentException(String reason, String message) {
            super(message);
            this.reason = reason;
        }
    }
}
