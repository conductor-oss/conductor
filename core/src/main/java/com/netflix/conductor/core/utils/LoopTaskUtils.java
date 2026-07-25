/*
 * Copyright 2026 Conductor Authors.
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
package com.netflix.conductor.core.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.conductor.model.TaskModel;

public final class LoopTaskUtils {

    private static final Pattern ITERATION_SUFFIX_CHAIN = Pattern.compile("(__\\d+)+$");
    private static final Pattern LAST_ITERATION = Pattern.compile("__(\\d+)$");

    private LoopTaskUtils() {}

    public static String removeIterationSuffixChain(String referenceTaskName) {
        return ITERATION_SUFFIX_CHAIN.matcher(referenceTaskName).replaceAll("");
    }

    /**
     * Returns the runtime iteration suffixes relative to the definition-time reference name. A
     * nested task has one {@code __<iteration>} suffix for each enclosing DO_WHILE.
     */
    public static String getIterationSuffixChain(TaskModel task) {
        String runtimeRefName = task.getReferenceTaskName();
        if (task.getWorkflowTask() != null) {
            String definitionRefName = task.getWorkflowTask().getTaskReferenceName();
            if (definitionRefName != null && runtimeRefName.startsWith(definitionRefName)) {
                String chain = runtimeRefName.substring(definitionRefName.length());
                if (ITERATION_SUFFIX_CHAIN.matcher(chain).matches()) {
                    return chain;
                }
                if (chain.isEmpty()
                        && task.getIteration() > 0
                        && !"DO_WHILE".equals(task.getTaskType())) {
                    return "__" + task.getIteration();
                }
                if (chain.isEmpty()) {
                    return "";
                }
            }
        }
        if (!task.isLoopOverTask()) {
            return "";
        }
        Matcher matcher = ITERATION_SUFFIX_CHAIN.matcher(runtimeRefName);
        if (matcher.find()) {
            return matcher.group();
        }
        if (task.getIteration() > 0 && !"DO_WHILE".equals(task.getTaskType())) {
            return "__" + task.getIteration();
        }
        return "";
    }

    public static int getIterationFromSuffixChain(String suffixChain) {
        Matcher matcher = LAST_ITERATION.matcher(suffixChain);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }
}
