package com.netflix.conductor.service;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.core.config.ValidationModule;
import com.netflix.conductor.dao.QueueDAO;
import com.netflix.conductor.interceptors.ServiceInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.Set;

import static com.netflix.conductor.utility.TestUtils.getConstraintViolationMessages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaskServiceTest {

    private TaskService taskService;

    private ExecutionService executionService;

    private QueueDAO queueDAO;

    @Before
    public void before() {
        executionService = Mockito.mock(ExecutionService.class);
        queueDAO = Mockito.mock(QueueDAO.class);
        Injector injector =
                Guice.createInjector(
                        new AbstractModule() {
                            @Override
                            protected void configure() {

                                bind(ExecutionService.class).toInstance(executionService);
                                bind(QueueDAO.class).toInstance(queueDAO);

                                install(new ValidationModule());
                                bindInterceptor(Matchers.any(), Matchers.annotatedWith(Service.class), new ServiceInterceptor(getProvider(Validator.class)));
                            }
                        });
        taskService = injector.getInstance(TaskServiceImpl.class);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testPoll(){
        try{
            taskService.poll(null, null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains( "TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testBatchPoll(){
        try{
            taskService.batchPoll(null, null, null, null,null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains( "TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTasks(){
        try{
            taskService.getTasks(null, null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains( "TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetPendingTaskForWorkflow(){
        try{
            taskService.getPendingTaskForWorkflow(null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowId cannot be null or empty."));
            assertTrue(messages.contains("TaskReferenceName cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTask() {
        try{
            taskService.updateTask(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskResult cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testUpdateTaskInValid() {
        try{
            TaskResult taskResult = new TaskResult();
            taskService.updateTask(taskResult);
        } catch (ConstraintViolationException ex){
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Workflow Id cannot be null or empty"));
            assertTrue(messages.contains("Task ID cannot be null or empty"));
            throw ex;
        }
    }


    @Test(expected = ConstraintViolationException.class)
    public void testAckTaskReceived() {
        try{
            taskService.ackTaskReceived(null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }
    @Test
    public void testAckTaskReceivedMissingWorkerId() {
        String ack = taskService.ackTaskReceived("abc", null);
        assertNotNull(ack);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testLog(){
        try{
            taskService.log(null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTaskLogs(){
        try{
            taskService.getTaskLogs(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetTask(){
        try{
            taskService.getTask(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRemoveTaskFromQueue(){
        try{
            taskService.removeTaskFromQueue(null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(2, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskId cannot be null or empty."));
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testGetPollData(){
        try{
            taskService.getPollData(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRequeuePendingTask(){
        try{
            taskService.requeuePendingTask(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("TaskType cannot be null or empty."));
            throw ex;
        }
    }
}
