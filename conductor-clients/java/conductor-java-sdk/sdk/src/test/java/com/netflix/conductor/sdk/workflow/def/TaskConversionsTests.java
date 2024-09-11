/*
 * Copyright 2022 Conductor Authors.
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
package com.netflix.conductor.sdk.workflow.def;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.sdk.workflow.def.tasks.DoWhile;
import com.netflix.conductor.sdk.workflow.def.tasks.Dynamic;
import com.netflix.conductor.sdk.workflow.def.tasks.DynamicFork;
import com.netflix.conductor.sdk.workflow.def.tasks.Event;
import com.netflix.conductor.sdk.workflow.def.tasks.ForkJoin;
import com.netflix.conductor.sdk.workflow.def.tasks.Http;
import com.netflix.conductor.sdk.workflow.def.tasks.JQ;
import com.netflix.conductor.sdk.workflow.def.tasks.Javascript;
import com.netflix.conductor.sdk.workflow.def.tasks.Join;
import com.netflix.conductor.sdk.workflow.def.tasks.SetVariable;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.def.tasks.SubWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.def.tasks.Task;
import com.netflix.conductor.sdk.workflow.def.tasks.TaskRegistry;
import com.netflix.conductor.sdk.workflow.def.tasks.Terminate;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskConversionsTests {

    static {
        WorkflowExecutor.initTaskImplementations();
    }

    @Test
    public void testSimpleTaskConversion() {
        SimpleTask simpleTask = new SimpleTask("task_name", "task_ref_name");

        Map<String, Object> map = new HashMap<>();
        map.put("key11", "value11");
        map.put("key12", 100);

        simpleTask.input("key1", "value");
        simpleTask.input("key2", 42);
        simpleTask.input("key3", true);
        simpleTask.input("key4", map);

        WorkflowTask workflowTask = simpleTask.getWorkflowDefTasks().get(0);

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof SimpleTask);
        SimpleTask simpleTaskFromWorkflowTask = (SimpleTask) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(simpleTask.getName(), fromWorkflowTask.getName());
        assertEquals(simpleTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(simpleTask.getTaskDef(), simpleTaskFromWorkflowTask.getTaskDef());
        assertEquals(simpleTask.getType(), simpleTaskFromWorkflowTask.getType());
        assertEquals(simpleTask.getStartDelay(), simpleTaskFromWorkflowTask.getStartDelay());
        assertEquals(simpleTask.getInput(), simpleTaskFromWorkflowTask.getInput());
    }

    @Test
    public void testDynamicTaskCoversion() {
        Dynamic dynamicTask = new Dynamic("task_name", "task_ref_name");

        WorkflowTask workflowTask = dynamicTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters().get(Dynamic.TASK_NAME_INPUT_PARAM));

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof Dynamic);
        Dynamic taskFromWorkflowTask = (Dynamic) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(dynamicTask.getName(), fromWorkflowTask.getName());
        assertEquals(dynamicTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(dynamicTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(dynamicTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(dynamicTask.getInput(), taskFromWorkflowTask.getInput());
    }

    @Test
    public void testForkTaskConversion() {
        SimpleTask task1 = new SimpleTask("task1", "task1");
        SimpleTask task2 = new SimpleTask("task2", "task2");
        SimpleTask task3 = new SimpleTask("task3", "task3");

        ForkJoin forkTask =
                new ForkJoin("task_ref_name", new Task[] {task1}, new Task[] {task2, task3});

        WorkflowTask workflowTask = forkTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getForkTasks());
        assertFalse(workflowTask.getForkTasks().isEmpty());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof ForkJoin);
        ForkJoin taskFromWorkflowTask = (ForkJoin) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(forkTask.getName(), fromWorkflowTask.getName());
        assertEquals(forkTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(forkTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(forkTask.getInput(), taskFromWorkflowTask.getInput());

        assertEquals(
                forkTask.getForkedTasks().length, taskFromWorkflowTask.getForkedTasks().length);
        for (int i = 0; i < forkTask.getForkedTasks().length; i++) {
            assertEquals(
                    forkTask.getForkedTasks()[i].length,
                    taskFromWorkflowTask.getForkedTasks()[i].length);
            for (int j = 0; j < forkTask.getForkedTasks()[i].length; j++) {
                assertEquals(
                        forkTask.getForkedTasks()[i][j].getTaskReferenceName(),
                        taskFromWorkflowTask.getForkedTasks()[i][j].getTaskReferenceName());

                assertEquals(
                        forkTask.getForkedTasks()[i][j].getName(),
                        taskFromWorkflowTask.getForkedTasks()[i][j].getName());

                assertEquals(
                        forkTask.getForkedTasks()[i][j].getType(),
                        taskFromWorkflowTask.getForkedTasks()[i][j].getType());
            }
        }
    }

    @Test
    public void testDynamicForkTaskConversion() {
        DynamicFork dynamicTask = new DynamicFork("task_ref_name", "forkTasks", "forkTaskInputs");

        WorkflowTask workflowTask = dynamicTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof DynamicFork);
        DynamicFork taskFromWorkflowTask = (DynamicFork) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(dynamicTask.getName(), fromWorkflowTask.getName());
        assertEquals(dynamicTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(dynamicTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(dynamicTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(dynamicTask.getInput(), taskFromWorkflowTask.getInput());
        assertEquals(
                dynamicTask.getForkTasksParameter(), taskFromWorkflowTask.getForkTasksParameter());
        assertEquals(
                dynamicTask.getForkTasksInputsParameter(),
                taskFromWorkflowTask.getForkTasksInputsParameter());
    }

    @Test
    public void testDoWhileConversion() {
        SimpleTask task1 = new SimpleTask("task_name", "task_ref_name");
        SimpleTask task2 = new SimpleTask("task_name", "task_ref_name");

        DoWhile doWhileTask = new DoWhile("task_ref_name", 2, task1, task2);

        WorkflowTask workflowTask = doWhileTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof DoWhile);
        DoWhile taskFromWorkflowTask = (DoWhile) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(doWhileTask.getName(), fromWorkflowTask.getName());
        assertEquals(doWhileTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(doWhileTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(doWhileTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(doWhileTask.getInput(), taskFromWorkflowTask.getInput());

        assertEquals(doWhileTask.getLoopCondition(), taskFromWorkflowTask.getLoopCondition());
        assertEquals(
                doWhileTask.getLoopTasks().stream()
                        .map(Task::getTaskReferenceName)
                        .sorted()
                        .collect(Collectors.toSet()),
                taskFromWorkflowTask.getLoopTasks().stream()
                        .map(Task::getTaskReferenceName)
                        .sorted()
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testJoin() {

        Join joinTask = new Join("task_ref_name", "task1", "task2");

        WorkflowTask workflowTask = joinTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());
        assertNotNull(workflowTask.getJoinOn());
        assertTrue(!workflowTask.getJoinOn().isEmpty());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Join,
                "task is not of type Join, but of type " + fromWorkflowTask.getClass().getName());
        Join taskFromWorkflowTask = (Join) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(joinTask.getName(), fromWorkflowTask.getName());
        assertEquals(joinTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(joinTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(joinTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(joinTask.getInput(), taskFromWorkflowTask.getInput());

        assertEquals(joinTask.getJoinOn().length, taskFromWorkflowTask.getJoinOn().length);
        assertEquals(
                Arrays.asList(joinTask.getJoinOn()).stream().sorted().collect(Collectors.toSet()),
                Arrays.asList(taskFromWorkflowTask.getJoinOn()).stream()
                        .sorted()
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testEvent() {

        Event eventTask = new Event("task_ref_name", "sqs:queue11");

        WorkflowTask workflowTask = eventTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Event,
                "task is not of type Event, but of type " + fromWorkflowTask.getClass().getName());
        Event taskFromWorkflowTask = (Event) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(eventTask.getName(), fromWorkflowTask.getName());
        assertEquals(eventTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(eventTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(eventTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(eventTask.getInput(), taskFromWorkflowTask.getInput());
        assertEquals(eventTask.getSink(), taskFromWorkflowTask.getSink());
    }

    @Test
    public void testSetVariableConversion() {

        SetVariable setVariableTask = new SetVariable("task_ref_name");

        WorkflowTask workflowTask = setVariableTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof SetVariable,
                "task is not of type SetVariable, but of type "
                        + fromWorkflowTask.getClass().getName());
        SetVariable taskFromWorkflowTask = (SetVariable) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(setVariableTask.getName(), fromWorkflowTask.getName());
        assertEquals(
                setVariableTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(setVariableTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(setVariableTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(setVariableTask.getInput(), taskFromWorkflowTask.getInput());
    }

    @Test
    public void testSubWorkflowConversion() {

        SubWorkflow subWorkflowTask = new SubWorkflow("task_ref_name", "sub_flow", 2);

        WorkflowTask workflowTask = subWorkflowTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof SubWorkflow,
                "task is not of type SubWorkflow, but of type "
                        + fromWorkflowTask.getClass().getName());
        SubWorkflow taskFromWorkflowTask = (SubWorkflow) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(subWorkflowTask.getName(), fromWorkflowTask.getName());
        assertEquals(
                subWorkflowTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(subWorkflowTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(subWorkflowTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(subWorkflowTask.getInput(), taskFromWorkflowTask.getInput());
        assertEquals(subWorkflowTask.getWorkflowName(), taskFromWorkflowTask.getWorkflowName());
        assertEquals(
                subWorkflowTask.getWorkflowVersion(), taskFromWorkflowTask.getWorkflowVersion());
    }

    @Test
    public void testSwitchConversion() {

        SimpleTask task1 = new SimpleTask("task_name", "task_ref_name1");
        SimpleTask task2 = new SimpleTask("task_name", "task_ref_name2");
        SimpleTask task3 = new SimpleTask("task_name", "task_ref_name3");

        Switch decision = new Switch("switch", "${workflow.input.zip");
        decision.switchCase("caseA", task1);
        decision.switchCase("caseB", task2, task3);

        decision.defaultCase(
                new Terminate("terminate", Workflow.WorkflowStatus.FAILED, "", new HashMap<>()));

        WorkflowTask workflowTask = decision.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Switch,
                "task is not of type Switch, but of type " + fromWorkflowTask.getClass().getName());
        Switch taskFromWorkflowTask = (Switch) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(decision.getName(), fromWorkflowTask.getName());
        assertEquals(decision.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(decision.getType(), taskFromWorkflowTask.getType());
        assertEquals(decision.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(decision.getInput(), taskFromWorkflowTask.getInput());
        // TODO: ADD CASES FOR DEFAULT CASE
        assertEquals(decision.getBranches().keySet(), taskFromWorkflowTask.getBranches().keySet());
        assertEquals(
                decision.getBranches().values().stream()
                        .map(
                                tasks ->
                                        tasks.stream()
                                                .map(Task::getTaskReferenceName)
                                                .collect(Collectors.toSet()))
                        .collect(Collectors.toSet()),
                taskFromWorkflowTask.getBranches().values().stream()
                        .map(
                                tasks ->
                                        tasks.stream()
                                                .map(Task::getTaskReferenceName)
                                                .collect(Collectors.toSet()))
                        .collect(Collectors.toSet()));
        assertEquals(decision.getBranches().size(), taskFromWorkflowTask.getBranches().size());
    }

    @Test
    public void testTerminateConversion() {

        Terminate terminateTask =
                new Terminate("terminate", Workflow.WorkflowStatus.FAILED, "", new HashMap<>());

        WorkflowTask workflowTask = terminateTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Terminate,
                "task is not of type Terminate, but of type "
                        + fromWorkflowTask.getClass().getName());
        Terminate taskFromWorkflowTask = (Terminate) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(terminateTask.getName(), fromWorkflowTask.getName());
        assertEquals(terminateTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(terminateTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(terminateTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(terminateTask.getInput(), taskFromWorkflowTask.getInput());
    }

    @Test
    public void testWaitConversion() {

        Wait waitTask = new Wait("terminate");

        WorkflowTask workflowTask = waitTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Wait,
                "task is not of type Wait, but of type " + fromWorkflowTask.getClass().getName());
        Wait taskFromWorkflowTask = (Wait) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(waitTask.getName(), fromWorkflowTask.getName());
        assertEquals(waitTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(waitTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(waitTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(waitTask.getInput(), taskFromWorkflowTask.getInput());

        // Wait for 10 seconds
        waitTask = new Wait("wait_for_10_seconds", Duration.of(10, ChronoUnit.SECONDS));
        workflowTask = waitTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());
        assertEquals("10s", workflowTask.getInputParameters().get(Wait.DURATION_INPUT));

        // Wait for 10 minutes
        waitTask = new Wait("wait_for_10_seconds", Duration.of(10, ChronoUnit.MINUTES));
        workflowTask = waitTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());
        assertEquals("600s", workflowTask.getInputParameters().get(Wait.DURATION_INPUT));

        // Wait till next week some time
        ZonedDateTime nextWeek = ZonedDateTime.now().plusDays(7);
        String formattedDateTime = Wait.dateTimeFormatter.format(nextWeek);
        waitTask = new Wait("wait_till_next_week", nextWeek);
        workflowTask = waitTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());
        assertEquals(formattedDateTime, workflowTask.getInputParameters().get(Wait.UNTIL_INPUT));
    }

    @Test
    public void testHttpConverter() {

        Http httpTask = new Http("terminate");
        Http.Input input = new Http.Input();
        input.setUri("http://example.com");
        input.setMethod(Http.Input.HttpMethod.POST);
        input.setBody("Hello World");
        input.setReadTimeOut(100);
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-AUTHORIZATION", "my_api_key");
        input.setHeaders(headers);

        httpTask.input(input);

        WorkflowTask workflowTask = httpTask.getWorkflowDefTasks().get(0);
        assertNotNull(workflowTask.getInputParameters());

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Http,
                "task is not of type Http, but of type " + fromWorkflowTask.getClass().getName());
        Http taskFromWorkflowTask = (Http) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(httpTask.getName(), fromWorkflowTask.getName());
        assertEquals(httpTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(httpTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(httpTask.getStartDelay(), taskFromWorkflowTask.getStartDelay());
        assertEquals(httpTask.getInput(), taskFromWorkflowTask.getInput());
        assertEquals(httpTask.getHttpRequest(), taskFromWorkflowTask.getHttpRequest());

        System.out.println(httpTask.getInput());
        System.out.println(taskFromWorkflowTask.getInput());
    }

    @Test
    public void testJQTaskConversion() {
        JQ jqTask = new JQ("task_name", "{ key3: (.key1.value1 + .key2.value2) }");

        Map<String, Object> map = new HashMap<>();
        map.put("key11", "value11");
        map.put("key12", 100);

        jqTask.input("key1", "value");
        jqTask.input("key2", 42);
        jqTask.input("key3", true);
        jqTask.input("key4", map);

        WorkflowTask workflowTask = jqTask.getWorkflowDefTasks().get(0);

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(fromWorkflowTask instanceof JQ, "Found the instance " + fromWorkflowTask);
        JQ taskFromWorkflowTask = (JQ) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(jqTask.getName(), fromWorkflowTask.getName());
        assertEquals(jqTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(jqTask.getQueryExpression(), taskFromWorkflowTask.getQueryExpression());
        assertEquals(jqTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(jqTask.getInput(), taskFromWorkflowTask.getInput());
    }

    @Test
    public void testInlineTaskConversion() {

        Javascript inlineTask =
                new Javascript(
                        "task_name",
                        "function e() { if ($.value == 1){return {\"result\": true}} else { return {\"result\": false}}} e();");
        inlineTask.validate();

        Map<String, Object> map = new HashMap<>();
        map.put("key11", "value11");
        map.put("key12", 100);

        inlineTask.input("key1", "value");
        inlineTask.input("key2", 42);
        inlineTask.input("key3", true);
        inlineTask.input("key4", map);

        WorkflowTask workflowTask = inlineTask.getWorkflowDefTasks().get(0);

        Task fromWorkflowTask = TaskRegistry.getTask(workflowTask);
        assertTrue(
                fromWorkflowTask instanceof Javascript, "Found the instance " + fromWorkflowTask);
        Javascript taskFromWorkflowTask = (Javascript) fromWorkflowTask;

        assertNotNull(fromWorkflowTask);
        assertEquals(inlineTask.getName(), fromWorkflowTask.getName());
        assertEquals(inlineTask.getTaskReferenceName(), fromWorkflowTask.getTaskReferenceName());
        assertEquals(inlineTask.getExpression(), taskFromWorkflowTask.getExpression());
        assertEquals(inlineTask.getType(), taskFromWorkflowTask.getType());
        assertEquals(inlineTask.getInput(), taskFromWorkflowTask.getInput());
    }

    @Test
    public void testJavascriptValidation() {
        //Validation is using Nashorn engine which was removed in Java 15: https://openjdk.org/jeps/372
        // Skipping it if version is greater than 11
        var version = Runtime.version();
        if (version.feature() > 11) {
            //FIXME
            System.err.println("WARNING: skipping testJavascriptValidation because Nashorn is not supported");
            return;
        }

        // This script has syntax errors "==>"
        Javascript inlineTask = new Javascript(
                "task_name",
                "function e() { if ($.value ==> 1){return {\"result\": true}} else { return {\"result\": false}}} e();");
        assertThrows(ValidationError.class, inlineTask::validate);

        // This script does NOT have errors
        inlineTask = new Javascript(
                "task_name",
                "function e() { if ($.value == 1){return {\"result\": true}} else { return {\"result\": false}}} e();");
        var result = inlineTask.validate();
        assertNotNull(result);
    }

}
