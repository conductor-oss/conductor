package com.netflix.conductor.contribs.script;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.execution.WorkflowExecutor;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ScriptTaskTest {

    private Workflow workflow = new Workflow();
    private WorkflowExecutor executor = mock(WorkflowExecutor.class);


    @Test
    public void start() throws Exception {
        ScriptTask scriptTask = new ScriptTask();

        Map inputObj = new HashMap();
        inputObj.put("a",1);

        Task task = new Task();
        task.getInputData().put("input", inputObj);
        task.getInputData().put("scriptExpression", "if ($.input.a==1){return {a:true}}else{return {a:false} } ");

        scriptTask.start(workflow, task, executor);

        assertEquals(Task.Status.COMPLETED, task.getStatus());
    }

}