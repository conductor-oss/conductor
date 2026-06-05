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
package io.orkes.conductor.scheduler.service;

import java.util.List;

import org.springframework.validation.annotation.Validated;

import com.netflix.conductor.common.model.BulkResponse;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Validated
public interface SchedulerBulkService {

    int MAX_REQUEST_ITEMS = 1000;

    BulkResponse pauseSchedules(
            @NotEmpty(message = "Schedule names list cannot be null.")
                    @Size(
                            max = MAX_REQUEST_ITEMS,
                            message =
                                    "Cannot process more than {max} schedules. Please use multiple requests.")
                    List<String> scheduleNames);

    BulkResponse resumeSchedules(
            @NotEmpty(message = "Schedule names list cannot be null.")
                    @Size(
                            max = MAX_REQUEST_ITEMS,
                            message =
                                    "Cannot process more than {max} schedules. Please use multiple requests.")
                    List<String> scheduleNames);
}
