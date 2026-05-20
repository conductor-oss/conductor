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
package org.conductoross.conductor.ai.tasks.mapper;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.conductoross.conductor.ai.models.LLMWorkerInput;
import org.conductoross.conductor.config.AIIntegrationEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.netflix.conductor.common.config.ObjectMapperProvider;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@Conditional(AIIntegrationEnabledCondition.class)
public abstract class AIModelTaskMapper<T extends LLMWorkerInput> implements TaskMapper {

    public static final String EMBEDDINGS = "embeddings";
    public static final String LLM_PROVIDER = "llmProvider";
    public static final String MODEL_NAME = "model";
    public static final String INDEX = "index";
    public static final String PROMPT_NAME_KEY = "promptName";
    public static final String VECTOR_DB = "vectorDB";
    protected final ObjectMapper objectMapper = new ObjectMapperProvider().getObjectMapper();

    private final String taskType;
    private final TypeReference<T> type = new TypeReference<T>() {};

    @Override
    public String getTaskType() {
        return taskType;
    }

    @Override
    public final List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        TaskModel simpleTask = getMappedTask(taskMapperContext);
        return List.of(simpleTask);
    }

    protected TaskModel getMappedTask(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {
        WorkflowTask workflowTask = taskMapperContext.getWorkflowTask();
        int retryCount = taskMapperContext.getRetryCount();
        String retriedTaskId = taskMapperContext.getRetryTaskId();
        TaskDef taskDefinition = workflowTask.getTaskDefinition();
        if (taskDefinition == null) {
            taskDefinition = new TaskDef();
        }
        TaskModel simpleTask = taskMapperContext.createTaskModel();
        simpleTask.setTaskType(workflowTask.getType());
        simpleTask.setStartDelayInSeconds(workflowTask.getStartDelay());
        simpleTask.setStatus(TaskModel.Status.SCHEDULED);
        simpleTask.setRetryCount(retryCount);
        simpleTask.setCallbackAfterSeconds(workflowTask.getStartDelay());
        simpleTask.setResponseTimeoutSeconds(taskDefinition.getResponseTimeoutSeconds());
        simpleTask.setRetriedTaskId(retriedTaskId);
        simpleTask.setRateLimitPerFrequency(taskDefinition.getRateLimitPerFrequency());
        simpleTask.setRateLimitFrequencyInSeconds(taskDefinition.getRateLimitFrequencyInSeconds());
        simpleTask.setInputData(taskMapperContext.getTaskInput());
        simpleTask.setTaskDefName(getTaskType());
        // Auto-threading of previousResponseId is DISABLED. See javadoc on
        // threadPreviousResponseId below for the failure-mode reasoning.
        // Re-enable only after the task mapper switches to TRUE delta-only
        // message construction (i.e., send ONLY new input items since the
        // prior response, not the full rebuilt history).
        // threadPreviousResponseId(taskMapperContext, simpleTask);
        return simpleTask;
    }

    /**
     * Auto-thread the OpenAI Responses API {@code previous_response_id} across iterations of the
     * same task within a workflow (typically a DoWhile loop).
     *
     * <p><b>DISABLED.</b> Currently not called from {@link #getMappedTask}. Measured behavior
     * across executions {@code 8083490c}, {@code 3d5177a8}, and {@code 9652d956}: with {@code
     * previous_response_id} set and ANY non-trivial local history resend, OpenAI's billed {@code
     * promptTokens} accumulates the carried server-side chain ON TOP OF the input we send. The
     * chars-per-token ratio in {@code 9652d956} collapsed from 7.40 at iter 0 to 0.41 at iter 16
     * (impossible from JSON content alone — BPE floor is ~2.5 chars/tok). At iter 17 we sent 108K
     * chars (≤ 50K tokens of content) and OpenAI billed over 400K tokens, rejecting the call.
     * Selectively suppressing the prior assistant message (the obvious "skip the duplicate" trick)
     * only slows the accumulation — every other piece of history we still resend gets billed
     * against the carried chain.
     *
     * <p>Valid modes for the Responses API:
     *
     * <ol>
     *   <li><b>Stateless (mode A):</b> send the full conversation, do NOT set {@code
     *       previous_response_id}. Each turn bills only for what we send. Reasoning state is
     *       re-derived each turn. <i>This is the mode currently in use.</i>
     *   <li><b>Delta (mode B):</b> send only the strict delta since the prior response (only the
     *       new tool outputs / user inputs added by this iteration, nothing else), AND set {@code
     *       previous_response_id}. The carried chain provides everything else. Reasoning state is
     *       preserved. Requires the task mapper to track exactly what was in the prior request — a
     *       non-trivial change to the history rebuild path.
     * </ol>
     *
     * <p>This method implements the wire-up for mode B but is disabled because the history rebuild
     * is still mode-A shaped. Re-enable only AFTER the history rebuild emits only the delta. Until
     * then, leaving it disabled keeps {@code promptTokens} bounded to what our estimator predicts,
     * which is what {@code condenseIfNeeded} needs to trigger on time.
     */
    @SuppressWarnings("unused")
    private void threadPreviousResponseId(TaskMapperContext context, TaskModel current) {
        WorkflowModel workflow = context.getWorkflowModel();
        if (workflow == null || workflow.getTasks() == null) {
            return;
        }
        Map<String, Object> currentInput = current.getInputData();
        if (currentInput == null) {
            return;
        }
        Object explicit = currentInput.get("previousResponseId");
        if (explicit != null && !explicit.toString().isEmpty()) {
            return;
        }
        String currentRef = context.getWorkflowTask().getTaskReferenceName();
        if (currentRef == null) {
            return;
        }
        // ListIterator from the tail — O(K) where K is the position of the
        // most-recent matching prior task counted from the end, instead of the
        // O(N²) we'd get from index-based reverse access on a LinkedList.
        List<TaskModel> tasks = workflow.getTasks();
        ListIterator<TaskModel> it = tasks.listIterator(tasks.size());
        while (it.hasPrevious()) {
            TaskModel prior = it.previous();
            if (prior == current) {
                continue;
            }
            if (!prior.getStatus().isTerminal()) {
                continue;
            }
            WorkflowTask priorDef = prior.getWorkflowTask();
            if (priorDef == null || !currentRef.equals(priorDef.getTaskReferenceName())) {
                continue;
            }
            Map<String, Object> output = prior.getOutputData();
            if (output == null) {
                continue;
            }
            Object respId = output.get("responseId");
            if (respId != null && !respId.toString().isEmpty()) {
                currentInput.put("previousResponseId", respId.toString());
                return;
            }
        }
    }
}
