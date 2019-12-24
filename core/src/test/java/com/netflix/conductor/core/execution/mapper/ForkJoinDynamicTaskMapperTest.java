package com.netflix.conductor.core.execution.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.TaskType;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.DeciderService;
import com.netflix.conductor.core.execution.ParametersUtils;
import com.netflix.conductor.core.execution.SystemTaskType;
import com.netflix.conductor.core.execution.TerminateWorkflowException;
import com.netflix.conductor.core.utils.IDGenerator;
import com.netflix.conductor.dao.MetadataDAO;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class ForkJoinDynamicTaskMapperTest {

    private MetadataDAO metadataDAO;
    private ParametersUtils parametersUtils;
    private ObjectMapper objectMapper;
    private DeciderService deciderService;
    private ForkJoinDynamicTaskMapper forkJoinDynamicTaskMapper;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();


    @Before
    public void setUp() throws Exception {
        metadataDAO = Mockito.mock(MetadataDAO.class);
        parametersUtils = Mockito.mock(ParametersUtils.class);
        objectMapper = Mockito.mock(ObjectMapper.class);
        deciderService = Mockito.mock(DeciderService.class);

        forkJoinDynamicTaskMapper = new ForkJoinDynamicTaskMapper(parametersUtils, objectMapper, metadataDAO);

    }

    @Test
    public void getMappedTasksException() {

        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        Workflow  workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(def);

        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");


        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamictask_join");

        def.getTasks().add(dynamicForkJoinToSchedule);

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        //when
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(Arrays.asList(wt2, wt3));


        Task simpleTask1 = new Task();
        simpleTask1.setReferenceTaskName("xdt1");

        Task simpleTask2 = new Task();
        simpleTask2.setReferenceTaskName("xdt2");

        when(deciderService.getTasksToBeScheduled(workflowInstance, wt2, 0 )).thenReturn(Arrays.asList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowInstance, wt3, 0 )).thenReturn(Arrays.asList(simpleTask2));

        String taskId = IDGenerator.generate();

        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(def)
                .withWorkflowInstance(workflowInstance)
                .withTaskToSchedule(dynamicForkJoinToSchedule)
                .withRetryCount(0)
                .withTaskId(taskId)
                .withDeciderService(deciderService)
                .build();

        //then
        expectedException.expect(TerminateWorkflowException.class);
        forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);

    }

    @Test
    public void getMappedTasks() {

        WorkflowDef def = new WorkflowDef();
        def.setName("DYNAMIC_FORK_JOIN_WF");
        def.setDescription(def.getName());
        def.setVersion(1);
        def.setInputParameters(Arrays.asList("param1", "param2"));

        Workflow  workflowInstance = new Workflow();
        workflowInstance.setWorkflowDefinition(def);

        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");


        WorkflowTask join = new WorkflowTask();
        join.setType(TaskType.JOIN.name());
        join.setTaskReferenceName("dynamictask_join");

        def.getTasks().add(dynamicForkJoinToSchedule);
        def.getTasks().add(join);

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        //when
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(), any()))
                .thenReturn(dynamicTasksInput);
        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(Arrays.asList(wt2, wt3));


        Task simpleTask1 = new Task();
        simpleTask1.setReferenceTaskName("xdt1");

        Task simpleTask2 = new Task();
        simpleTask2.setReferenceTaskName("xdt2");

        when(deciderService.getTasksToBeScheduled(workflowInstance, wt2, 0 )).thenReturn(Arrays.asList(simpleTask1));
        when(deciderService.getTasksToBeScheduled(workflowInstance, wt3, 0 )).thenReturn(Arrays.asList(simpleTask2));

        String taskId = IDGenerator.generate();
        TaskMapperContext taskMapperContext = TaskMapperContext.newBuilder()
                .withWorkflowDefinition(def)
                .withWorkflowInstance(workflowInstance)
                .withTaskToSchedule(dynamicForkJoinToSchedule)
                .withRetryCount(0)
                .withTaskId(taskId)
                .withDeciderService(deciderService)
                .build();

        //then
        List<Task> mappedTasks = forkJoinDynamicTaskMapper.getMappedTasks(taskMapperContext);

        assertEquals(4, mappedTasks.size());

        assertEquals(SystemTaskType.FORK.name(),mappedTasks.get(0).getTaskType());
        assertEquals(SystemTaskType.JOIN.name(), mappedTasks.get(3).getTaskType());
        List<String> joinTaskNames = (List<String>)mappedTasks.get(3).getInputData().get("joinOn");
        assertEquals("xdt1, xdt2", joinTaskNames.stream().collect(Collectors.joining(", ")));

    }


    @Test
    public void getDynamicForkJoinTasksAndInput() {
        //Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkJoinTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        DynamicForkJoinTaskList dtasks = new DynamicForkJoinTaskList();

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "v1");
        dtasks.add("junit_task_2", null, "xdt1", input);

        HashMap<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");
        dtasks.add("junit_task_3", null, "xdt2", input2);

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("dynamicTasks", dtasks);

        //when
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(TaskDef.class), anyString()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(Class.class))).thenReturn(dtasks);

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> dynamicForkJoinTasksAndInput =
                forkJoinDynamicTaskMapper.getDynamicForkJoinTasksAndInput(dynamicForkJoinToSchedule, new Workflow());
        //then
        assertNotNull(dynamicForkJoinTasksAndInput.getLeft());
        assertEquals(2,dynamicForkJoinTasksAndInput.getLeft().size());
        assertEquals(2, dynamicForkJoinTasksAndInput.getRight().size());

    }

    @Test
    public void getDynamicForkJoinTasksAndInputException() {
        //Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkJoinTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        DynamicForkJoinTaskList dtasks = new DynamicForkJoinTaskList();

        Map<String, Object> input = new HashMap<>();
        input.put("k1", "v1");
        dtasks.add("junit_task_2", null, "xdt1", input);

        HashMap<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");
        dtasks.add("junit_task_3", null, "xdt2", input2);

        Map<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("dynamicTasks", dtasks);

        //when
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(TaskDef.class), anyString()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(Class.class))).thenReturn(null);

        //then
        expectedException.expect(TerminateWorkflowException.class);

        forkJoinDynamicTaskMapper.getDynamicForkJoinTasksAndInput(dynamicForkJoinToSchedule, new Workflow());


    }

    @Test
    public void getDynamicForkTasksAndInput() {
        //Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", dynamicTasksInput);

        //when
        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(Arrays.asList(wt2, wt3));

        Pair<List<WorkflowTask>, Map<String, Map<String, Object>>> dynamicTasks = forkJoinDynamicTaskMapper.getDynamicForkTasksAndInput(dynamicForkJoinToSchedule, new Workflow(), "dynamicTasks");

        //then
        assertNotNull(dynamicTasks.getLeft());
    }

    @Test
    public void getDynamicForkTasksAndInputException() {

        //Given
        WorkflowTask dynamicForkJoinToSchedule = new WorkflowTask();
        dynamicForkJoinToSchedule.setType(TaskType.FORK_JOIN_DYNAMIC.name());
        dynamicForkJoinToSchedule.setTaskReferenceName("dynamicfanouttask");
        dynamicForkJoinToSchedule.setDynamicForkTasksParam("dynamicTasks");
        dynamicForkJoinToSchedule.setDynamicForkTasksInputParamName("dynamicTasksInput");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasks", "dt1.output.dynamicTasks");
        dynamicForkJoinToSchedule.getInputParameters().put("dynamicTasksInput", "dt1.output.dynamicTasksInput");

        Map<String, Object> input1 = new HashMap<>();
        input1.put("k1", "v1");
        WorkflowTask wt2 = new WorkflowTask();
        wt2.setName("junit_task_2");
        wt2.setTaskReferenceName("xdt1");

        Map<String, Object> input2 = new HashMap<>();
        input2.put("k2", "v2");

        WorkflowTask wt3 = new WorkflowTask();
        wt3.setName("junit_task_3");
        wt3.setTaskReferenceName("xdt2");

        HashMap<String, Object> dynamicTasksInput = new HashMap<>();
        dynamicTasksInput.put("xdt1", input1);
        dynamicTasksInput.put("xdt2", input2);
        dynamicTasksInput.put("dynamicTasks", Arrays.asList(wt2, wt3));
        dynamicTasksInput.put("dynamicTasksInput", null);

        when(parametersUtils.getTaskInput(anyMap(), any(Workflow.class), any(), any()))
                .thenReturn(dynamicTasksInput);

        when(objectMapper.convertValue(any(), any(TypeReference.class))).thenReturn(Arrays.asList(wt2, wt3));
        //then
        expectedException.expect(TerminateWorkflowException.class);
        //when
        forkJoinDynamicTaskMapper.getDynamicForkTasksAndInput(dynamicForkJoinToSchedule, new Workflow(), "dynamicTasks");

    }
}
