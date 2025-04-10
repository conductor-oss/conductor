/*
 * Copyright 2025 Conductor Authors.
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
package com.netflix.conductor.common.metadata.workflow;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestSkipTaskRequestPojoMethods {

    @Test
    public void testTaskInputGetterAndSetter() {
        // Arrange
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("key1", "value1");
        taskInput.put("key2", 123);

        // Act
        skipTaskRequest.setTaskInput(taskInput);
        Map<String, Object> result = skipTaskRequest.getTaskInput();

        // Assert
        assertEquals(taskInput, result);
        assertEquals(2, result.size());
        assertEquals("value1", result.get("key1"));
        assertEquals(123, result.get("key2"));
    }

    @Test
    public void testTaskOutputGetterAndSetter() {
        // Arrange
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("resultKey1", "resultValue1");
        taskOutput.put("resultKey2", 456);

        // Act
        skipTaskRequest.setTaskOutput(taskOutput);
        Map<String, Object> result = skipTaskRequest.getTaskOutput();

        // Assert
        assertEquals(taskOutput, result);
        assertEquals(2, result.size());
        assertEquals("resultValue1", result.get("resultKey1"));
        assertEquals(456, result.get("resultKey2"));
    }

    @Test
    public void testTaskInputSetToNull() {
        // Arrange
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        Map<String, Object> taskInput = new HashMap<>();
        taskInput.put("key", "value");
        skipTaskRequest.setTaskInput(taskInput);

        // Act
        skipTaskRequest.setTaskInput(null);

        // Assert
        assertNull(skipTaskRequest.getTaskInput());
    }

    @Test
    public void testTaskOutputSetToNull() {
        // Arrange
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        Map<String, Object> taskOutput = new HashMap<>();
        taskOutput.put("key", "value");
        skipTaskRequest.setTaskOutput(taskOutput);

        // Act
        skipTaskRequest.setTaskOutput(null);

        // Assert
        assertNull(skipTaskRequest.getTaskOutput());
    }

    @Test
    public void testEmptyMapsWork() {
        // Arrange
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();
        Map<String, Object> emptyInput = new HashMap<>();
        Map<String, Object> emptyOutput = new HashMap<>();

        // Act
        skipTaskRequest.setTaskInput(emptyInput);
        skipTaskRequest.setTaskOutput(emptyOutput);

        // Assert
        assertEquals(emptyInput, skipTaskRequest.getTaskInput());
        assertEquals(emptyOutput, skipTaskRequest.getTaskOutput());
        assertEquals(0, skipTaskRequest.getTaskInput().size());
        assertEquals(0, skipTaskRequest.getTaskOutput().size());
    }

    @Test
    public void testDefaultStateIsNull() {
        // Arrange & Act
        SkipTaskRequest skipTaskRequest = new SkipTaskRequest();

        // Assert
        assertNull(skipTaskRequest.getTaskInput());
        assertNull(skipTaskRequest.getTaskOutput());
    }
}