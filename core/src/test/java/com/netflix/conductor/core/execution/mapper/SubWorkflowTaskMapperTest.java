package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.execution.tasks.SubWorkflow;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SubWorkflowTaskMapperTest {

    private SubWorkflowTaskMapper subWorkflowTaskMapper;
    private ParametersUtils parametersUtils;
    private MetadataDAO metadataDAO;
    private DeciderService deciderService;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        parametersUtils = mock(ParametersUtils.class);
        metadataDAO = mock(MetadataDAO.class);
        subWorkflowTaskMapper = new SubWorkflowTaskMapper(parametersUtils, metadataDAO);
        deciderService = mock(DeciderService.class);
    }

    @Test
    public void getMappedTasks() throws Exception {
        //Given
        WorkflowDef workflowDef = new WorkflowDef();
        Workflow  workflowInstance = new Workflow();
        WorkflowTask taskToSchedule = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion("2");
        taskToSchedule.setSubWorkflowParam(subWorkflowParams);
        Map<String,Object> taskInput = new HashMap<>();

        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name","FooWorkFlow");
        subWorkflowParamMap.put("version","2");
        when(parametersUtils.getTaskInputV2(anyMap(), any(Workflow.class), anyString(), any(TaskDef.class)))
                .thenReturn(subWorkflowParamMap);


        //When
        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(workflowDef)
                .withWorkflowInstance(workflowInstance)
                .withTaskToSchedule(taskToSchedule)
                .withTaskInput(taskInput)
                .withRetryCount(0)
                .withTaskId(IDGenerator.generate())
                .withDeciderService(deciderService)
                .build();

        List<Task> mappedTasks = subWorkflowTaskMapper.getMappedTasks(taskMapperContext);

        //Then
        assertTrue(!mappedTasks.isEmpty());
        assertEquals(1, mappedTasks.size());

        Task subWorkFlowTask = mappedTasks.get(0);
        assertEquals(Task.Status.SCHEDULED, subWorkFlowTask.getStatus());
        assertEquals(SubWorkflow.NAME, subWorkFlowTask.getTaskType());
    }


    @Test
    public void getSubWorkflowParams() throws Exception {
        WorkflowTask workflowTask = new WorkflowTask();
        SubWorkflowParams subWorkflowParams = new SubWorkflowParams();
        subWorkflowParams.setName("Foo");
        subWorkflowParams.setVersion("2");
        workflowTask.setSubWorkflowParam(subWorkflowParams);

        assertEquals(subWorkflowParams, subWorkflowTaskMapper.getSubWorkflowParams(workflowTask));
    }

    @Test
    public void getExceptionWhenNoSubWorkflowParamsPassed() throws Exception {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName("FooWorkFLow");

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("Task %s is defined as sub-workflow and is missing subWorkflowParams. " +
                "Please check the blueprint", workflowTask.getName()));

        subWorkflowTaskMapper.getSubWorkflowParams(workflowTask);
    }


    @Test
    public void getSubWorkflowVersion() throws Exception {
        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        subWorkflowParamMap.put("name","FooWorkFlow");
        subWorkflowParamMap.put("version","2");

        Integer version = subWorkflowTaskMapper.getSubWorkflowVersion(subWorkflowParamMap, "FooWorkFlow");

        assertEquals(version, Integer.valueOf(2));
    }

    @Test
    public void getSubworkflowVersionFromMeta() throws Exception {
        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setName("FooWorkFlow");
        workflowDef.setVersion(2);
        when(metadataDAO.getLatest(any())).thenReturn(workflowDef);

        Integer version = subWorkflowTaskMapper.getSubWorkflowVersion(subWorkflowParamMap, "FooWorkFlow");

        assertEquals(version, Integer.valueOf(2));
    }

    @Test
    public void getSubworkflowVersionFromMetaException() throws Exception {
        Map<String, Object> subWorkflowParamMap = new HashMap<>();
        when(metadataDAO.getLatest(any())).thenReturn(null);

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage(String.format("The Task %s defined as a sub-workflow has no workflow definition available ", "FooWorkFlow"));

        subWorkflowTaskMapper.getSubWorkflowVersion(subWorkflowParamMap, "FooWorkFlow");
    }

}