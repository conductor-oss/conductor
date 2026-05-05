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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.netflix.conductor.common.model.BulkResponse;
import com.netflix.conductor.core.exception.NotFoundException;
import com.netflix.conductor.core.exception.TransientException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchedulerBulkServiceTest {

    @Mock private SchedulerService schedulerService;

    private SchedulerBulkService schedulerBulkService;

    @BeforeEach
    void setUp() {
        schedulerBulkService = new SchedulerBulkServiceImpl(schedulerService);
    }

    @Test
    @DisplayName("pauseSchedules should successfully pause existing schedules")
    void testPauseSchedulesSuccessful() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2");

        // Mock pauseSchedule to do nothing (success)
        doNothing().when(schedulerService).pauseSchedule("schedule1");
        doNothing().when(schedulerService).pauseSchedule("schedule2");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("schedule1"));
        assertTrue(response.getBulkSuccessfulResults().contains("schedule2"));
        assertTrue(response.getBulkErrorResults().isEmpty());

        verify(schedulerService, times(1)).pauseSchedule("schedule1");
        verify(schedulerService, times(1)).pauseSchedule("schedule2");
    }

    @Test
    @DisplayName("pauseSchedules should handle exceptions during pause operation")
    void testPauseSchedulesWithException() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2");

        // schedule1 succeeds, schedule2 throws exception
        doNothing().when(schedulerService).pauseSchedule("schedule1");
        doThrow(new RuntimeException("Database error"))
                .when(schedulerService)
                .pauseSchedule("schedule2");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("schedule1"));
        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("schedule2"));
        assertEquals("Database error", response.getBulkErrorResults().get("schedule2"));
    }

    @Test
    @DisplayName("pauseSchedules should handle empty list")
    void testPauseSchedulesWithEmptyList() {
        // Given
        List<String> scheduleNames = Collections.emptyList();

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(0, response.getBulkSuccessfulResults().size());
        assertEquals(0, response.getBulkErrorResults().size());

        verify(schedulerService, never()).pauseSchedule(anyString());
    }

    @Test
    @DisplayName("pauseSchedules should handle single schedule")
    void testPauseSchedulesWithSingleSchedule() {
        // Given
        List<String> scheduleNames = List.of("single_schedule");
        doNothing().when(schedulerService).pauseSchedule("single_schedule");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("single_schedule"));
        assertTrue(response.getBulkErrorResults().isEmpty());

        verify(schedulerService, times(1)).pauseSchedule("single_schedule");
    }

    @Test
    @DisplayName("pauseSchedules should handle mixed success and failure scenarios")
    void testPauseSchedulesMixedResults() {
        // Given
        List<String> scheduleNames = List.of("success1", "exception", "success2");

        doNothing().when(schedulerService).pauseSchedule("success1");
        doThrow(new TransientException("System error"))
                .when(schedulerService)
                .pauseSchedule("exception");
        doNothing().when(schedulerService).pauseSchedule("success2");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("success1"));
        assertTrue(response.getBulkSuccessfulResults().contains("success2"));

        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("exception"));
        assertEquals("System error", response.getBulkErrorResults().get("exception"));
    }

    @Test
    @DisplayName("pauseSchedules should handle large list within limits")
    void testPauseSchedulesWithLargeValidList() {
        // Given - Create a list of 50 schedules (keep it smaller for cleaner test)
        List<String> scheduleNames = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String scheduleName = "schedule" + i;
            scheduleNames.add(scheduleName);
            doNothing().when(schedulerService).pauseSchedule(scheduleName);
        }

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(50, response.getBulkSuccessfulResults().size());
        assertEquals(0, response.getBulkErrorResults().size());

        // Verify all schedules were processed
        for (int i = 1; i <= 50; i++) {
            String scheduleName = "schedule" + i;
            verify(schedulerService, times(1)).pauseSchedule(scheduleName);
        }
    }

    @Test
    @DisplayName("pauseSchedules should maintain order in results")
    void testPauseSchedulesResultOrder() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2", "schedule3");

        for (String scheduleName : scheduleNames) {
            doNothing().when(schedulerService).pauseSchedule(scheduleName);
        }

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(3, response.getBulkSuccessfulResults().size());
        assertEquals(scheduleNames, response.getBulkSuccessfulResults());
    }

    @Test
    @DisplayName(
            "pauseSchedules should handle non-existent schedule gracefully if no exception thrown")
    void testPauseSchedulesWithNonExistentSchedule() {
        // Given - In current implementation, pauseSchedule doesn't throw exception for non-existent
        // schedules
        List<String> scheduleNames = List.of("existing_schedule", "non_existent_schedule");

        // Both succeed (current behavior - pauseSchedule silently returns for non-existent
        // schedules)
        doNothing().when(schedulerService).pauseSchedule("existing_schedule");
        doNothing().when(schedulerService).pauseSchedule("non_existent_schedule");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then - Both should succeed (current behavior)
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("existing_schedule"));
        assertTrue(response.getBulkSuccessfulResults().contains("non_existent_schedule"));
        assertEquals(0, response.getBulkErrorResults().size());

        verify(schedulerService, times(1)).pauseSchedule("existing_schedule");
        verify(schedulerService, times(1)).pauseSchedule("non_existent_schedule");
    }

    @Test
    @DisplayName("pauseSchedules should handle ApplicationException with NOT_FOUND code")
    void testPauseSchedulesWithNotFoundSchedule() {
        // Given - This tests the future behavior when pauseSchedule throws exception for
        // non-existent schedules
        List<String> scheduleNames = List.of("existing_schedule", "non_existent_schedule");

        doNothing().when(schedulerService).pauseSchedule("existing_schedule");
        doThrow(new NotFoundException("Schedule 'non_existent_schedule' not found"))
                .when(schedulerService)
                .pauseSchedule("non_existent_schedule");

        // When
        BulkResponse response = schedulerBulkService.pauseSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("existing_schedule"));
        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("non_existent_schedule"));
        assertEquals(
                "Schedule 'non_existent_schedule' not found",
                response.getBulkErrorResults().get("non_existent_schedule"));
    }

    // Resume Schedule Tests

    @Test
    @DisplayName("resumeSchedules should successfully resume existing schedules")
    void testResumeSchedulesSuccessful() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2");

        // Mock resumeSchedule to do nothing (success)
        doNothing().when(schedulerService).resumeSchedule("schedule1");
        doNothing().when(schedulerService).resumeSchedule("schedule2");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("schedule1"));
        assertTrue(response.getBulkSuccessfulResults().contains("schedule2"));
        assertTrue(response.getBulkErrorResults().isEmpty());

        verify(schedulerService, times(1)).resumeSchedule("schedule1");
        verify(schedulerService, times(1)).resumeSchedule("schedule2");
    }

    @Test
    @DisplayName("resumeSchedules should handle exceptions during resume operation")
    void testResumeSchedulesWithException() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2");

        // schedule1 succeeds, schedule2 throws exception
        doNothing().when(schedulerService).resumeSchedule("schedule1");
        doThrow(new RuntimeException("Database error"))
                .when(schedulerService)
                .resumeSchedule("schedule2");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("schedule1"));
        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("schedule2"));
        assertEquals("Database error", response.getBulkErrorResults().get("schedule2"));
    }

    @Test
    @DisplayName("resumeSchedules should handle empty list")
    void testResumeSchedulesWithEmptyList() {
        // Given
        List<String> scheduleNames = Collections.emptyList();

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(0, response.getBulkSuccessfulResults().size());
        assertEquals(0, response.getBulkErrorResults().size());

        verify(schedulerService, never()).resumeSchedule(anyString());
    }

    @Test
    @DisplayName("resumeSchedules should handle single schedule")
    void testResumeSchedulesWithSingleSchedule() {
        // Given
        List<String> scheduleNames = List.of("single_schedule");
        doNothing().when(schedulerService).resumeSchedule("single_schedule");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("single_schedule"));
        assertTrue(response.getBulkErrorResults().isEmpty());

        verify(schedulerService, times(1)).resumeSchedule("single_schedule");
    }

    @Test
    @DisplayName("resumeSchedules should handle mixed success and failure scenarios")
    void testResumeSchedulesMixedResults() {
        // Given
        List<String> scheduleNames = List.of("success1", "exception", "success2");

        doNothing().when(schedulerService).resumeSchedule("success1");
        doThrow(new TransientException("System error"))
                .when(schedulerService)
                .resumeSchedule("exception");
        doNothing().when(schedulerService).resumeSchedule("success2");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("success1"));
        assertTrue(response.getBulkSuccessfulResults().contains("success2"));

        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("exception"));
        assertEquals("System error", response.getBulkErrorResults().get("exception"));
    }

    @Test
    @DisplayName("resumeSchedules should handle large list within limits")
    void testResumeSchedulesWithLargeValidList() {
        // Given - Create a list of 50 schedules (keep it smaller for cleaner test)
        List<String> scheduleNames = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            String scheduleName = "schedule" + i;
            scheduleNames.add(scheduleName);
            doNothing().when(schedulerService).resumeSchedule(scheduleName);
        }

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(50, response.getBulkSuccessfulResults().size());
        assertEquals(0, response.getBulkErrorResults().size());

        // Verify all schedules were processed
        for (int i = 1; i <= 50; i++) {
            String scheduleName = "schedule" + i;
            verify(schedulerService, times(1)).resumeSchedule(scheduleName);
        }
    }

    @Test
    @DisplayName("resumeSchedules should maintain order in results")
    void testResumeSchedulesResultOrder() {
        // Given
        List<String> scheduleNames = List.of("schedule1", "schedule2", "schedule3");

        for (String scheduleName : scheduleNames) {
            doNothing().when(schedulerService).resumeSchedule(scheduleName);
        }

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(3, response.getBulkSuccessfulResults().size());
        assertEquals(scheduleNames, response.getBulkSuccessfulResults());
    }

    @Test
    @DisplayName(
            "resumeSchedules should handle non-existent schedule gracefully if no exception thrown")
    void testResumeSchedulesWithNonExistentSchedule() {
        // Given - In current implementation, resumeSchedule doesn't throw exception for
        // non-existent schedules
        List<String> scheduleNames = List.of("existing_schedule", "non_existent_schedule");

        // Both succeed (current behavior - resumeSchedule silently returns for non-existent
        // schedules)
        doNothing().when(schedulerService).resumeSchedule("existing_schedule");
        doNothing().when(schedulerService).resumeSchedule("non_existent_schedule");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then - Both should succeed (current behavior)
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("existing_schedule"));
        assertTrue(response.getBulkSuccessfulResults().contains("non_existent_schedule"));
        assertEquals(0, response.getBulkErrorResults().size());

        verify(schedulerService, times(1)).resumeSchedule("existing_schedule");
        verify(schedulerService, times(1)).resumeSchedule("non_existent_schedule");
    }

    @Test
    @DisplayName("resumeSchedules should handle ApplicationException with NOT_FOUND code")
    void testResumeSchedulesWithNotFoundSchedule() {
        // Given - This tests the future behavior when resumeSchedule throws exception for
        // non-existent schedules
        List<String> scheduleNames = List.of("existing_schedule", "non_existent_schedule");

        doNothing().when(schedulerService).resumeSchedule("existing_schedule");
        doThrow(new NotFoundException("Schedule 'non_existent_schedule' not found"))
                .when(schedulerService)
                .resumeSchedule("non_existent_schedule");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then
        assertEquals(1, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("existing_schedule"));
        assertEquals(1, response.getBulkErrorResults().size());
        assertTrue(response.getBulkErrorResults().containsKey("non_existent_schedule"));
        assertEquals(
                "Schedule 'non_existent_schedule' not found",
                response.getBulkErrorResults().get("non_existent_schedule"));
    }

    @Test
    @DisplayName("resumeSchedules should handle already running schedule gracefully")
    void testResumeSchedulesWithAlreadyRunningSchedule() {
        // Given - Testing scenario where a schedule is already running/resumed
        List<String> scheduleNames = List.of("paused_schedule", "already_running_schedule");

        doNothing().when(schedulerService).resumeSchedule("paused_schedule");
        // resumeSchedule silently returns for already running schedules (based on current
        // implementation)
        doNothing().when(schedulerService).resumeSchedule("already_running_schedule");

        // When
        BulkResponse response = schedulerBulkService.resumeSchedules(scheduleNames);

        // Then - Both should succeed
        assertEquals(2, response.getBulkSuccessfulResults().size());
        assertTrue(response.getBulkSuccessfulResults().contains("paused_schedule"));
        assertTrue(response.getBulkSuccessfulResults().contains("already_running_schedule"));
        assertEquals(0, response.getBulkErrorResults().size());

        verify(schedulerService, times(1)).resumeSchedule("paused_schedule");
        verify(schedulerService, times(1)).resumeSchedule("already_running_schedule");
    }
}
