/*
 * Copyright 2024 Conductor Authors.
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
package org.conductoross.conductor.core.execution.tasks.annotated;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.conductoross.conductor.sdk.workflow.task.OutputParam;
import org.junit.Test;

import com.netflix.conductor.model.TaskModel;

import static org.junit.Assert.*;

public class TestAnnotatedMethodResultMapper {

    private final AnnotatedMethodResultMapper mapper = new AnnotatedMethodResultMapper();

    static class TestWorker {
        public void voidReturn() {}

        public Map<String, Object> mapReturn() {
            return Map.of("result", "success");
        }

        public String stringReturn() {
            return "hello";
        }

        public Integer numberReturn() {
            return 42;
        }

        public Boolean booleanReturn() {
            return true;
        }

        public List<String> listReturn() {
            return List.of("a", "b", "c");
        }

        @OutputParam("customKey")
        public String annotatedReturn() {
            return "custom value";
        }

        public TestPojo pojoReturn() {
            TestPojo pojo = new TestPojo();
            pojo.field1 = "value1";
            pojo.field2 = 123;
            return pojo;
        }
    }

    static class TestPojo {
        public String field1;
        public int field2;
    }

    @Test
    public void testVoidReturn() throws Exception {
        Method method = TestWorker.class.getMethod("voidReturn");
        TaskModel task = createTaskModel();

        mapper.applyResult(null, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertTrue(task.getOutputData().isEmpty());
    }

    @Test
    public void testMapReturn() throws Exception {
        Method method = TestWorker.class.getMethod("mapReturn");
        TaskModel task = createTaskModel();
        Map<String, Object> returnValue = Map.of("result", "success", "count", 5);

        mapper.applyResult(returnValue, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("success", task.getOutputData().get("result"));
        assertEquals(5, task.getOutputData().get("count"));
    }

    @Test
    public void testStringReturn() throws Exception {
        Method method = TestWorker.class.getMethod("stringReturn");
        TaskModel task = createTaskModel();

        mapper.applyResult("hello", task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("hello", task.getOutputData().get("result"));
    }

    @Test
    public void testNumberReturn() throws Exception {
        Method method = TestWorker.class.getMethod("numberReturn");
        TaskModel task = createTaskModel();

        mapper.applyResult(42, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(42, task.getOutputData().get("result"));
    }

    @Test
    public void testBooleanReturn() throws Exception {
        Method method = TestWorker.class.getMethod("booleanReturn");
        TaskModel task = createTaskModel();

        mapper.applyResult(true, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals(true, task.getOutputData().get("result"));
    }

    @Test
    public void testListReturn() throws Exception {
        Method method = TestWorker.class.getMethod("listReturn");
        TaskModel task = createTaskModel();
        List<String> returnValue = List.of("a", "b", "c");

        mapper.applyResult(returnValue, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) task.getOutputData().get("result");
        assertEquals(3, result.size());
        assertEquals("a", result.get(0));
    }

    @Test
    public void testAnnotatedReturn() throws Exception {
        Method method = TestWorker.class.getMethod("annotatedReturn");
        TaskModel task = createTaskModel();

        mapper.applyResult("custom value", task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("custom value", task.getOutputData().get("customKey"));
        assertFalse(task.getOutputData().containsKey("result"));
    }

    @Test
    public void testPojoReturn() throws Exception {
        Method method = TestWorker.class.getMethod("pojoReturn");
        TaskModel task = createTaskModel();
        TestPojo pojo = new TestPojo();
        pojo.field1 = "test";
        pojo.field2 = 99;

        mapper.applyResult(pojo, task, method);

        assertEquals(TaskModel.Status.COMPLETED, task.getStatus());
        assertEquals("test", task.getOutputData().get("field1"));
        assertEquals(99, task.getOutputData().get("field2"));
    }

    private TaskModel createTaskModel() {
        TaskModel task = new TaskModel();
        task.setOutputData(new HashMap<>());
        return task;
    }
}
