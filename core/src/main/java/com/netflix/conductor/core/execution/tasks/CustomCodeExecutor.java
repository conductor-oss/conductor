package com.netflix.conductor.core.execution.tasks;

import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_CUSTOM_CODE;
import static com.netflix.conductor.model.TaskModel.Status.*;

@Component(TASK_TYPE_CUSTOM_CODE)
public class CustomCodeExecutor extends WorkflowSystemTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomCodeExecutor.class);
    public static final String SCRIPT_INPUT = "script";
    public static final String PARAMS_INPUT = "params";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public CustomCodeExecutor() {
        super(TASK_TYPE_CUSTOM_CODE);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {

        if (!task.getStatus().equals(TaskModel.Status.IN_PROGRESS)) {
            return false;
        }
        try {
            String script = (String) task.getInputData().get(SCRIPT_INPUT);
            Object params = task.getInputData().get(PARAMS_INPUT);

            String result = executePythonScript(script, params);
            Object outputValue = parseOutputAsJson(result);
            task.addOutput("result", outputValue);
            task.setStatus(COMPLETED);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error executing python script: {}", e.getMessage(), e);
            task.setStatus(FAILED);
            task.setReasonForIncompletion(e.getMessage());
            return true;
        }
    }

    private Object parseOutputAsJson(String output) {
        Object outputValue = output;
        try {
            String trimmed = output.trim();
            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {

                if (trimmed.startsWith("[")) {
                    List<Object> parsed =
                            MAPPER.readValue(trimmed, new TypeReference<List<Object>>() {});
                    outputValue = parsed;
                } else {
                    Map<String, Object> parsed =
                            MAPPER.readValue(trimmed, new TypeReference<Map<String, Object>>() {});
                    outputValue = parsed;
                }
            } else {
                // Try parsing as JSON value (number, boolean, null, or quoted string)
                // This will parse:
                // - Numbers (123, 45.67) → Integer/Long/Double
                // - Booleans (true, false) → Boolean
                // - null → null
                // - Quoted strings ("hello") → String (unquoted)
                // - Unquoted strings (hello) → JSON parsing fails, stays as string
                Object parsed = MAPPER.readValue(trimmed, Object.class);
                outputValue = parsed;
            }
        } catch (Exception jsonEx) {
            LOGGER.debug("Output is not valid JSON, storing as string: {}", jsonEx.getMessage());
        }
        return outputValue;
    }

    private String executePythonScript(String script, Object params) {
        try (Context context =
                     Context.newBuilder("python")
                             .allowAllAccess(true)
                             .option("engine.WarnInterpreterOnly", "false")
                             .build()) {

            // Enable output capturing
            context.eval(
                    "python",
                    """
            import sys
            import io
            sys.stdout = io.StringIO()
            """);

            // Bind params using JSON to ensure native Python types (expects a Map)
            try {
                Value bindings = context.getBindings("python");
                if (params instanceof Map) {
                    String paramsJson = MAPPER.writeValueAsString(params);
                    bindings.putMember("_params_json", paramsJson);
                    context.eval(
                            "python",
                            """
                    import json
                    params = json.loads(_params_json)
                    del _params_json

                    # Bind params keys to globals with safety check
                    import builtins
                    builtin_names = dir(builtins)
                    for key, value in params.items():
                        if key not in builtin_names and not key.startswith('_'):
                            globals()[key] = value
                        else:
                            print(f"Warning: Skipping parameter '{key}' to prevent shadowing built-in or protected name")
                    """);
                } else if (params != null) {
                    LOGGER.warn(
                            "Expected params to be a Map, got: {}", params.getClass().getName());
                }
            } catch (Exception bindEx) {
                LOGGER.warn("Unable to bind params into Python context: {}", bindEx.getMessage());
            }

            // Execute the script
            context.eval("python", script);

            // Get the captured output
            Value stdout = context.eval("python", "sys.stdout.getvalue()");
            String output = stdout.asString();

            if (output.isEmpty()) {
                // If no output from print statements, try getting the result directly
                Value result = context.eval("python", script);
                if (result.hasArrayElements() || result.hasMembers()) {
                    return result.toString();
                }
            }

            return output.trim();
        } catch (Exception e) {
            LOGGER.error("Error executing Python script: {}", e.getMessage(), e);
            throw new RuntimeException("Python script execution failed: " + e.getMessage());
        }
    }

    @Override
    public void cancel(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        task.setStatus(CANCELED);
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}