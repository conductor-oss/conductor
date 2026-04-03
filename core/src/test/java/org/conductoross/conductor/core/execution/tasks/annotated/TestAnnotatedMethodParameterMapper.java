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

import org.junit.Test;

import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.sdk.workflow.task.InputParam;

import static org.junit.Assert.*;

public class TestAnnotatedMethodParameterMapper {

    private final AnnotatedMethodParameterMapper mapper = new AnnotatedMethodParameterMapper();

    // Test class with various method signatures
    static class TestWorker {
        public void taskModelParameter(TaskModel task) {}

        public void mapParameter(Map<String, Object> input) {}

        public void singleInputParam(@InputParam("name") String name) {}

        public void multipleInputParams(
                @InputParam("firstName") String firstName, @InputParam("age") Integer age) {}

        public void listInputParam(@InputParam("items") List<String> items) {}

        public void pojoParameter(TestPojo pojo) {}
    }

    public static class TestPojo {
        public String field1;
        public int field2;
    }

    @Test
    public void testTaskModelParameter() throws Exception {
        Method method = TestWorker.class.getMethod("taskModelParameter", TaskModel.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("key", "value"));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertSame(task, params[0]);
    }

    @Test
    public void testMapParameter() throws Exception {
        Method method = TestWorker.class.getMethod("mapParameter", Map.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("key", "value"));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertSame(task.getInputData(), params[0]);
    }

    @Test
    public void testSingleInputParam() throws Exception {
        Method method = TestWorker.class.getMethod("singleInputParam", String.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("name", "John"));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertEquals("John", params[0]);
    }

    @Test
    public void testMultipleInputParams() throws Exception {
        Method method =
                TestWorker.class.getMethod("multipleInputParams", String.class, Integer.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("firstName", "Jane", "age", 25));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(2, params.length);
        assertEquals("Jane", params[0]);
        assertEquals(25, params[1]);
    }

    @Test
    public void testListInputParam() throws Exception {
        Method method = TestWorker.class.getMethod("listInputParam", List.class);
        TaskModel task =
                createTaskModel("workflow-id", Map.of("items", List.of("item1", "item2", "item3")));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertTrue(params[0] instanceof List);
        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) params[0];
        assertEquals(3, items.size());
        assertEquals("item1", items.get(0));
    }

    @Test
    public void testPojoParameter() throws Exception {
        Method method = TestWorker.class.getMethod("pojoParameter", TestPojo.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("field1", "value1", "field2", 42));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertTrue(params[0] instanceof TestPojo);
        TestPojo pojo = (TestPojo) params[0];
        assertEquals("value1", pojo.field1);
        assertEquals(42, pojo.field2);
    }

    @Test
    public void testInputParamWithNullValue() throws Exception {
        Method method = TestWorker.class.getMethod("singleInputParam", String.class);
        TaskModel task = createTaskModel("workflow-id", Map.of("other", "value"));

        Object[] params = mapper.mapParameters(task, method);

        assertEquals(1, params.length);
        assertNull(params[0]);
    }

    private TaskModel createTaskModel(String workflowId, Map<String, Object> inputData) {
        TaskModel task = new TaskModel();
        task.setWorkflowInstanceId(workflowId);
        task.setInputData(new HashMap<>(inputData));
        return task;
    }
}
