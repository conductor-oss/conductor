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
package com.netflix.conductor.common.metadata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestAuditablePojoMethods {

    // Concrete implementation of the abstract class for testing
    private static class TestAuditable extends Auditable {
        // No additional implementation needed
    }

    private TestAuditable auditable;

    @BeforeEach
    void setUp() {
        auditable = new TestAuditable();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(auditable);
        assertEquals(0L, auditable.getCreateTime());
        assertEquals(0L, auditable.getUpdateTime());
        assertNull(auditable.getOwnerApp());
        assertNull(auditable.getCreatedBy());
        assertNull(auditable.getUpdatedBy());
    }

    @Test
    void testSetAndGetOwnerApp() {
        String ownerApp = "testApp";
        auditable.setOwnerApp(ownerApp);
        assertEquals(ownerApp, auditable.getOwnerApp());
    }

    @Test
    void testSetAndGetCreateTime() {
        Long createTime = 1234567890L;
        auditable.setCreateTime(createTime);
        assertEquals(createTime, auditable.getCreateTime());
    }

    @Test
    void testGetCreateTimeWithNull() {
        auditable.setCreateTime(null);
        assertEquals(0L, auditable.getCreateTime());
    }

    @Test
    void testSetAndGetUpdateTime() {
        Long updateTime = 9876543210L;
        auditable.setUpdateTime(updateTime);
        assertEquals(updateTime, auditable.getUpdateTime());
    }

    @Test
    void testGetUpdateTimeWithNull() {
        auditable.setUpdateTime(null);
        assertEquals(0L, auditable.getUpdateTime());
    }

    @Test
    void testSetAndGetCreatedBy() {
        String createdBy = "user1";
        auditable.setCreatedBy(createdBy);
        assertEquals(createdBy, auditable.getCreatedBy());
    }

    @Test
    void testSetAndGetUpdatedBy() {
        String updatedBy = "user2";
        auditable.setUpdatedBy(updatedBy);
        assertEquals(updatedBy, auditable.getUpdatedBy());
    }

    @Test
    void testCompleteAuditableLifecycle() {
        // Setup
        String ownerApp = "testCompleteApp";
        Long createTime = 1000000L;
        Long updateTime = 2000000L;
        String createdBy = "creator";
        String updatedBy = "updater";

        // Set all properties
        auditable.setOwnerApp(ownerApp);
        auditable.setCreateTime(createTime);
        auditable.setUpdateTime(updateTime);
        auditable.setCreatedBy(createdBy);
        auditable.setUpdatedBy(updatedBy);

        // Verify all properties
        assertEquals(ownerApp, auditable.getOwnerApp());
        assertEquals(createTime, auditable.getCreateTime());
        assertEquals(updateTime, auditable.getUpdateTime());
        assertEquals(createdBy, auditable.getCreatedBy());
        assertEquals(updatedBy, auditable.getUpdatedBy());
    }
}
