package com.netflix.conductor.service;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.config.ValidationModule;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.MetadataDAO;
import com.netflix.conductor.interceptors.ServiceInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static com.netflix.conductor.utility.TestUtils.getConstraintViolationMessages;
import static org.mockito.Mockito.when;

public class MetadataServiceTest{

    private MetadataServiceImpl metadataService;

    private MetadataDAO metadataDAO;

    private EventQueues eventQueues;

    @Before
    public void before() {
        metadataDAO = Mockito.mock(MetadataDAO.class);
        eventQueues = Mockito.mock(EventQueues.class);

        Injector injector =
                Guice.createInjector(
                        new AbstractModule() {
                            @Override
                            protected void configure() {

                                bind(MetadataDAO.class).toInstance(metadataDAO);
                                bind(EventQueues.class).toInstance(eventQueues);

                                install(new ValidationModule());
                                bindInterceptor(
                                        Matchers.any(), Matchers.annotatedWith(Service.class), new ServiceInterceptor(getProvider(Validator.class)));
                            }
                        });
        metadataService = injector.getInstance(MetadataServiceImpl.class);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterTaskDefNoName() {
        TaskDef taskDef = new TaskDef();//name is null
        try{
            metadataService.registerTaskDef(Arrays.asList(taskDef));
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef name cannot be null or empty"));
            throw ex;
        }
        fail("metadataService.registerTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterTaskDefNull() {
        try{
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
        try{
            TaskDef taskDef = new TaskDef();
            taskDef.setName("somename");
            taskDef.setResponseTimeoutSeconds(0);//wrong
            metadataService.registerTaskDef(Arrays.asList(taskDef));
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef responseTimeoutSeconds: 0 should be minimum 1 second"));
            throw ex;
        }
        fail("metadataService.registerTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskDefNameNull() {
        try{
            TaskDef taskDef = new TaskDef();
            metadataService.updateTaskDef(taskDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskDef name cannot be null or empty"));
            throw ex;
        }
        fail("metadataService.updateTaskDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskDefNull() {
        try{
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
        when(metadataDAO.getTaskDef(any())).thenReturn(null);
        metadataService.updateTaskDef(taskDef);
    }

    @Test(expected = ApplicationException.class)
    public void testUpdateTaskDefDaoException() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("test");
        when(metadataDAO.getTaskDef(any())).thenReturn(null);
        metadataService.updateTaskDef(taskDef);
    }

    @Test
    public void testRegisterTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("somename");
        taskDef.setResponseTimeoutSeconds(60 * 60);//wrong
        metadataService.registerTaskDef(Arrays.asList(taskDef));
        verify(metadataDAO, times(1)).createTaskDef(any(TaskDef.class));
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateWorkflowDefNull() {
        try{
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
        try{
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
        try{
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
        try{
            List<WorkflowDef> workflowDefList = new ArrayList<>();
            WorkflowDef workflowDef = new WorkflowDef();
            workflowDef.setName(null);
            workflowDefList.add(workflowDef);
            metadataService.updateWorkflowDef(workflowDefList);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            throw ex;
        }
        fail("metadataService.updateWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test
    public void testUpdateWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        List<WorkflowTask> tasks = new ArrayList<>();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("hello");
        workflowTask.setName("hello");
        tasks.add(workflowTask);
        workflowDef.setTasks(tasks);
        when(metadataDAO.getTaskDef(any())).thenReturn(new TaskDef());
        metadataService.updateWorkflowDef(Arrays.asList(workflowDef));
        verify(metadataDAO, times(1)).update(workflowDef);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterWorkflowDefNoName() {
        try{
            WorkflowDef workflowDef = new WorkflowDef();//name is null
            metadataService.registerWorkflowDef(workflowDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowDef name cannot be null or empty"));
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            throw ex;
        }
        fail("metadataService.registerWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRegisterWorkflowDefInvalidName() {
        try{
            WorkflowDef workflowDef = new WorkflowDef();
            workflowDef.setName("invalid:name");//not allowed
            metadataService.registerWorkflowDef(workflowDef);
        } catch (ConstraintViolationException ex) {
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowTask list cannot be empty"));
            assertTrue(messages.contains("Workflow name cannot contain the following set of characters: ':'"));
            throw ex;
        }
        fail("metadataService.registerWorkflowDef did not throw ConstraintViolationException !");
    }

    @Test
    public void testRegisterWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        workflowDef.setSchemaVersion(2);
        List<WorkflowTask> tasks = new ArrayList<>();
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setTaskReferenceName("hello");
        workflowTask.setName("hello");
        tasks.add(workflowTask);
        workflowDef.setTasks(tasks);
        when(metadataDAO.getTaskDef(any())).thenReturn(new TaskDef());
        metadataService.registerWorkflowDef(workflowDef);
        verify(metadataDAO, times(1)).create(workflowDef);
        assertEquals(2, workflowDef.getSchemaVersion());
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUnregisterWorkflowDefNoName() {
        try{
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
        try{
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
        try{
            EventHandler eventHandler = new EventHandler();
            metadataService.addEventHandler(eventHandler);
        } catch (ConstraintViolationException ex) {
            assertEquals(3, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Missing event handler name"));
            assertTrue(messages.contains("Missing event location"));
            assertTrue(messages.contains("No actions specified. Please specify at-least one action"));
            throw ex;
        }
        fail("metadataService.addEventHandler did not throw ConstraintViolationException !");
    }

}
