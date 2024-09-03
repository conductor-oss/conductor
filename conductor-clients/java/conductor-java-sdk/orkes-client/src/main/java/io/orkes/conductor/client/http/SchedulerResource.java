/*
 * Copyright 2022 Orkes, Inc.
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
package io.orkes.conductor.client.http;

import java.util.List;
import java.util.Map;

import com.netflix.conductor.client.http.ConductorClient;
import com.netflix.conductor.client.http.ConductorClientRequest;
import com.netflix.conductor.client.http.ConductorClientRequest.Method;
import com.netflix.conductor.client.http.ConductorClientResponse;

import io.orkes.conductor.client.model.SaveScheduleRequest;
import io.orkes.conductor.client.model.SearchResultWorkflowScheduleExecution;
import io.orkes.conductor.client.model.TagObject;
import io.orkes.conductor.client.model.WorkflowSchedule;

import com.fasterxml.jackson.core.type.TypeReference;


public class SchedulerResource {

    private final ConductorClient client;

    public SchedulerResource(ConductorClient client) {
        this.client = client;
    }

    public void deleteSchedule(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/scheduler/schedules/{name}")
                .addPathParam("name", name)
                .build();

        client.execute(request);
    }

    public List<WorkflowSchedule> getAllSchedules(String workflowName) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/schedules")
                .addQueryParam("workflowName", workflowName)
                .build();

        ConductorClientResponse<List<WorkflowSchedule>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public List<Long> getNextFewSchedules(String cronExpression,
                                          Long scheduleStartTime,
                                          Long scheduleEndTime,
                                          Integer limit) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/nextFewSchedules")
                .addQueryParam("cronExpression", cronExpression)
                .addQueryParam("scheduleStartTime", scheduleStartTime)
                .addQueryParam("scheduleEndTime", scheduleEndTime)
                .addQueryParam("limit", limit)
                .build();

        ConductorClientResponse<List<Long>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public WorkflowSchedule getSchedule(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/schedules/{name}")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<WorkflowSchedule> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public Map<String, Object> pauseAllSchedules() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/admin/pause")
                .build();

        ConductorClientResponse<Map<String, Object>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void pauseSchedule(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/schedules/{name}/pause")
                .addPathParam("name", name)
                .build();

        client.execute(request);
    }

    public Map<String, Object> requeueAllExecutionRecords() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/admin/requeue")
                .build();

        ConductorClientResponse<Map<String, Object>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public Map<String, Object> resumeAllSchedules() {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/admin/resume")
                .build();

        ConductorClientResponse<Map<String, Object>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void resumeSchedule(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/schedules/{name}/resume")
                .addPathParam("name", name)
                .build();

        client.execute(request);
    }

    public void saveSchedule(SaveScheduleRequest saveScheduleRequest) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.POST)
                .path("/scheduler/schedules")
                .body(saveScheduleRequest)
                .build();

        client.execute(request);
    }

    public SearchResultWorkflowScheduleExecution search(
            Integer start, Integer size, String sort, String freeText, String query) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/search/executions")
                .addQueryParam("start", start)
                .addQueryParam("size", size)
                .addQueryParam("sort", sort)
                .addQueryParam("freeText", freeText)
                .addQueryParam("query", query)
                .build();

        ConductorClientResponse<SearchResultWorkflowScheduleExecution> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }

    public void deleteTagForSchedule(String name, List<TagObject> body) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.DELETE)
                .path("/scheduler/schedules/{name}/tags")
                .addPathParam("name", name)
                .body(body)
                .build();

        client.execute(request);
    }

    public void putTagForSchedule(String name, List<TagObject> body) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.PUT)
                .path("/scheduler/schedules/{name}/tags")
                .addPathParam("name", name)
                .body(body)
                .build();

        client.execute(request);
    }

    public List<TagObject> getTagsForSchedule(String name) {
        ConductorClientRequest request = ConductorClientRequest.builder()
                .method(Method.GET)
                .path("/scheduler/schedules/{name}/tags")
                .addPathParam("name", name)
                .build();

        ConductorClientResponse<List<TagObject>> resp = client.execute(request, new TypeReference<>() {
        });

        return resp.getData();
    }
}
