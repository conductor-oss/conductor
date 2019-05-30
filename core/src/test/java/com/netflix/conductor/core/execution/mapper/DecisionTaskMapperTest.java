package com.netflix.conductor.core.execution.mapper;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecisionTaskMapperTest {

    private ParametersUtils parametersUtils;
    private DeciderService deciderService;
    //Subject
    private DecisionTaskMapper decisionTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    Map<String, Object> ip1;
    WorkflowTask task1;
    WorkflowTask task2;
    WorkflowTask task3;

    @Before
    public void setUp() {
        parametersUtils = new ParametersUtils();

        ip1 = new HashMap<>();
        ip1.put("p1", "${workflow.input.param1}");
        ip1.put("p2", "${workflow.input.param2}");
        ip1.put("case", "${workflow.input.case}");

        task1 = new WorkflowTask();
        task1.setName("Test1");
        task1.setInputParameters(ip1);
        task1.setTaskReferenceName("t1");

        task2 = new WorkflowTask();
        task2.setName("Test2");
        task2.setInputParameters(ip1);
        task2.setTaskReferenceName("t2");

        task3 = new WorkflowTask();
        task3.setName("Test3");
        task3.setInputParameters(ip1);
        task3.setTaskReferenceName("t3");
        deciderService = mock(DeciderService.class);
        decisionTaskMapper = new DecisionTaskMapper();
    }

    @Test
    public void getMappedTasks() {

        //Given
        //Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("Id", "${workflow.input.Id}");
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        //Decision task instance
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setName("Decision");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setDefaultCase(Arrays.asList(task1));
        decisionTask.setCaseValueParam("case");
        decisionTask.getInputParameters().put("Id", "${workflow.input.Id}");
        decisionTask.setCaseExpression("if ($.Id == null) 'bad input'; else if ( ($.Id != null && $.Id % 2 == 0)) 'even'; else 'odd'; ");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Arrays.asList(task2));
        decisionCases.put("odd", Arrays.asList(task3));
        decisionTask.setDecisionCases(decisionCases);
        //Workflow instance
        WorkflowDef workflowDef = new WorkflowDef();
        workflowDef.setSchemaVersion(2);

        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(workflowDef);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("Id", "22");
        workflowInstance.setInput(workflowInput);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);

        Map<String, Object> input = parametersUtils.getTaskInput(decisionTask.getInputParameters(),
                workflowInstance, null, null);


        Task theTask = new Task();
        theTask.setReferenceTaskName("Foo");
        theTask.setTaskId(IDGenerator.generate());

        when(deciderService.getTasksToBeScheduled(workflowInstance, task2, 0, null))
                .thenReturn(Arrays.asList(theTask));

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(workflowDef)
                .withWorkflowInstance(workflowInstance)
                .withTaskToSchedule(decisionTask)
                .withTaskInput(input)
                .withRetryCount(0)
                .withTaskId(IDGenerator.generate())
                .withDeciderService(deciderService)
                .build();

        //When
        List<Task> mappedTasks = decisionTaskMapper.getMappedTasks(taskMapperContext);

        //Then
        assertEquals(2, mappedTasks.size());
        assertEquals("decisionTask", mappedTasks.get(0).getReferenceTaskName());
        assertEquals("Foo", mappedTasks.get(1).getReferenceTaskName());

    }

    @Test
    public void getEvaluatedCaseValue() {
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setName("Decision");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setInputParameters(ip1);
        decisionTask.setDefaultCase(Arrays.asList(task1));
        decisionTask.setCaseValueParam("case");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("0", Arrays.asList(task2));
        decisionCases.put("1", Arrays.asList(task3));
        decisionTask.setDecisionCases(decisionCases);

        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(new WorkflowDef());
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("param1", "test1");
        workflowInput.put("param2", "test2");
        workflowInput.put("case", "0");
        workflowInstance.setInput(workflowInput);

        Map<String, Object> input = parametersUtils.getTaskInput(decisionTask.getInputParameters(),
                workflowInstance, null, null);

        assertEquals("0", decisionTaskMapper.getEvaluatedCaseValue(decisionTask, input));
    }

    @Test
    public void getEvaluatedCaseValueUsingExpression() {
        //Given
        //Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("Id", "${workflow.input.Id}");
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        //Decision task instance
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setName("Decision");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setDefaultCase(Arrays.asList(task1));
        decisionTask.setCaseValueParam("case");
        decisionTask.getInputParameters().put("Id", "${workflow.input.Id}");
        decisionTask.setCaseExpression("if ($.Id == null) 'bad input'; else if ( ($.Id != null && $.Id % 2 == 0)) 'even'; else 'odd'; ");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Arrays.asList(task2));
        decisionCases.put("odd", Arrays.asList(task3));
        decisionTask.setDecisionCases(decisionCases);

        //Workflow instance
        WorkflowDef def = new WorkflowDef();
        def.setSchemaVersion(2);

        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(def);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put("Id", "22");
        workflowInstance.setInput(workflowInput);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);

        Map<String, Object> evaluatorInput = parametersUtils.getTaskInput(decisionTask.getInputParameters(),
                workflowInstance, taskDef, null);

        assertEquals("even", decisionTaskMapper.getEvaluatedCaseValue(decisionTask, evaluatorInput));

    }


    @Test
    public void getEvaluatedCaseValueException() {
        //Given
        //Task Definition
        TaskDef taskDef = new TaskDef();
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("Id", "${workflow.input.Id}");
        List<Map<String, Object>> taskDefinitionInput = new LinkedList<>();
        taskDefinitionInput.add(inputMap);

        //Decision task instance
        WorkflowTask decisionTask = new WorkflowTask();
        decisionTask.setType(TaskType.DECISION.name());
        decisionTask.setName("Decision");
        decisionTask.setTaskReferenceName("decisionTask");
        decisionTask.setDefaultCase(Collections.singletonList(task1));
        decisionTask.setCaseValueParam("case");
        decisionTask.getInputParameters().put("Id", "${workflow.input.Id}");
        decisionTask.setCaseExpression("if ($Id == null) 'bad input'; else if ( ($Id != null && $Id % 2 == 0)) 'even'; else 'odd'; ");
        Map<String, List<WorkflowTask>> decisionCases = new HashMap<>();
        decisionCases.put("even", Collections.singletonList(task2));
        decisionCases.put("odd", Collections.singletonList(task3));
        decisionTask.setDecisionCases(decisionCases);

        //Workflow instance
        WorkflowDef def = new WorkflowDef();
        def.setSchemaVersion(2);

        Workflow workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(def);
        Map<String, Object> workflowInput = new HashMap<>();
        workflowInput.put(".Id", "22");
        workflowInstance.setInput(workflowInput);

        Map<String, Object> body = new HashMap<>();
        body.put("input", taskDefinitionInput);
        taskDef.getInputTemplate().putAll(body);


        Map<String, Object> evaluatorInput = parametersUtils.getTaskInput(decisionTask.getInputParameters(),
                workflowInstance, taskDef, null);

        expectedException.expect(TerminateWorkflowException.class);
        expectedException.expectMessage("Error while evaluating script: " + decisionTask.getCaseExpression());

        decisionTaskMapper.getEvaluatedCaseValue(decisionTask, evaluatorInput);
    }
}
