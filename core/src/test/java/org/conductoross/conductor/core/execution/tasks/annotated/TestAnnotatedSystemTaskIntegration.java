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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.util.Map;
import java.util.Set;

import org.conductoross.conductor.sdk.workflow.task.InputParam;
import org.conductoross.conductor.sdk.workflow.task.WorkerTask;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.tasks.SystemTaskRegistry;
import com.netflix.conductor.core.execution.tasks.WorkflowSystemTask;
import com.netflix.conductor.core.utils.ParametersUtils;

import static org.junit.Assert.*;

/**
 * Integration test to verify that WorkerTaskAnnotationScanner correctly discovers and
 * registers @WorkerTask annotated methods with the Spring application context.
 */
@RunWith(SpringRunner.class)
@Import({TestAnnotatedSystemTaskIntegration.TestConfig.class, WorkerTaskAnnotationScanner.class})
public class TestAnnotatedSystemTaskIntegration {

    @Autowired
    @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
    private Set<WorkflowSystemTask> asyncSystemTasks;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public SampleAnnotatedTasks sampleAnnotatedTasks() {
            return new SampleAnnotatedTasks();
        }

        @Bean
        @Qualifier(SystemTaskRegistry.ASYNC_SYSTEM_TASKS_QUALIFIER)
        public Set<WorkflowSystemTask> asyncSystemTasks() {
            // Start with empty set - scanner will add to it
            return new java.util.HashSet<>();
        }

        @Bean
        @Qualifier("taskMappersByTaskType")
        public Map<String, TaskMapper> taskMappersByTaskType() {
            // Start with empty map - scanner will populate it
            return new java.util.HashMap<>();
        }

        @Bean
        public ParametersUtils parametersUtils() {
            // Mock ParametersUtils for test
            return new ParametersUtils(null);
        }
    }

    /** Sample bean with @WorkerTask annotated methods for testing */
    static class SampleAnnotatedTasks {

        @WorkerTask("integration_test_task_1")
        public Map<String, Object> task1(@InputParam("input") String input) {
            return Map.of("result", "task1: " + input);
        }

        @WorkerTask(value = "integration_test_task_2", threadCount = 5, pollingInterval = 200)
        public Map<String, Object> task2(@InputParam("value") Integer value) {
            return Map.of("doubled", value * 2);
        }

        // Method without @WorkerTask should be ignored
        public String notATask() {
            return "not registered";
        }
    }

    @Test
    public void testAnnotatedTasksAreDiscovered() {
        assertNotNull(asyncSystemTasks);
        assertFalse("Should have discovered annotated tasks", asyncSystemTasks.isEmpty());

        // Find our test tasks
        AnnotatedWorkflowSystemTask task1 = findTask("integration_test_task_1");
        AnnotatedWorkflowSystemTask task2 = findTask("integration_test_task_2");

        assertNotNull("integration_test_task_1 should be discovered", task1);
        assertNotNull("integration_test_task_2 should be discovered", task2);
    }

    @Test
    public void testAnnotatedTasksAreAsync() {
        AnnotatedWorkflowSystemTask task1 = findTask("integration_test_task_1");
        assertNotNull(task1);
        assertTrue("Annotated tasks should be async", task1.isAsync());
    }

    @Test
    public void testAnnotatedTaskHasCorrectMetadata() {
        AnnotatedWorkflowSystemTask task2 = findTask("integration_test_task_2");
        assertNotNull(task2);

        assertEquals("integration_test_task_2", task2.getTaskType());
        assertEquals("task2", task2.getMethod().getName());
        assertTrue(task2.getBean() instanceof SampleAnnotatedTasks);

        // Verify annotation metadata
        assertEquals(5, task2.getAnnotation().threadCount());
        assertEquals(200, task2.getAnnotation().pollingInterval());
    }

    @Test
    public void testNonAnnotatedMethodsNotRegistered() {
        // Ensure that methods without @WorkerTask are not registered
        long notATaskCount =
                asyncSystemTasks.stream()
                        .filter(task -> task instanceof AnnotatedWorkflowSystemTask)
                        .map(task -> (AnnotatedWorkflowSystemTask) task)
                        .filter(task -> task.getMethod().getName().equals("notATask"))
                        .count();

        assertEquals("Non-annotated methods should not be registered", 0, notATaskCount);
    }

    private AnnotatedWorkflowSystemTask findTask(String taskType) {
        return asyncSystemTasks.stream()
                .filter(task -> task instanceof AnnotatedWorkflowSystemTask)
                .map(task -> (AnnotatedWorkflowSystemTask) task)
                .filter(task -> task.getTaskType().equals(taskType))
                .findFirst()
                .orElse(null);
    }
}
