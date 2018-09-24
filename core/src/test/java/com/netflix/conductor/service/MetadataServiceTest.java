package com.netflix.conductor.service;

import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.core.events.EventQueues;
import com.netflix.conductor.core.execution.ApplicationException;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class MetadataServiceTest {

    private MetadataService metadataService;
    private MetadataDAO metadataDAO;
    private EventQueues eventQueues;

    @Before
    public void before() {
        metadataDAO = Mockito.mock(MetadataDAO.class);
        eventQueues = Mockito.mock(EventQueues.class);
        metadataService = new MetadataService(metadataDAO, eventQueues);
    }

    @Test(expected = ApplicationException.class)
    public void testRegisterTaskDefNoName() {
        TaskDef taskDef = new TaskDef();//name is null
        metadataService.registerTaskDef(Arrays.asList(taskDef));
    }

    @Test(expected = ApplicationException.class)
    public void testRegisterTaskDefNoResponseTimeout() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("somename");
        taskDef.setResponseTimeoutSeconds(0);//wrong
        metadataService.registerTaskDef(Arrays.asList(taskDef));
    }

    @Test
    public void testRegisterTaskDef() {
        TaskDef taskDef = new TaskDef();
        taskDef.setName("somename");
        taskDef.setResponseTimeoutSeconds(60 * 60);//wrong
        metadataService.registerTaskDef(Arrays.asList(taskDef));
        verify(metadataDAO, times(1)).createTaskDef(any(TaskDef.class));
    }

    @Test(expected = ApplicationException.class)
    public void testUpdateWorkflowDefNoName() {
        WorkflowDef workflowDef = new WorkflowDef();//name is null
        metadataService.updateWorkflowDef(Arrays.asList(workflowDef));
    }

    @Test
    public void testUpdateWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        metadataService.updateWorkflowDef(Arrays.asList(workflowDef));
        verify(metadataDAO, times(1)).update(workflowDef);
    }

    @Test(expected = ApplicationException.class)
    public void testRegisterWorkflowDefNoName() {
        WorkflowDef workflowDef = new WorkflowDef();//name is null
        metadataService.registerWorkflowDef(workflowDef);
    }

    @Test(expected = ApplicationException.class)
    public void testRegisterWorkflowDefInvalidName() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("invalid:name");//not allowed
        metadataService.registerWorkflowDef(workflowDef);
    }

    @Test
    public void testRegisterWorkflowDef() {
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("somename");
        workflowDef.setSchemaVersion(5);
        metadataService.registerWorkflowDef(workflowDef);
        verify(metadataDAO, times(1)).create(workflowDef);
        assertEquals(2, workflowDef.getSchemaVersion());
    }

    @Test(expected = ApplicationException.class)
    public void testUnregisterWorkflowDefNoName() {
        metadataService.unregisterWorkflowDef("", 1);
    }

    @Test(expected = ApplicationException.class)
    public void testUnregisterWorkflowDefNoVersion() {
        metadataService.unregisterWorkflowDef("somename", null);
    }

    @Test
    public void testUnregisterWorkflowDef() {
        metadataService.unregisterWorkflowDef("somename", 111);
        verify(metadataDAO, times(1)).removeWorkflowDef("somename", 111);
    }

    @Test(expected = ApplicationException.class)
    public void testValidateEventNoName() {
        EventHandler eventHandler = new EventHandler();
        metadataService.validateEvent(eventHandler);
    }

    @Test(expected = ApplicationException.class)
    public void testValidateEventNoEvent() {
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName("somename");
        metadataService.validateEvent(eventHandler);
    }

    @Test(expected = ApplicationException.class)
    public void testValidateEventNoAction() {
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName("somename");
        eventHandler.setEvent("someevent");
        metadataService.validateEvent(eventHandler);
    }

    @Test
    public void testValidateEvent() {
        EventHandler eventHandler = new EventHandler();
        eventHandler.setName("somename");
        eventHandler.setEvent("someevent");
        EventHandler.Action action = new EventHandler.Action();
        eventHandler.setActions(Arrays.asList(action));
        metadataService.validateEvent(eventHandler);

        verify(eventQueues, times(1)).getQueue("someevent");
    }

}
