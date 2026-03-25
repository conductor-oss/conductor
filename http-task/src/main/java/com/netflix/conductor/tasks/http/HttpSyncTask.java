package com.netflix.conductor.tasks.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.execution.WorkflowExecutor;
import com.netflix.conductor.model.TaskModel;
import com.netflix.conductor.model.WorkflowModel;
import com.netflix.conductor.tasks.http.providers.RestTemplateProvider;

import com.fasterxml.jackson.databind.ObjectMapper;

import static com.netflix.conductor.common.metadata.tasks.TaskType.TASK_TYPE_HTTP_SYNC;

/** Synchronous HTTP Task that executes HTTP calls synchronously within the workflow engine */
@Component(TASK_TYPE_HTTP_SYNC)
public class HttpSyncTask extends HttpTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSyncTask.class);

    static final String MISSING_REQUEST =
            "Missing HTTP request. Task input MUST have a '"
                    + REQUEST_PARAMETER_NAME
                    + "' key with HttpTask.Input as value. See documentation for HttpSyncTask for required input parameters";

    @Autowired
    public HttpSyncTask(RestTemplateProvider restTemplateProvider, ObjectMapper objectMapper) {
        super(TASK_TYPE_HTTP_SYNC, restTemplateProvider, objectMapper);
    }

    @Override
    public boolean execute(WorkflowModel workflow, TaskModel task, WorkflowExecutor executor) {
        Object request = task.getInputData().get(REQUEST_PARAMETER_NAME);
        if (request == null) {
            task.setReasonForIncompletion(MISSING_REQUEST);
            task.setStatus(TaskModel.Status.FAILED);
            return true;
        }

        HttpTask.Input input = objectMapper.convertValue(request, HttpTask.Input.class);
        if (input.getUri() == null) {
            String reason =
                    "Missing HTTP URI.  See documentation for HttpSyncTask for required input parameters";
            task.setReasonForIncompletion(reason);
            task.setStatus(TaskModel.Status.FAILED);
            return true;
        }

        if (input.getMethod() == null) {
            String reason = "No HTTP method specified";
            task.setReasonForIncompletion(reason);
            task.setStatus(TaskModel.Status.FAILED);
            return true;
        }

        try {
            // Reuse the parent class's httpCall method
            HttpTask.HttpResponse response = httpCall(input);
            LOGGER.debug(
                    "Response: {}, {}, task:{}",
                    response.statusCode,
                    response.body,
                    task.getTaskId());
            if (response.statusCode > 199 && response.statusCode < 300) {
                task.setStatus(TaskModel.Status.COMPLETED);
            } else {
                if (response.body != null) {
                    task.setReasonForIncompletion(response.body.toString());
                } else {
                    task.setReasonForIncompletion("No response from the remote service");
                }
                task.setStatus(TaskModel.Status.FAILED);
            }
            //noinspection ConstantConditions
            if (response != null) {
                task.addOutput("response", response.asMap());
            }
            return true;

        } catch (Exception e) {
            LOGGER.error(
                    "Failed to invoke {} task: {} - uri: {}, vipAddress: {} in workflow: {}",
                    getTaskType(),
                    task.getTaskId(),
                    input.getUri(),
                    input.getVipAddress(),
                    task.getWorkflowInstanceId(),
                    e);
            task.setStatus(TaskModel.Status.FAILED);
            task.setReasonForIncompletion(
                    "Failed to invoke " + getTaskType() + " task due to: " + e);
            task.addOutput("response", e.toString());
            return true;
        }
    }

    @Override
    public boolean isAsync() {
        return false; // HttpSyncTask is synchronous, not async
    }
}