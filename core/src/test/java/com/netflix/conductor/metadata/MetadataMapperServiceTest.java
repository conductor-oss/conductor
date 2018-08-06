package com.netflix.conductor.metadata;

import com.google.common.collect.ImmutableList;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.metadata.MetadataMapperService;
import com.netflix.conductor.dao.MetadataDAO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MetadataMapperServiceTest {

    @Mock
    private MetadataDAO metadataDAO;

    @InjectMocks
    private MetadataMapperService metadataMapperService;

    @Test
    public void testMetadataPopulationOnSimpleTask() {
        String nameTaskDefinition = "task1";
        TaskDef taskDefinition = createTaskDefinition(nameTaskDefinition);
        WorkflowTask workflowTask = createWorkflowTask(nameTaskDefinition);

        when(metadataDAO.getTaskDef(nameTaskDefinition)).thenReturn(taskDefinition);

        WorkflowDef workflowDefinition = createWorkflowDefinition("testMetadataPopulation");
        workflowDefinition.setTasks(ImmutableList.of(workflowTask));

        metadataMapperService.populateTaskDefinitions(workflowDefinition);

        assertEquals(1, workflowDefinition.getTasks().size());
        WorkflowTask populatedWorkflowTask = workflowDefinition.getTasks().get(0);
        assertNotNull(populatedWorkflowTask.getTaskDefinition());
        verify(metadataDAO).getTaskDef(nameTaskDefinition);
    }

    @Test
    public void testNoMetadataPopulationOnEmbeddedTaskDefinition() {
        String nameTaskDefinition = "task2";
        TaskDef taskDefinition = createTaskDefinition(nameTaskDefinition);
        WorkflowTask workflowTask = createWorkflowTask(nameTaskDefinition);
        workflowTask.setTaskDefinition(taskDefinition);

        WorkflowDef workflowDefinition = createWorkflowDefinition("testMetadataPopulation");
        workflowDefinition.setTasks(ImmutableList.of(workflowTask));

        metadataMapperService.populateTaskDefinitions(workflowDefinition);

        assertEquals(1, workflowDefinition.getTasks().size());
        WorkflowTask populatedWorkflowTask = workflowDefinition.getTasks().get(0);
        assertNotNull(populatedWorkflowTask.getTaskDefinition());
        verifyZeroInteractions(metadataDAO);
    }

    @Test
    public void testMetadataPopulationOnlyOnNecessaryWorkflowTasks() {
        String nameTaskDefinition1 = "task4";
        TaskDef taskDefinition = createTaskDefinition(nameTaskDefinition1);
        WorkflowTask workflowTask1 = createWorkflowTask(nameTaskDefinition1);
        workflowTask1.setTaskDefinition(taskDefinition);

        String nameTaskDefinition2 = "task5";
        WorkflowTask workflowTask2 = createWorkflowTask(nameTaskDefinition2);

        WorkflowDef workflowDefinition = createWorkflowDefinition("testMetadataPopulation");
        workflowDefinition.setTasks(ImmutableList.of(workflowTask1, workflowTask2));

        when(metadataDAO.getTaskDef(nameTaskDefinition2)).thenReturn(taskDefinition);

        metadataMapperService.populateTaskDefinitions(workflowDefinition);

        assertEquals(2, workflowDefinition.getTasks().size());
        List<WorkflowTask> workflowTasks = workflowDefinition.getTasks();
        assertNotNull(workflowTasks.get(0).getTaskDefinition());
        assertNotNull(workflowTasks.get(1).getTaskDefinition());

        verify(metadataDAO).getTaskDef(nameTaskDefinition2);
        verifyNoMoreInteractions(metadataDAO);
    }

    @Test
    public void testVersionPopulationForSubworkflowTaskIfNotAvailable() {
        // TODO
    }

    @Test
    public void testNoVersionPopulationForSubworkflowTaskIfAvailable() {
        // TODO
    }


    @Test
    public void testExceptionWhenWorkflowDefinitionNotAvailable() {
        // TODO
    }


    private WorkflowDef createWorkflowDefinition(String name) {
        WorkflowDef workflowDefinition = new WorkflowDef();
        workflowDefinition.setName(name);
        return workflowDefinition;
    }

    private WorkflowTask createWorkflowTask(String name) {
        WorkflowTask workflowTask = new WorkflowTask();
        workflowTask.setName(name);
        workflowTask.setType(WorkflowTask.Type.SIMPLE.name());
        return workflowTask;
    }

    private TaskDef createTaskDefinition(String name) {
        TaskDef taskDefinition = new TaskDef(name);
        return taskDefinition;
    }

}
