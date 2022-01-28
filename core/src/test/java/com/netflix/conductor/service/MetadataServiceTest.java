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
package com.netflix.conductor.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.validation.ConstraintViolationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ConductorProperties;
import com.netflix.conductor.core.exception.ApplicationException;
import com.netflix.conductor.dao.EventHandlerDAO;
import com.netflix.conductor.dao.MetadataDAO;

import static com.netflix.conductor.TestUtils.getConstraintViolationMessages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("SpringJavaAutowiredMembersInspection")
@RunWith(SpringRunner.class)
@EnableAutoConfiguration
public class MetadataServiceTest {

    @TestConfiguration
    static class TestMetadataConfiguration {

        @Bean
        public MetadataDAO metadataDAO() {
            return mock(MetadataDAO.class);
        }

        @Bean
        public ConductorProperties properties() {
            ConductorProperties properties = mock(ConductorProperties.class);
            when(properties.isOwnerEmailMandatory()).thenReturn(true);
            return properties;
        }

        @Bean
        public MetadataService metadataService(
                MetadataDAO metadataDAO, ConductorProperties properties) {
            EventHandlerDAO eventHandlerDAO = mock(EventHandlerDAO.class);
            return new MetadataServiceImpl(metadataDAO, eventHandlerDAO, properties);
        }
    }

    @Autowired private MetadataDAO metadataDAO;

