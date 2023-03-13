/*
 * Copyright 2022 Netflix, Inc.
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
package com.netflix.conductor.sdk.workflow.executor.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.netflix.conductor.client.automator.TaskRunnerConfigurer;
import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.sdk.workflow.task.InputParam;
import com.netflix.conductor.sdk.workflow.task.OutputParam;
import com.netflix.conductor.sdk.workflow.task.WorkerTask;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class AnnotatedWorkerTests {

    static class Car {
        String brand;

        String getBrand() {
            return brand;
        }

        void setBrand(String brand) {
            this.brand = brand;
        }
    }

    static class Bike {
        String brand;

        String getBrand() {
            return brand;
        }

        void setBrand(String brand) {
            this.brand = brand;
        }
    }

    static class CarWorker {
        @WorkerTask("test_1")
        public @OutputParam("result") List<Car> doWork(@InputParam("input") List<Car> input) {
            return input;
        }
    }

    @Test
    @DisplayName("it should handle null values when InputParam is a List")
    void nullListAsInputParam() throws NoSuchMethodException {
        var worker = new CarWorker();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", List.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();
        assertNull(outputData.get("result"));
    }

    @Test
    @DisplayName("it should handle an empty List as InputParam")
    void emptyListAsInputParam() throws NoSuchMethodException {
        var worker = new CarWorker();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", List.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("input", List.of()));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();

        @SuppressWarnings("unchecked")
        List<Car> result = (List<Car>) outputData.get("result");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("it should handle a non empty List as InputParam")
    void nonEmptyListAsInputParam() throws NoSuchMethodException {
        var worker = new CarWorker();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", List.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("input", List.of(Map.of("brand", "BMW"))));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();

        @SuppressWarnings("unchecked")
        List<Car> result = (List<Car>) outputData.get("result");
        assertEquals(1, result.size());

        Car car = result.get(0);
        assertEquals("BMW", car.getBrand());
    }

    @SuppressWarnings("rawtypes")
    static class RawListInput {
        @WorkerTask("test_1")
        public @OutputParam("result") List doWork(@InputParam("input") List input) {
            return input;
        }
    }

    @Test
    @DisplayName("it should handle a Raw List Type as InputParam")
    void rawListAsInputParam() throws NoSuchMethodException {
        var worker = new RawListInput();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", List.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("input", List.of(Map.of("brand", "BMW"))));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();
        assertEquals(task.getInputData().get("input"), outputData.get("result"));
    }

    static class MapInput {
        @WorkerTask("test_1")
        public @OutputParam("result") Map<String, Object> doWork(Map<String, Object> input) {
            return input;
        }
    }

    @Test
    @DisplayName("it should accept a not annotated Map as input")
    void mapAsInputParam() throws NoSuchMethodException {
        var worker = new MapInput();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", Map.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setInputData(Map.of("input", List.of(Map.of("brand", "BMW"))));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();
        assertEquals(task.getInputData(), outputData.get("result"));
    }

    static class TaskInput {
        @WorkerTask("test_1")
        public @OutputParam("result") Task doWork(Task input) {
            return input;
        }
    }

    @Test
    @DisplayName("it should accept a Task as input")
    void taskAsInputParam() throws NoSuchMethodException {
        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskId(UUID.randomUUID().toString());
        task.setInputData(Map.of("input", List.of(Map.of("brand", "BMW"))));

        var worker = new TaskInput();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", Task.class), worker);

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();
        var result = (Task) outputData.get("result");
        assertEquals(result.getTaskId(), task.getTaskId());
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface AnotherAnnotation {}

    static class AnotherAnnotationInput {
        @WorkerTask("test_2")
        public @OutputParam("result") Bike doWork(@AnotherAnnotation Bike input) {
            return input;
        }
    }

    @Test
    @DisplayName(
            "it should convert to the correct type even if there's no @InputParam and parameters are annotated with other annotations")
    void annotatedWithAnotherAnnotation() throws NoSuchMethodException {
        var worker = new AnotherAnnotationInput();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1", worker.getClass().getMethod("doWork", Bike.class), worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskId(UUID.randomUUID().toString());
        task.setInputData(Map.of("brand", "Trek"));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();
        var bike = (Bike) outputData.get("result");
        assertEquals("Trek", bike.getBrand());
    }

    static class MultipleInputParams {
        @WorkerTask(value = "test_1", threadCount = 3, pollingInterval = 333)
        public Map<String, Object> doWork(
                @InputParam("bike") Bike bike, @InputParam("car") Car car) {
            return Map.of("bike", bike, "car", car);
        }
    }

    @Test
    @DisplayName("it should handle multiple input params")
    void multipleInputParams() throws NoSuchMethodException {
        var worker = new MultipleInputParams();
        var annotatedWorker =
                new AnnotatedWorker(
                        "test_1",
                        worker.getClass().getMethod("doWork", Bike.class, Car.class),
                        worker);

        var task = new Task();
        task.setStatus(Task.Status.IN_PROGRESS);
        task.setTaskId(UUID.randomUUID().toString());
        task.setInputData(Map.of("bike", Map.of("brand", "Trek"), "car", Map.of("brand", "BMW")));

        var result0 = annotatedWorker.execute(task);
        var outputData = result0.getOutputData();

        var bike = (Bike) outputData.get("bike");
        assertEquals("Trek", bike.getBrand());

        var car = (Car) outputData.get("car");
        assertEquals("BMW", car.getBrand());
    }

    @Test
    @DisplayName("it should honor the polling interval from annotations and config")
    void pollingIntervalTest() throws NoSuchMethodException {
        var config = new TestWorkerConfig();

        var worker = new MultipleInputParams();

        AnnotatedWorkerExecutor annotatedWorkerExecutor =
                new AnnotatedWorkerExecutor(mock(TaskClient.class));
        annotatedWorkerExecutor.addBean(worker);
        annotatedWorkerExecutor.startPolling();
        List<Worker> workers = annotatedWorkerExecutor.getExecutors();
        assertNotNull(workers);
        assertEquals(1, workers.size());
        Worker taskWorker = workers.get(0);
        assertEquals(333, taskWorker.getPollingInterval());

        var worker2 = new AnotherAnnotationInput();
        annotatedWorkerExecutor = new AnnotatedWorkerExecutor(mock(TaskClient.class));
        annotatedWorkerExecutor.addBean(worker2);
        annotatedWorkerExecutor.startPolling();
        workers = annotatedWorkerExecutor.getExecutors();
        assertNotNull(workers);
        assertEquals(1, workers.size());
        taskWorker = workers.get(0);
        assertEquals(100, taskWorker.getPollingInterval());

        config.setPollingInterval("test_2", 123);
        annotatedWorkerExecutor = new AnnotatedWorkerExecutor(mock(TaskClient.class), config);
        annotatedWorkerExecutor.addBean(worker2);
        annotatedWorkerExecutor.startPolling();
        workers = annotatedWorkerExecutor.getExecutors();
        assertNotNull(workers);
        assertEquals(1, workers.size());
        taskWorker = workers.get(0);
        assertEquals(123, taskWorker.getPollingInterval());
    }

    @Test
    @DisplayName("it should honor the polling interval from annotations and config")
    void threadCountTest() throws NoSuchMethodException {
        var config = new TestWorkerConfig();

        var worker = new MultipleInputParams();
        var worker2 = new AnotherAnnotationInput();

        AnnotatedWorkerExecutor annotatedWorkerExecutor =
                new AnnotatedWorkerExecutor(mock(TaskClient.class), config);
        annotatedWorkerExecutor.addBean(worker);
        annotatedWorkerExecutor.addBean(worker2);

        annotatedWorkerExecutor.startPolling();
        TaskRunnerConfigurer runner = annotatedWorkerExecutor.getTaskRunner();
        assertNotNull(runner);
        Map<String, Integer> taskThreadCount = runner.getTaskThreadCount();

        assertNotNull(taskThreadCount);
        assertEquals(3, taskThreadCount.get("test_1"));
        assertEquals(1, taskThreadCount.get("test_2"));

        annotatedWorkerExecutor.shutdown();
        config.setThreadCount("test_2", 2);
        annotatedWorkerExecutor = new AnnotatedWorkerExecutor(mock(TaskClient.class), config);
        annotatedWorkerExecutor.addBean(worker);
        annotatedWorkerExecutor.addBean(worker2);

        annotatedWorkerExecutor.startPolling();
        runner = annotatedWorkerExecutor.getTaskRunner();

        taskThreadCount = runner.getTaskThreadCount();

        assertNotNull(taskThreadCount);
        assertEquals(3, taskThreadCount.get("test_1"));
        assertEquals(2, taskThreadCount.get("test_2"));
    }
}
