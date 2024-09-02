/*
 * Copyright 2024 Orkes, Inc.
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
package io.orkes.conductor.sdk.examples.workflowops.workflowdef;

import java.time.Duration;
import java.util.List;

import com.netflix.conductor.sdk.workflow.def.ConductorWorkflow;
import com.netflix.conductor.sdk.workflow.def.tasks.Http;
import com.netflix.conductor.sdk.workflow.def.tasks.JQ;
import com.netflix.conductor.sdk.workflow.def.tasks.Javascript;
import com.netflix.conductor.sdk.workflow.def.tasks.SetVariable;
import com.netflix.conductor.sdk.workflow.def.tasks.SimpleTask;
import com.netflix.conductor.sdk.workflow.def.tasks.Switch;
import com.netflix.conductor.sdk.workflow.def.tasks.Wait;
import com.netflix.conductor.sdk.workflow.executor.WorkflowExecutor;

import io.orkes.conductor.sdk.examples.workflowops.Main;

import lombok.Data;

public class GreetingsWorkflow {
    private final WorkflowExecutor executor;

    public GreetingsWorkflow(WorkflowExecutor executor) {
        this.executor = executor;
    }

    public ConductorWorkflow<Input> create() {
        ConductorWorkflow<Input> workflow = new ConductorWorkflow<>(executor);
        workflow.setName("greetings");
        workflow.setVersion(1);
        //Simple Task
        SimpleTask greetingsTask = new SimpleTask("greet", "greet_ref");
        greetingsTask.input("name", "IDXC" + "${workflow.input.name}");
        workflow.add(greetingsTask);
        //JS Task
        String script = "function greetings(name){return {\"text\": \"Your email is \" + name+\"@workflow.io\",\"url\": \"https://orkes-api-tester.orkesconductor.com/api\"}}greetings(\"'${workflow.input.name}'\");";
        Javascript jstask = new Javascript("hello_script", script);
        workflow.add(jstask);
        //Wait Task
        Wait waitTask = new Wait("wait_for_1_sec", Duration.ofMillis(1000));
        workflow.add(waitTask);//workflow is an object of ConductorWorkflow<WorkflowInput>
        //Set Variable
        SetVariable setVariable = new SetVariable("set_name");
        setVariable.input("Name", "${workflow.input.name}");
        workflow.add(setVariable);
//        SubWorkflow
//        SubWorkflow subWorkflow = new SubWorkflow("persist_inDB", "insertintodb", 1);
//        subWorkflow.input("name", "{workflow.input.name}");
//        workflow.add(subWorkflow);
        //Switch Case
        Wait waitTask2 = new Wait("wait_for_2_sec", Duration.ofSeconds(2));
        Javascript jstask2 = new Javascript("hello_script2", script);
        Switch switchTask = new Switch("Version_switch", "${workflow.input.name}").switchCase("Orkes", jstask2).switchCase("Others", waitTask2);
        workflow.add(switchTask);
        //JQ Task
        JQ jqtask = new JQ("jq_task", ".persons | map({user:{email, name}})");
        jqtask.input("persons", "${workflow.input.persons}");
        workflow.add(jqtask);
        //HTTP Task
        Http httptask = new Http("HTTPtask");
        httptask.url("https://orkes-api-tester.orkesconductor.com/api");
        workflow.add(httptask);
        Wait waitTask10 = new Wait("wait_for_10_sec", Duration.ofSeconds(10));
        workflow.add(waitTask10);
        return workflow;
    }

    @Data
    public static class Input {
        private String name;
        private List<Main.Person> persons;
    }
}