    @Autowired private MetadataService metadataService;

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterTaskDefNoName() {
        TaskDef taskDef = new TaskDef();
        try {
            metadataService.registerTaskDef(Collections.singletonList(taskDef));
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef name cannot be null or empty"));
            assertTrue(messages.contains("ownerEmail cannot be empty"));
            throw ex;
        }
        fail("metadataService.registerTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterTaskDefNull() {
        try {
            metadataService.registerTaskDef(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDefList cannot be empty or null"));
            throw ex;
        }
        fail("metadataService.registerTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterTaskDefNoResponseTimeout() {
        try {
            TaskDef taskDef = new TaskDef();
            taskDef.setName("somename");
            taskDef.setOwnerEmail("sample@test.com");
            taskDef.setResponseTimeoutSeconds(0);
            metadataService.registerTaskDef(Collections.singletonList(taskDef));
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(
                    messages.contains(
                            "TaskDef responseTimeoutSeconds: 0 should be minimum 1 second"));
            throw ex;
        }
        fail("metadataService.registerTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskDefNameNull() {
        try {
            TaskDef taskDef = new TaskDef();
            metadataService.updateTaskDef(taskDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef name cannot be null or empty"));
            assertTrue(messages.contains("ownerEmail cannot be empty"));
            throw ex;
        }
        fail("metadataService.updateTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskDefNull() {
        try {
            metadataService.updateTaskDef(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef cannot be null"));
            throw ex;
        }
        fail("metadataService.updateTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ApplicationException.class)
    public void testUpdateTaskDefNotExisting() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setOwnerEmail("sample@test.com");
        when(metadataDAO.getTaskDef(any())).thenReturn(null);
        metadataService.updateTaskDef(taskDef);
    }

    @Test(expected = ApplicationException.class)
    public void testUpdateTaskDefDaoException() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        taskDef.setOwnerEmail("sample@test.com");
        when(metadataDAO.getTaskDef(any())).thenReturn(null);
        metadataService.updateTaskDef(taskDef);
    }

    @Test
    public void testRegisterTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("somename");
        taskDef.setOwnerEmail("sample@test.com");
        taskDef.setResponseTimeoutSeconds(60 * 60);
        metadataService.registerTaskDef(Collections.singletonList(taskDef));
        verify(metadataDAO, times(1)).createTaskDef(any(TaskDef.class));
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateWorkflowDefNull() {
        try {
            List<WorkflowDef> workflowDefList = null;
            metadataService.updateWorkflowDef(workflowDefList);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef list name cannot be null or empty"));
            throw ex;
        }
        fail("metadataService.updateWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateWorkflowDefEmptyList() {
        try {
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            metadataService.updateWorkflowDef(workflowDefList);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDefList is empty"));
            throw ex;
        }
        fail("metadataService.updateWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateWorkflowDefWithNullWorkflowDef() {
        try {
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            workflowDefList.add(null);
            metadataService.updateWorkflowDef(workflowDefList);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef cannot be null"));
            throw ex;
        }
        fail("metadataService.updateWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateWorkflowDefWithEmptyWorkflowDefName() {
        try {
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            WorkflowDef workflowDef = new WorkflowDef();
            workflowDef.setName(null);
            workflowDef.setOwnerEmail(null);
            workflowDefList.add(workflowDef);
            metadataService.updateWorkflowDef(workflowDefList);
        } catch (ConstraintViolationException ex) {
            assertEquals(3, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            assertTrue(messages.contains("ownerEmail cannot be empty"));
            throw ex;
        }
        fail("metadataService.updateWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test
    public void testUpdateWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        workflowDef.setOwnerEmail("sample@test.com");
        List<WorkflowTask> tasks = new ArrayList<>();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("hello");
        workflowTask.setName("hello");
        tasks.add(workflowTask);
        workflowDef.setTasks(tasks);
        when(metadataDAO.getTaskDef(any())).thenReturn(new TaskDef());
        metadataService.updateWorkflowDef(Collections.singletonList(workflowDef));
        verify(metadataDAO, times(1)).updateWorkflowDef(workflowDef);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterWorkflowDefNoName() {
        try {
            WorkflowDef workflowDef = new WorkflowDef();
            metadataService.registerWorkflowDef(workflowDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(3, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            assertTrue(messages.contains("ownerEmail cannot be empty"));
            throw ex;
        }
        fail("metadataService.registerWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterWorkflowDefInvalidName() {
        try {
            WorkflowDef workflowDef = new WorkflowDef();
            workflowDef.setName("invalid:name");
            workflowDef.setOwnerEmail("inavlid-email");
            metadataService.registerWorkflowDef(workflowDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(3, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            assertTrue(
                    messages.contains(
                            "Workflow name cannot contain the following set of characters: ':'"));
            assertTrue(messages.contains("ownerEmail should be valid email address"));
            throw ex;
        }
        fail("metadataService.registerWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test
    public void testRegisterWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        workflowDef.setSchemaVersion(2);
        workflowDef.setOwnerEmail("sample@test.com");
        List<WorkflowTask> tasks = new ArrayList<>();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("hello");
        workflowTask.setName("hello");
        tasks.add(workflowTask);
        workflowDef.setTasks(tasks);
        when(metadataDAO.getTaskDef(any())).thenReturn(new TaskDef());
        metadataService.registerWorkflowDef(workflowDef);
        verify(metadataDAO, times(1)).createWorkflowDef(workflowDef);
        assertEquals(2, workflowDef.getSchemaVersion());
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUnregisterWorkflowDefNoName() {
        try {
            metadataService.unregisterWorkflowDef("", null);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow name cannot be null or empty"));
            assertTrue(messages.contains("Version cannot be null"));
            throw ex;
        }
        fail("metadataService.unregisterWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test
    public void testUnregisterWorkflowDef() {
        metadataService.unregisterWorkflowDef("somename", 111);
        verify(metadataDAO, times(1)).removeWorkflowDef("somename", 111);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testValidateEventNull() {
        try {
            metadataService.addEventHandler(null);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("EventHandler cannot be null"));
            throw ex;
        }
        fail("metadataService.addEventHandler did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testValidateEventNoEvent() {
        try {
            EventHandler eventHandler = new EventHandler();
            metadataService.addEventHandler(eventHandler);
        } catch (ConstraintViolationException ex) {
            assertEquals(3, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Missing event handler name"));
            assertTrue(messages.contains("Missing event location"));
            assertTrue(
                    messages.contains("No actions specified. Please specify at-least one action"));
            throw ex;
        }
        fail("metadataService.addEventHandler did not throw ConstraintViolationException !");
    }
}
