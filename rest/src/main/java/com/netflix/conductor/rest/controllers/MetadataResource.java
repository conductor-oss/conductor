/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.conductor.rest.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.service.MetadataService;

import io.swagger.v3.oas.annotations.Operation;

import static com.netflix.conductor.rest.config.RequestMappingConstants.METADATA;

@RestController
@RequestMapping(value = METADATA)
public class MetadataResource {

    private final MetadataService metadataService;

    public MetadataResource(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @PostMapping("/workflow")
    @Operation(summary = "Create a new workflow definition")
    public void create(@RequestBody WorkflowDef workflowDef) {
        metadataService.registerWorkflowDef(workflowDef);
    }

    @PutMapping("/workflow")
    @Operation(summary = "Create or update workflow definition")
    public void update(@RequestBody List<WorkflowDef> workflowDefs) {
        metadataService.updateWorkflowDef(workflowDefs);
    }

    @Operation(summary = "Retrieves workflow definition along with blueprint")
    @GetMapping("/workflow/{name}")
    public WorkflowDef get(
            @PathVariable("name") String name,
            @RequestParam(value = "version", required = false) Integer version) {
        return metadataService.getWorkflowDef(name, version);
    }

    @Operation(summary = "Retrieves all workflow definition along with blueprint")
    @GetMapping("/workflow")
    public List<WorkflowDef> getAll() {
        return metadataService.getWorkflowDefs();
    }

    @DeleteMapping("/workflow/{name}/{version}")
    @Operation(
            summary =
                    "Removes workflow definition. It does not remove workflows associated with the definition.")
    public void unregisterWorkflowDef(
            @PathVariable("name") String name, @PathVariable("version") Integer version) {
        metadataService.unregisterWorkflowDef(name, version);
    }

    @PostMapping("/taskdefs")
    @Operation(summary = "Create new task definition(s)")
    public void registerTaskDef(@RequestBody List<TaskDef> taskDefs) {
        metadataService.registerTaskDef(taskDefs);
    }

    @PutMapping("/taskdefs")
    @Operation(summary = "Update an existing task")
    public void registerTaskDef(@RequestBody TaskDef taskDef) {
        metadataService.updateTaskDef(taskDef);
    }

    @GetMapping(value = "/taskdefs")
    @Operation(summary = "Gets all task definition")
    public List<TaskDef> getTaskDefs() {
        return metadataService.getTaskDefs();
    }

    @GetMapping("/taskdefs/{tasktype}")
    @Operation(summary = "Gets the task definition")
    public TaskDef getTaskDef(@PathVariable("tasktype") String taskType) {
        return metadataService.getTaskDef(taskType);
    }

    @DeleteMapping("/taskdefs/{tasktype}")
    @Operation(summary = "Remove a task definition")
    public void unregisterTaskDef(@PathVariable("tasktype") String taskType) {
        metadataService.unregisterTaskDef(taskType);
    }
}
