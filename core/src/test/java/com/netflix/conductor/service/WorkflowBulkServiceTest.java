package com.netflix.conductor.service;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.matcher.Matchers;
import com.netflix.conductor.annotations.Service;
import com.netflix.conductor.core.config.ValidationModule;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.interceptors.ServiceInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.validation.ConstraintViolationException;
import javax.validation.Validator;
import java.util.*;

import static com.netflix.conductor.utility.TestUtils.getConstraintViolationMessages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WorkflowBulkServiceTest {

    private WorkflowExecutor workflowExecutor;

    private WorkflowBulkService workflowBulkService;

    @Before
    public void before() {
        workflowExecutor = Mockito.mock(WorkflowExecutor.class);
        Injector injector =
                Guice.createInjector(
                        new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(WorkflowExecutor.class).toInstance(workflowExecutor);
                                install(new ValidationModule());
                                bindInterceptor(Matchers.any(), Matchers.annotatedWith(Service.class), new ServiceInterceptor(getProvider(Validator.class)));
                            }
                        });
        workflowBulkService = injector.getInstance(WorkflowBulkServiceImpl.class);
    }

    @Test(expected = ConstraintViolationException.class)
    public void testPauseWorkflowNull(){
        try{
            workflowBulkService.pauseWorkflow(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowIds list cannot be null."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testPauseWorkflowWithInvalidListSize(){
        try{
            List<String> list = new ArrayList<>(1001);
            for(int i = 0; i < 1002; i++) {
                list.add("test");
            }
            workflowBulkService.pauseWorkflow(list);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("Cannot process more than 1000 workflows. Please use multiple requests."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testResumeWorkflowNull(){
        try{
            workflowBulkService.resumeWorkflow(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowIds list cannot be null."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRestartWorkflowNull(){
        try{
            workflowBulkService.restart(null, false);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowIds list cannot be null."));
            throw ex;
        }
    }

    @Test(expected = ConstraintViolationException.class)
    public void testRetryWorkflowNull(){
        try{
            workflowBulkService.retry(null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowIds list cannot be null."));
            throw ex;
        }
    }

    @Test
    public void testRetryWorkflowSuccessful(){
        //When
        workflowBulkService.retry(Collections.singletonList("anyId"));
        //Then
        Mockito.verify(workflowExecutor).retry("anyId", false);
    }


    @Test(expected = ConstraintViolationException.class)
    public void testTerminateNull(){
        try{
            workflowBulkService.terminate(null, null);
        } catch (ConstraintViolationException ex){
            assertEquals(1, ex.getConstraintViolations().size());
            Set<String> messages = getConstraintViolationMessages(ex.getConstraintViolations());
            assertTrue(messages.contains("WorkflowIds list cannot be null."));
            throw ex;
        }
    }

}
