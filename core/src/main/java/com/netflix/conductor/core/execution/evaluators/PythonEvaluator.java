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
package com.netflix.conductor.core.execution.evaluators;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.script.ScriptException;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.events.ScriptEvaluator;
import com.netflix.conductor.core.exception.TerminateWorkflowException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

@Component(PythonEvaluator.NAME)
public class PythonEvaluator implements Evaluator {

    public static final String NAME = "python";
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonEvaluator.class);
    private static final PythonInterpreter pythonInterpreter = new PythonInterpreter();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern pattern = Pattern.compile("\\$\\.([a-zA-Z0-9_\\.\\[\\]]+)");

    @Override
    public Object evaluate(String script, Object input) {
        LOGGER.debug("Python evaluator -- script: {}", script);
        try {
            script = script.trim();
            Map<String, Object> inputs = (Map<String, Object>) input;
            script = replaceVariablesInScript(script, inputs);
            String untrustedCodeValidatorOutput = getUntrustedCodeValidatorOutput(script);
            if (untrustedCodeValidatorOutput.startsWith("Error : ")) {
                throw new ScriptException(
                        "Script execution is restricted due to policy violations : "
                                + untrustedCodeValidatorOutput);
            }
            Object result = ScriptEvaluator.eval(script, input);
            LOGGER.debug("Python evaluator -- result: {}", result);
            return result;
        } catch (Exception e) {
            LOGGER.error("Error while evaluating script: {}", script, e);
            throw new TerminateWorkflowException(e.getMessage());
        }
    }

    private static String getUntrustedCodeValidatorOutput(String script) {
        try (InputStream inputStream =
                        PythonEvaluator.class
                                .getClassLoader()
                                .getResourceAsStream("python/untrusted_code_validator.py");
                InputStreamReader isr = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader br = new BufferedReader(isr)) {
            if (inputStream == null) {
                throw new FileNotFoundException(
                        String.format(
                                "Resource file %s not found.", "untrusted_code_validator.py"));
            }
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                stringBuilder.append(line).append("\n");
            }
            String untrustedCode = "'''" + script + "'''";
            String pythonScript = stringBuilder.toString().replace("${code}", untrustedCode);
            pythonInterpreter.exec(pythonScript);
            PyObject result = pythonInterpreter.get("codeTrusted");
            return result.toString();
        } catch (Exception e) {
            LOGGER.error(
                    "Some error encountered running getUntrustedCodeValidatorOutput as : {}", e);
            throw new TerminateWorkflowException(e.getMessage());
        }
    }

    private String replaceVariablesInScript(String script, Map<String, Object> inputs)
            throws IOException {
        String inputJsonString = objectMapper.writeValueAsString(inputs);
        DocumentContext jsonContext = JsonPath.parse(inputJsonString);

        // Function to handle nested replacements
        script = processNestedReplacements(script, jsonContext, pattern);

        return script;
    }

    /**
     * Replace nested expressions like $.$.$.variable
     *
     * @param script
     * @param jsonContext
     * @param pattern
     * @return
     */
    private String processNestedReplacements(
            String script, DocumentContext jsonContext, Pattern pattern) {
        boolean found = true;

        while (found) {
            found = false;
            Matcher matcher = pattern.matcher(script);
            StringBuffer updatedScript = new StringBuffer();

            while (matcher.find()) {
                found = true;
                String jsonPath = matcher.group(1);
                try {
                    Object value = jsonContext.read("$." + jsonPath);
                    // Create the replacement string for the variable
                    String replacement = value != null ? value.toString() : "";
                    // Escape $ to avoid issues in replacement string
                    String safeReplacement = replacement.replace("$", "\\$");
                    // Append the new script with the replaced variable
                    matcher.appendReplacement(updatedScript, safeReplacement);
                } catch (Exception e) {
                    matcher.appendReplacement(updatedScript, "\\$." + jsonPath);
                    found = false;
                    break;
                }
            }
            // Append the remaining part of the script after the last match
            matcher.appendTail(updatedScript);
            script = updatedScript.toString();
        }
        return script;
    }
}
