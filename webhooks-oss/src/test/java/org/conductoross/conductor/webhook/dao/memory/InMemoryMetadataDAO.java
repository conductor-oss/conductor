/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.conductoross.conductor.webhook.dao.memory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.dao.MetadataDAO;

/**
 * Minimal in-memory {@link MetadataDAO} for tests. Only WorkflowDef storage is implemented;
 * TaskDef methods throw {@link UnsupportedOperationException}.
 */
public class InMemoryMetadataDAO implements MetadataDAO {

    private final ConcurrentHashMap<String, WorkflowDef> defs = new ConcurrentHashMap<>();

    public void putWorkflowDef(WorkflowDef def) {
        defs.put(def.getName() + ":" + def.getVersion(), def);
    }

    @Override
    public Optional<WorkflowDef> getWorkflowDef(String name, int version) {
        return Optional.ofNullable(defs.get(name + ":" + version));
    }

    @Override
    public Optional<WorkflowDef> getLatestWorkflowDef(String name) {
        return defs.values().stream()
                .filter(d -> d.getName().equals(name))
                .max(Comparator.comparingInt(WorkflowDef::getVersion));
    }

    @Override
    public void createWorkflowDef(WorkflowDef def) {
        putWorkflowDef(def);
    }

    @Override
    public void updateWorkflowDef(WorkflowDef def) {
        putWorkflowDef(def);
    }

    @Override
    public void removeWorkflowDef(String name, Integer version) {
        defs.remove(name + ":" + version);
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefs() {
        return new ArrayList<>(defs.values());
    }

    @Override
    public List<WorkflowDef> getAllWorkflowDefsLatestVersions() {
        Map<String, WorkflowDef> latest = new HashMap<>();
        defs.values()
                .forEach(
                        d ->
                                latest.merge(
                                        d.getName(),
                                        d,
                                        (a, b) -> a.getVersion() >= b.getVersion() ? a : b));
        return new ArrayList<>(latest.values());
    }

    @Override
    public TaskDef createTaskDef(TaskDef taskDef) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskDef updateTaskDef(TaskDef taskDef) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TaskDef getTaskDef(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<TaskDef> getAllTaskDefs() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeTaskDef(String name) {
        throw new UnsupportedOperationException();
    }
}
