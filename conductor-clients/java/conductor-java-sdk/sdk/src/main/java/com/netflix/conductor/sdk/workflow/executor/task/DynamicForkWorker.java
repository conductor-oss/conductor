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
package com.netflix.conductor.sdk.workflow.executor.task;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.sdk.workflow.def.tasks.DynamicFork;
import com.netflix.conductor.sdk.workflow.def.tasks.DynamicForkInput;
import com.netflix.conductor.sdk.workflow.task.InputParam;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DynamicForkWorker implements Worker {

    private final int pollingInterval;

    private final Function<Object, DynamicForkInput> workerMethod;

    private final String name;

    private final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    public DynamicForkWorker(
            String name, Function<Object, DynamicForkInput> workerMethod, int pollingInterval) {
        this.name = name;
        this.workerMethod = workerMethod;
        this.pollingInterval = pollingInterval;
    }

    @Override
    public String getTaskDefName() {
        return name;
    }

    @Override
    public TaskResult execute(Task task) {
        TaskResult result = new TaskResult(task);
        try {

            Object parameter = getInvocationParameters(this.workerMethod, task);
            DynamicForkInput output = this.workerMethod.apply(parameter);
            result.getOutputData().put(DynamicFork.FORK_TASK_PARAM, output.getTasks());
            result.getOutputData().put(DynamicFork.FORK_TASK_INPUT_PARAM, output.getInputs());
            result.setStatus(TaskResult.Status.COMPLETED);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    @Override
    public int getPollingInterval() {
        return pollingInterval;
    }

    private Object getInvocationParameters(Function<?, DynamicForkInput> function, Task task) {
        InputParam annotation = null;
        Class<?> parameterType = null;
        for (Method method : function.getClass().getDeclaredMethods()) {
            if (method.getReturnType().equals(DynamicForkInput.class)) {
                annotation = method.getParameters()[0].getAnnotation(InputParam.class);
                parameterType = method.getParameters()[0].getType();
            }
        }

        if (parameterType.equals(Task.class)) {
            return task;
        } else if (parameterType.equals(Map.class)) {
            return task.getInputData();
        }
        if (annotation != null) {
            String name = annotation.value();
            Object value = task.getInputData().get(name);
            return objectMapper.convertValue(value, parameterType);
        }
        return objectMapper.convertValue(task.getInputData(), parameterType);
    }

    public static void main(String[] args) {
        Function<?, DynamicForkInput> fn =
                new Function<TaskDef, DynamicForkInput>() {
                    @Override
                    public DynamicForkInput apply(@InputParam("a") TaskDef s) {
                        return null;
                    }
                };

        for (Method method : fn.getClass().getDeclaredMethods()) {
            if (method.getReturnType().equals(DynamicForkInput.class)) {
                System.out.println(
                        "\n\n-->method: "
                                + method
                                + ", input: "
                                + method.getParameters()[0].getType());
                System.out.println("I take input as " + method.getParameters()[0].getType());
                InputParam annotation = method.getParameters()[0].getAnnotation(InputParam.class);
                System.out.println("I have annotation " + annotation);
            }
        }
    }
}
