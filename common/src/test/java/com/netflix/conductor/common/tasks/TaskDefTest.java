/*
 * Copyright 2020 Conductor Authors.
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
package com.netflix.conductor.common.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.netflix.conductor.common.metadata.tasks.TaskDef;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaskDefTest {

    private Validator validator;

    @Before
    public void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        this.validator = factory.getValidator();
    }

    // -------------------------------------------------------------------------
    // Constructor / defaults
    // -------------------------------------------------------------------------

    @Test
    public void test() {
        String name = "test1";
        String description = "desc";
        int retryCount = 10;
        int timeout = 100;
        TaskDef def = new TaskDef(name, description, retryCount, timeout);
        assertEquals(36_00, def.getResponseTimeoutSeconds());
        assertEquals(name, def.getName());
        assertEquals(description, def.getDescription());
        assertEquals(retryCount, def.getRetryCount());
        assertEquals(timeout, def.getTimeoutSeconds());
    }

    @Test
    public void testDefaultValues() {
        TaskDef def = new TaskDef("task");
        assertEquals(3, def.getRetryCount());
        assertEquals(60, def.getRetryDelaySeconds());
        assertEquals(TaskDef.ONE_HOUR, def.getResponseTimeoutSeconds());
        assertEquals(TaskDef.RetryLogic.FIXED, def.getRetryLogic());
        assertEquals(TaskDef.TimeoutPolicy.TIME_OUT_WF, def.getTimeoutPolicy());
        assertEquals(Integer.valueOf(1), def.getBackoffScaleFactor());
        assertTrue(def.isTaskStatusListenerEnabled());
        assertNotNull(def.getInputKeys());
        assertTrue(def.getInputKeys().isEmpty());
        assertNotNull(def.getOutputKeys());
        assertTrue(def.getOutputKeys().isEmpty());
        assertNotNull(def.getInputTemplate());
        assertTrue(def.getInputTemplate().isEmpty());
        assertNotNull(def.getMetadata());
        assertTrue(def.getMetadata().isEmpty());
    }

    // -------------------------------------------------------------------------
    // version defaulting to 1
    // -------------------------------------------------------------------------

    @Test
    public void testVersionDefaultsToOneWhenNotSet() {
        TaskDef def = new TaskDef("task");
        assertEquals(Integer.valueOf(1), def.getVersion());
    }

    @Test
    public void testVersionDefaultsToOneWhenExplicitlySetToNull() {
        TaskDef def = new TaskDef("task");
        def.setVersion(null);
        assertEquals(Integer.valueOf(1), def.getVersion());
    }

    @Test
    public void testVersionExplicitValue() {
        TaskDef def = new TaskDef("task");
        def.setVersion(3);
        assertEquals(Integer.valueOf(3), def.getVersion());
    }

    // -------------------------------------------------------------------------
    // equals / hashCode — PK is (name, version)
    // -------------------------------------------------------------------------

    @Test
    public void testEqualsReflexive() {
        TaskDef def = new TaskDef("task");
        assertEquals(def, def);
    }

    @Test
    public void testEqualsSymmetric() {
        TaskDef a = new TaskDef("task");
        TaskDef b = new TaskDef("task");
        assertEquals(a, b);
        assertEquals(b, a);
    }

    @Test
    public void testEqualsNull() {
        TaskDef def = new TaskDef("task");
        assertNotEquals(null, def);
    }

    @Test
    public void testEqualsDifferentClass() {
        TaskDef def = new TaskDef("task");
        assertFalse(def.equals("task"));
    }

    @Test
    public void testEqualsSameNameAndVersion() {
        TaskDef a = new TaskDef("task");
        a.setVersion(2);
        TaskDef b = new TaskDef("task");
        b.setVersion(2);
        // change unrelated fields — must still be equal
        b.setRetryCount(10);
        b.setOwnerEmail("owner@example.com");
        assertEquals(a, b);
    }

    @Test
    public void testEqualsDifferentName() {
        TaskDef a = new TaskDef("task-a");
        TaskDef b = new TaskDef("task-b");
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsDifferentVersion() {
        TaskDef a = new TaskDef("task");
        a.setVersion(1);
        TaskDef b = new TaskDef("task");
        b.setVersion(2);
        assertNotEquals(a, b);
    }

    @Test
    public void testEqualsNullVersionTreatedAsOne() {
        // null version and explicit version=1 must be the same PK
        TaskDef nullVersion = new TaskDef("task");
        TaskDef explicitOne = new TaskDef("task");
        explicitOne.setVersion(1);
        assertEquals(nullVersion, explicitOne);
        assertEquals(nullVersion.hashCode(), explicitOne.hashCode());
    }

    @Test
    public void testEqualsBothVersionsNull() {
        TaskDef a = new TaskDef("task");
        TaskDef b = new TaskDef("task");
        assertEquals(a, b);
    }

    @Test
    public void testHashCodeConsistency() {
        TaskDef def = new TaskDef("task");
        def.setVersion(5);
        int first = def.hashCode();
        int second = def.hashCode();
        assertEquals(first, second);
    }

    @Test
    public void testHashCodeEqualObjects() {
        TaskDef a = new TaskDef("task");
        a.setVersion(2);
        TaskDef b = new TaskDef("task");
        b.setVersion(2);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testHashSetDeduplication() {
        TaskDef a = new TaskDef("task");
        TaskDef b = new TaskDef("task"); // same PK, different object
        b.setRetryCount(99);

        Set<TaskDef> set = new HashSet<>();
        set.add(a);
        set.add(b);
        assertEquals(1, set.size());
    }

    @Test
    public void testHashSetDistinctVersions() {
        TaskDef v1 = new TaskDef("task");
        v1.setVersion(1);
        TaskDef v2 = new TaskDef("task");
        v2.setVersion(2);

        Set<TaskDef> set = new HashSet<>();
        set.add(v1);
        set.add(v2);
        assertEquals(2, set.size());
    }

    // -------------------------------------------------------------------------
    // toString
    // -------------------------------------------------------------------------

    @Test
    public void testToStringReturnsName() {
        TaskDef def = new TaskDef("my-task");
        assertEquals("my-task", def.toString());
    }

    // -------------------------------------------------------------------------
    // Null-coalescing getters — must never throw NPE
    // -------------------------------------------------------------------------

    @Test
    public void testGetRateLimitPerFrequencyDefaultsToZeroWhenNull() {
        TaskDef def = new TaskDef("task");
        // field starts null
        assertEquals(Integer.valueOf(0), def.getRateLimitPerFrequency());
    }

    @Test
    public void testGetRateLimitPerFrequencyReturnsValueWhenSet() {
        TaskDef def = new TaskDef("task");
        def.setRateLimitPerFrequency(10);
        assertEquals(Integer.valueOf(10), def.getRateLimitPerFrequency());
    }

    @Test
    public void testGetRateLimitFrequencyInSecondsDefaultsToOneWhenNull() {
        TaskDef def = new TaskDef("task");
        assertEquals(Integer.valueOf(1), def.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testGetRateLimitFrequencyInSecondsReturnsValueWhenSet() {
        TaskDef def = new TaskDef("task");
        def.setRateLimitFrequencyInSeconds(30);
        assertEquals(Integer.valueOf(30), def.getRateLimitFrequencyInSeconds());
    }

    @Test
    public void testConcurrencyLimitDefaultsToZeroWhenNull() {
        TaskDef def = new TaskDef("task");
        // getConcurrentExecLimit() is null; concurrencyLimit() should return 0
        assertEquals(0, def.concurrencyLimit());
    }

    @Test
    public void testConcurrencyLimitReturnsValueWhenSet() {
        TaskDef def = new TaskDef("task");
        def.setConcurrentExecLimit(5);
        assertEquals(5, def.concurrencyLimit());
        assertEquals(Integer.valueOf(5), def.getConcurrentExecLimit());
    }

    // -------------------------------------------------------------------------
    // Validation — existing coverage
    // -------------------------------------------------------------------------

    @Test
    public void testTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("task1");
        taskDef.setRetryCount(-1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1001);

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(3, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.contains(
                        "TaskDef: task1 responseTimeoutSeconds: 1001 must be less than timeoutSeconds: 1000"));
        assertTrue(validationErrors.contains("TaskDef retryCount: 0 must be >= 0"));
        assertTrue(validationErrors.contains("ownerEmail cannot be empty"));
    }

    @Test
    public void testTaskDefTotalTimeOutSeconds() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setTotalTimeoutSeconds(900);
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setOwnerEmail("blah@gmail.com");

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.toString(),
                validationErrors.contains(
                        "TaskDef: test-task timeoutSeconds: 1000 must be less than or equal to totalTimeoutSeconds: 900"));
    }

    @Test
    public void testTaskDefInvalidEmail() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1);

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(1, result.size());

        List<String> validationErrors = new ArrayList<>();
        result.forEach(e -> validationErrors.add(e.getMessage()));

        assertTrue(
                validationErrors.toString(),
                validationErrors.contains("ownerEmail cannot be empty"));
    }

    @Test
    public void testTaskDefValidEmail() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test-task");
        taskDef.setRetryCount(1);
        taskDef.setTimeoutSeconds(1000);
        taskDef.setResponseTimeoutSeconds(1);
        taskDef.setOwnerEmail("owner@test.com");

        Set<ConstraintViolation<Object>> result = validator.validate(taskDef);
        assertEquals(0, result.size());
    }

    // -------------------------------------------------------------------------
    // Validation — edge cases
    // -------------------------------------------------------------------------

    @Test
    public void testValidationFailsForNullName() {
        TaskDef def = new TaskDef();
        def.setRetryCount(1);
        def.setTimeoutSeconds(100);
        def.setResponseTimeoutSeconds(1);
        def.setOwnerEmail("owner@test.com");

        List<String> messages = violationMessages(def);
        assertTrue(
                messages.stream()
                        .anyMatch(m -> m.contains("TaskDef name cannot be null or empty")));
    }

    @Test
    public void testValidationFailsForEmptyName() {
        TaskDef def = new TaskDef();
        def.setName("");
        def.setRetryCount(1);
        def.setTimeoutSeconds(100);
        def.setResponseTimeoutSeconds(1);
        def.setOwnerEmail("owner@test.com");

        List<String> messages = violationMessages(def);
        assertTrue(
                messages.stream()
                        .anyMatch(m -> m.contains("TaskDef name cannot be null or empty")));
    }

    @Test
    public void testValidationFailsForNegativePollTimeout() {
        TaskDef def = validDef();
        def.setPollTimeoutSeconds(-1);

        List<String> messages = violationMessages(def);
        assertTrue(messages.stream().anyMatch(m -> m.contains("pollTimeoutSeconds")));
    }

    @Test
    public void testValidationFailsForNegativeMaxRetryDelay() {
        TaskDef def = validDef();
        def.setMaxRetryDelaySeconds(-1);

        List<String> messages = violationMessages(def);
        assertTrue(messages.stream().anyMatch(m -> m.contains("maxRetryDelaySeconds")));
    }

    @Test
    public void testValidationFailsForNegativeBackoffJitter() {
        TaskDef def = validDef();
        def.setBackoffJitterMs(-1);

        List<String> messages = violationMessages(def);
        assertTrue(messages.stream().anyMatch(m -> m.contains("backoffJitterMs")));
    }

    @Test
    public void testValidationFailsForZeroBackoffScaleFactor() {
        TaskDef def = validDef();
        def.setBackoffScaleFactor(0);

        List<String> messages = violationMessages(def);
        assertTrue(messages.stream().anyMatch(m -> m.contains("Backoff scale factor")));
    }

    @Test
    public void testValidationPassesForValidDef() {
        TaskDef def = validDef();
        assertEquals(0, validator.validate(def).size());
    }

    @Test
    public void testValidationResponseTimeoutEqualToTimeoutIsValid() {
        TaskDef def = validDef();
        def.setTimeoutSeconds(500);
        def.setResponseTimeoutSeconds(500);

        assertEquals(0, validator.validate(def).size());
    }

    @Test
    public void testValidationTimeoutZeroSkipsResponseTimeoutCheck() {
        // timeoutSeconds = 0 means no timeout; constraint only fires when timeoutSeconds > 0
        TaskDef def = validDef();
        def.setTimeoutSeconds(0);
        def.setResponseTimeoutSeconds(9999);

        assertEquals(0, validator.validate(def).size());
    }

    @Test
    public void testValidationTotalTimeoutZeroSkipsCheck() {
        // totalTimeoutSeconds = 0 means unset; constraint only fires when both are > 0
        TaskDef def = validDef();
        def.setTimeoutSeconds(1000);
        def.setTotalTimeoutSeconds(0);

        assertEquals(0, validator.validate(def).size());
    }

    // -------------------------------------------------------------------------
    // NPE safety on collections / maps
    // -------------------------------------------------------------------------

    @Test
    public void testSetInputKeysDoesNotThrowOnEmptyList() {
        TaskDef def = new TaskDef("task");
        def.setInputKeys(new ArrayList<>());
        assertNotNull(def.getInputKeys());
        assertTrue(def.getInputKeys().isEmpty());
    }

    @Test
    public void testSetInputTemplateDoesNotThrowOnEmptyMap() {
        TaskDef def = new TaskDef("task");
        def.setInputTemplate(new HashMap<>());
        assertNotNull(def.getInputTemplate());
        assertTrue(def.getInputTemplate().isEmpty());
    }

    @Test
    public void testSetMetadataDoesNotThrowOnEmptyMap() {
        TaskDef def = new TaskDef("task");
        def.setMetadata(new HashMap<>());
        assertNotNull(def.getMetadata());
        assertTrue(def.getMetadata().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TaskDef validDef() {
        TaskDef def = new TaskDef();
        def.setName("valid-task");
        def.setRetryCount(1);
        def.setTimeoutSeconds(100);
        def.setResponseTimeoutSeconds(50);
        def.setOwnerEmail("owner@test.com");
        return def;
    }

    private List<String> violationMessages(TaskDef def) {
        List<String> messages = new ArrayList<>();
        validator.validate(def).forEach(v -> messages.add(v.getMessage()));
        return messages;
    }
}
