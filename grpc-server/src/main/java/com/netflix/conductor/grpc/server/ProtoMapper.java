package com.netflix.conductor.grpc.server;

import com.google.protobuf.Value;
import com.netflix.conductor.common.metadata.events.EventExecution;
import com.netflix.conductor.common.metadata.events.EventHandler;
import com.netflix.conductor.common.metadata.tasks.PollData;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskDef;
import com.netflix.conductor.common.metadata.tasks.TaskExecLog;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask;
import com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList;
import com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SkipTaskRequest;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.metadata.workflow.SubWorkflowParams;
import com.netflix.conductor.common.metadata.workflow.WorkflowDef;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.common.run.TaskSummary;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.common.run.WorkflowSummary;
import com.netflix.conductor.proto.DynamicForkJoinTaskListPb;
import com.netflix.conductor.proto.DynamicForkJoinTaskPb;
import com.netflix.conductor.proto.EventExecutionPb;
import com.netflix.conductor.proto.EventHandlerPb;
import com.netflix.conductor.proto.PollDataPb;
import com.netflix.conductor.proto.RerunWorkflowRequestPb;
import com.netflix.conductor.proto.SkipTaskRequestPb;
import com.netflix.conductor.proto.StartWorkflowRequestPb;
import com.netflix.conductor.proto.SubWorkflowParamsPb;
import com.netflix.conductor.proto.TaskDefPb;
import com.netflix.conductor.proto.TaskExecLogPb;
import com.netflix.conductor.proto.TaskPb;
import com.netflix.conductor.proto.TaskResultPb;
import com.netflix.conductor.proto.TaskSummaryPb;
import com.netflix.conductor.proto.WorkflowDefPb;
import com.netflix.conductor.proto.WorkflowPb;
import com.netflix.conductor.proto.WorkflowSummaryPb;
import com.netflix.conductor.proto.WorkflowTaskPb;
import java.lang.IllegalArgumentException;
import java.lang.Object;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Generated;

@Generated("com.netflix.conductor.protogen.ProtoGen")
public final class ProtoMapper extends ProtoMapperBase {
    public static EventExecutionPb.EventExecution toProto(EventExecution from) {
        EventExecutionPb.EventExecution.Builder to = EventExecutionPb.EventExecution.newBuilder();
        to.setId( from.getId() );
        to.setMessageId( from.getMessageId() );
        to.setName( from.getName() );
        to.setEvent( from.getEvent() );
        to.setCreated( from.getCreated() );
        to.setStatus( toProto( from.getStatus() ) );
        to.setAction( toProto( from.getAction() ) );
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public static EventExecution fromProto(EventExecutionPb.EventExecution from) {
        EventExecution to = new EventExecution();
        to.setId( from.getId() );
        to.setMessageId( from.getMessageId() );
        to.setName( from.getName() );
        to.setEvent( from.getEvent() );
        to.setCreated( from.getCreated() );
        to.setStatus( fromProto( from.getStatus() ) );
        to.setAction( fromProto( from.getAction() ) );
        Map<String, Object> outputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputMap().entrySet()) {
            outputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutput(outputMap);
        return to;
    }

    public static EventExecutionPb.EventExecution.Status toProto(EventExecution.Status from) {
        EventExecutionPb.EventExecution.Status to;
        switch (from) {
            case IN_PROGRESS: to = EventExecutionPb.EventExecution.Status.IN_PROGRESS; break;
            case COMPLETED: to = EventExecutionPb.EventExecution.Status.COMPLETED; break;
            case FAILED: to = EventExecutionPb.EventExecution.Status.FAILED; break;
            case SKIPPED: to = EventExecutionPb.EventExecution.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static EventExecution.Status fromProto(EventExecutionPb.EventExecution.Status from) {
        EventExecution.Status to;
        switch (from) {
            case IN_PROGRESS: to = EventExecution.Status.IN_PROGRESS; break;
            case COMPLETED: to = EventExecution.Status.COMPLETED; break;
            case FAILED: to = EventExecution.Status.FAILED; break;
            case SKIPPED: to = EventExecution.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static EventHandlerPb.EventHandler toProto(EventHandler from) {
        EventHandlerPb.EventHandler.Builder to = EventHandlerPb.EventHandler.newBuilder();
        to.setName( from.getName() );
        to.setEvent( from.getEvent() );
        to.setCondition( from.getCondition() );
        for (EventHandler.Action elem : from.getActions()) {
            to.addActions( toProto(elem) );
        }
        to.setActive( from.isActive() );
        return to.build();
    }

    public static EventHandler fromProto(EventHandlerPb.EventHandler from) {
        EventHandler to = new EventHandler();
        to.setName( from.getName() );
        to.setEvent( from.getEvent() );
        to.setCondition( from.getCondition() );
        to.setActions( from.getActionsList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setActive( from.getActive() );
        return to;
    }

    public static EventHandlerPb.EventHandler.StartWorkflow toProto(
            EventHandler.StartWorkflow from) {
        EventHandlerPb.EventHandler.StartWorkflow.Builder to = EventHandlerPb.EventHandler.StartWorkflow.newBuilder();
        to.setName( from.getName() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public static EventHandler.StartWorkflow fromProto(
            EventHandlerPb.EventHandler.StartWorkflow from) {
        EventHandler.StartWorkflow to = new EventHandler.StartWorkflow();
        to.setName( from.getName() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        Map<String, Object> inputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputMap().entrySet()) {
            inputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInput(inputMap);
        return to;
    }

    public static EventHandlerPb.EventHandler.TaskDetails toProto(EventHandler.TaskDetails from) {
        EventHandlerPb.EventHandler.TaskDetails.Builder to = EventHandlerPb.EventHandler.TaskDetails.newBuilder();
        to.setWorkflowId( from.getWorkflowId() );
        to.setTaskRefName( from.getTaskRefName() );
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public static EventHandler.TaskDetails fromProto(EventHandlerPb.EventHandler.TaskDetails from) {
        EventHandler.TaskDetails to = new EventHandler.TaskDetails();
        to.setWorkflowId( from.getWorkflowId() );
        to.setTaskRefName( from.getTaskRefName() );
        Map<String, Object> outputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputMap().entrySet()) {
            outputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutput(outputMap);
        return to;
    }

    public static EventHandlerPb.EventHandler.Action toProto(EventHandler.Action from) {
        EventHandlerPb.EventHandler.Action.Builder to = EventHandlerPb.EventHandler.Action.newBuilder();
        to.setAction( toProto( from.getAction() ) );
        to.setStartWorkflow( toProto( from.getStartWorkflow() ) );
        to.setCompleteTask( toProto( from.getCompleteTask() ) );
        to.setFailTask( toProto( from.getFailTask() ) );
        to.setExpandInlineJson( from.isExpandInlineJson() );
        return to.build();
    }

    public static EventHandler.Action fromProto(EventHandlerPb.EventHandler.Action from) {
        EventHandler.Action to = new EventHandler.Action();
        to.setAction( fromProto( from.getAction() ) );
        to.setStartWorkflow( fromProto( from.getStartWorkflow() ) );
        to.setCompleteTask( fromProto( from.getCompleteTask() ) );
        to.setFailTask( fromProto( from.getFailTask() ) );
        to.setExpandInlineJson( from.getExpandInlineJson() );
        return to;
    }

    public static EventHandlerPb.EventHandler.Action.Type toProto(EventHandler.Action.Type from) {
        EventHandlerPb.EventHandler.Action.Type to;
        switch (from) {
            case START_WORKFLOW: to = EventHandlerPb.EventHandler.Action.Type.START_WORKFLOW; break;
            case COMPLETE_TASK: to = EventHandlerPb.EventHandler.Action.Type.COMPLETE_TASK; break;
            case FAIL_TASK: to = EventHandlerPb.EventHandler.Action.Type.FAIL_TASK; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static EventHandler.Action.Type fromProto(EventHandlerPb.EventHandler.Action.Type from) {
        EventHandler.Action.Type to;
        switch (from) {
            case START_WORKFLOW: to = EventHandler.Action.Type.START_WORKFLOW; break;
            case COMPLETE_TASK: to = EventHandler.Action.Type.COMPLETE_TASK; break;
            case FAIL_TASK: to = EventHandler.Action.Type.FAIL_TASK; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static PollDataPb.PollData toProto(PollData from) {
        PollDataPb.PollData.Builder to = PollDataPb.PollData.newBuilder();
        to.setQueueName( from.getQueueName() );
        to.setDomain( from.getDomain() );
        to.setWorkerId( from.getWorkerId() );
        to.setLastPollTime( from.getLastPollTime() );
        return to.build();
    }

    public static PollData fromProto(PollDataPb.PollData from) {
        PollData to = new PollData();
        to.setQueueName( from.getQueueName() );
        to.setDomain( from.getDomain() );
        to.setWorkerId( from.getWorkerId() );
        to.setLastPollTime( from.getLastPollTime() );
        return to;
    }

    public static TaskPb.Task toProto(Task from) {
        TaskPb.Task.Builder to = TaskPb.Task.newBuilder();
        to.setTaskType( from.getTaskType() );
        to.setStatus( toProto( from.getStatus() ) );
        for (Map.Entry<String, Object> pair : from.getInputData().entrySet()) {
            to.putInputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setReferenceTaskName( from.getReferenceTaskName() );
        to.setRetryCount( from.getRetryCount() );
        to.setSeq( from.getSeq() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setPollCount( from.getPollCount() );
        to.setTaskDefName( from.getTaskDefName() );
        to.setScheduledTime( from.getScheduledTime() );
        to.setStartTime( from.getStartTime() );
        to.setEndTime( from.getEndTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setStartDelayInSeconds( from.getStartDelayInSeconds() );
        to.setRetriedTaskId( from.getRetriedTaskId() );
        to.setRetried( from.isRetried() );
        to.setCallbackFromWorker( from.isCallbackFromWorker() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        to.setWorkflowType( from.getWorkflowType() );
        to.setTaskId( from.getTaskId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        to.setWorkerId( from.getWorkerId() );
        for (Map.Entry<String, Object> pair : from.getOutputData().entrySet()) {
            to.putOutputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setWorkflowTask( toProto( from.getWorkflowTask() ) );
        to.setDomain( from.getDomain() );
        return to.build();
    }

    public static Task fromProto(TaskPb.Task from) {
        Task to = new Task();
        to.setTaskType( from.getTaskType() );
        to.setStatus( fromProto( from.getStatus() ) );
        Map<String, Object> inputDataMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputDataMap().entrySet()) {
            inputDataMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInputData(inputDataMap);
        to.setReferenceTaskName( from.getReferenceTaskName() );
        to.setRetryCount( from.getRetryCount() );
        to.setSeq( from.getSeq() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setPollCount( from.getPollCount() );
        to.setTaskDefName( from.getTaskDefName() );
        to.setScheduledTime( from.getScheduledTime() );
        to.setStartTime( from.getStartTime() );
        to.setEndTime( from.getEndTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setStartDelayInSeconds( from.getStartDelayInSeconds() );
        to.setRetriedTaskId( from.getRetriedTaskId() );
        to.setRetried( from.getRetried() );
        to.setCallbackFromWorker( from.getCallbackFromWorker() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        to.setWorkflowType( from.getWorkflowType() );
        to.setTaskId( from.getTaskId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        to.setWorkerId( from.getWorkerId() );
        Map<String, Object> outputDataMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputDataMap().entrySet()) {
            outputDataMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutputData(outputDataMap);
        to.setWorkflowTask( fromProto( from.getWorkflowTask() ) );
        to.setDomain( from.getDomain() );
        return to;
    }

    public static TaskPb.Task.Status toProto(Task.Status from) {
        TaskPb.Task.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskPb.Task.Status.IN_PROGRESS; break;
            case CANCELED: to = TaskPb.Task.Status.CANCELED; break;
            case FAILED: to = TaskPb.Task.Status.FAILED; break;
            case COMPLETED: to = TaskPb.Task.Status.COMPLETED; break;
            case COMPLETED_WITH_ERRORS: to = TaskPb.Task.Status.COMPLETED_WITH_ERRORS; break;
            case SCHEDULED: to = TaskPb.Task.Status.SCHEDULED; break;
            case TIMED_OUT: to = TaskPb.Task.Status.TIMED_OUT; break;
            case READY_FOR_RERUN: to = TaskPb.Task.Status.READY_FOR_RERUN; break;
            case SKIPPED: to = TaskPb.Task.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static Task.Status fromProto(TaskPb.Task.Status from) {
        Task.Status to;
        switch (from) {
            case IN_PROGRESS: to = Task.Status.IN_PROGRESS; break;
            case CANCELED: to = Task.Status.CANCELED; break;
            case FAILED: to = Task.Status.FAILED; break;
            case COMPLETED: to = Task.Status.COMPLETED; break;
            case COMPLETED_WITH_ERRORS: to = Task.Status.COMPLETED_WITH_ERRORS; break;
            case SCHEDULED: to = Task.Status.SCHEDULED; break;
            case TIMED_OUT: to = Task.Status.TIMED_OUT; break;
            case READY_FOR_RERUN: to = Task.Status.READY_FOR_RERUN; break;
            case SKIPPED: to = Task.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskDefPb.TaskDef toProto(TaskDef from) {
        TaskDefPb.TaskDef.Builder to = TaskDefPb.TaskDef.newBuilder();
        to.setName( from.getName() );
        to.setDescription( from.getDescription() );
        to.setRetryCount( from.getRetryCount() );
        to.setTimeoutSeconds( from.getTimeoutSeconds() );
        to.addAllInputKeys( from.getInputKeys() );
        to.addAllOutputKeys( from.getOutputKeys() );
        to.setTimeoutPolicy( toProto( from.getTimeoutPolicy() ) );
        to.setRetryLogic( toProto( from.getRetryLogic() ) );
        to.setRetryDelaySeconds( from.getRetryDelaySeconds() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        to.setConcurrentExecLimit( from.getConcurrentExecLimit() );
        for (Map.Entry<String, Object> pair : from.getInputTemplate().entrySet()) {
            to.putInputTemplate( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public static TaskDef fromProto(TaskDefPb.TaskDef from) {
        TaskDef to = new TaskDef();
        to.setName( from.getName() );
        to.setDescription( from.getDescription() );
        to.setRetryCount( from.getRetryCount() );
        to.setTimeoutSeconds( from.getTimeoutSeconds() );
        to.setInputKeys( from.getInputKeysList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        to.setOutputKeys( from.getOutputKeysList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        to.setTimeoutPolicy( fromProto( from.getTimeoutPolicy() ) );
        to.setRetryLogic( fromProto( from.getRetryLogic() ) );
        to.setRetryDelaySeconds( from.getRetryDelaySeconds() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        to.setConcurrentExecLimit( from.getConcurrentExecLimit() );
        Map<String, Object> inputTemplateMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputTemplateMap().entrySet()) {
            inputTemplateMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInputTemplate(inputTemplateMap);
        return to;
    }

    public static TaskDefPb.TaskDef.RetryLogic toProto(TaskDef.RetryLogic from) {
        TaskDefPb.TaskDef.RetryLogic to;
        switch (from) {
            case FIXED: to = TaskDefPb.TaskDef.RetryLogic.FIXED; break;
            case EXPONENTIAL_BACKOFF: to = TaskDefPb.TaskDef.RetryLogic.EXPONENTIAL_BACKOFF; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskDef.RetryLogic fromProto(TaskDefPb.TaskDef.RetryLogic from) {
        TaskDef.RetryLogic to;
        switch (from) {
            case FIXED: to = TaskDef.RetryLogic.FIXED; break;
            case EXPONENTIAL_BACKOFF: to = TaskDef.RetryLogic.EXPONENTIAL_BACKOFF; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskDefPb.TaskDef.TimeoutPolicy toProto(TaskDef.TimeoutPolicy from) {
        TaskDefPb.TaskDef.TimeoutPolicy to;
        switch (from) {
            case RETRY: to = TaskDefPb.TaskDef.TimeoutPolicy.RETRY; break;
            case TIME_OUT_WF: to = TaskDefPb.TaskDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = TaskDefPb.TaskDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskDef.TimeoutPolicy fromProto(TaskDefPb.TaskDef.TimeoutPolicy from) {
        TaskDef.TimeoutPolicy to;
        switch (from) {
            case RETRY: to = TaskDef.TimeoutPolicy.RETRY; break;
            case TIME_OUT_WF: to = TaskDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = TaskDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskExecLogPb.TaskExecLog toProto(TaskExecLog from) {
        TaskExecLogPb.TaskExecLog.Builder to = TaskExecLogPb.TaskExecLog.newBuilder();
        to.setLog( from.getLog() );
        to.setTaskId( from.getTaskId() );
        to.setCreatedTime( from.getCreatedTime() );
        return to.build();
    }

    public static TaskExecLog fromProto(TaskExecLogPb.TaskExecLog from) {
        TaskExecLog to = new TaskExecLog();
        to.setLog( from.getLog() );
        to.setTaskId( from.getTaskId() );
        to.setCreatedTime( from.getCreatedTime() );
        return to;
    }

    public static TaskResultPb.TaskResult toProto(TaskResult from) {
        TaskResultPb.TaskResult.Builder to = TaskResultPb.TaskResult.newBuilder();
        to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        to.setTaskId( from.getTaskId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        to.setWorkerId( from.getWorkerId() );
        to.setStatus( toProto( from.getStatus() ) );
        for (Map.Entry<String, Object> pair : from.getOutputData().entrySet()) {
            to.putOutputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public static TaskResult fromProto(TaskResultPb.TaskResult from) {
        TaskResult to = new TaskResult();
        to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        to.setTaskId( from.getTaskId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        to.setWorkerId( from.getWorkerId() );
        to.setStatus( fromProto( from.getStatus() ) );
        Map<String, Object> outputDataMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputDataMap().entrySet()) {
            outputDataMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutputData(outputDataMap);
        return to;
    }

    public static TaskResultPb.TaskResult.Status toProto(TaskResult.Status from) {
        TaskResultPb.TaskResult.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskResultPb.TaskResult.Status.IN_PROGRESS; break;
            case FAILED: to = TaskResultPb.TaskResult.Status.FAILED; break;
            case COMPLETED: to = TaskResultPb.TaskResult.Status.COMPLETED; break;
            case SCHEDULED: to = TaskResultPb.TaskResult.Status.SCHEDULED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskResult.Status fromProto(TaskResultPb.TaskResult.Status from) {
        TaskResult.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskResult.Status.IN_PROGRESS; break;
            case FAILED: to = TaskResult.Status.FAILED; break;
            case COMPLETED: to = TaskResult.Status.COMPLETED; break;
            case SCHEDULED: to = TaskResult.Status.SCHEDULED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static DynamicForkJoinTaskPb.DynamicForkJoinTask toProto(DynamicForkJoinTask from) {
        DynamicForkJoinTaskPb.DynamicForkJoinTask.Builder to = DynamicForkJoinTaskPb.DynamicForkJoinTask.newBuilder();
        to.setTaskName( from.getTaskName() );
        to.setWorkflowName( from.getWorkflowName() );
        to.setReferenceName( from.getReferenceName() );
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setType( from.getType() );
        return to.build();
    }

    public static DynamicForkJoinTask fromProto(DynamicForkJoinTaskPb.DynamicForkJoinTask from) {
        DynamicForkJoinTask to = new DynamicForkJoinTask();
        to.setTaskName( from.getTaskName() );
        to.setWorkflowName( from.getWorkflowName() );
        to.setReferenceName( from.getReferenceName() );
        Map<String, Object> inputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputMap().entrySet()) {
            inputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInput(inputMap);
        to.setType( from.getType() );
        return to;
    }

    public static DynamicForkJoinTaskListPb.DynamicForkJoinTaskList toProto(
            DynamicForkJoinTaskList from) {
        DynamicForkJoinTaskListPb.DynamicForkJoinTaskList.Builder to = DynamicForkJoinTaskListPb.DynamicForkJoinTaskList.newBuilder();
        for (DynamicForkJoinTask elem : from.getDynamicTasks()) {
            to.addDynamicTasks( toProto(elem) );
        }
        return to.build();
    }

    public static DynamicForkJoinTaskList fromProto(
            DynamicForkJoinTaskListPb.DynamicForkJoinTaskList from) {
        DynamicForkJoinTaskList to = new DynamicForkJoinTaskList();
        to.setDynamicTasks( from.getDynamicTasksList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        return to;
    }

    public static RerunWorkflowRequest fromProto(RerunWorkflowRequestPb.RerunWorkflowRequest from) {
        RerunWorkflowRequest to = new RerunWorkflowRequest();
        to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        Map<String, Object> workflowInputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getWorkflowInputMap().entrySet()) {
            workflowInputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setWorkflowInput(workflowInputMap);
        to.setReRunFromTaskId( from.getReRunFromTaskId() );
        Map<String, Object> taskInputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getTaskInputMap().entrySet()) {
            taskInputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setTaskInput(taskInputMap);
        to.setCorrelationId( from.getCorrelationId() );
        return to;
    }

    public static SkipTaskRequest fromProto(SkipTaskRequestPb.SkipTaskRequest from) {
        SkipTaskRequest to = new SkipTaskRequest();
        Map<String, Object> taskInputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getTaskInputMap().entrySet()) {
            taskInputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setTaskInput(taskInputMap);
        Map<String, Object> taskOutputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getTaskOutputMap().entrySet()) {
            taskOutputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setTaskOutput(taskOutputMap);
        return to;
    }

    public static StartWorkflowRequest fromProto(StartWorkflowRequestPb.StartWorkflowRequest from) {
        StartWorkflowRequest to = new StartWorkflowRequest();
        to.setName( from.getName() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        Map<String, Object> inputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputMap().entrySet()) {
            inputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInput(inputMap);
        to.setTaskToDomain( from.getTaskToDomainMap() );
        return to;
    }

    public static SubWorkflowParamsPb.SubWorkflowParams toProto(SubWorkflowParams from) {
        SubWorkflowParamsPb.SubWorkflowParams.Builder to = SubWorkflowParamsPb.SubWorkflowParams.newBuilder();
        to.setName( from.getName() );
        to.setVersion( toProto( from.getVersion() ) );
        return to.build();
    }

    public static SubWorkflowParams fromProto(SubWorkflowParamsPb.SubWorkflowParams from) {
        SubWorkflowParams to = new SubWorkflowParams();
        to.setName( from.getName() );
        to.setVersion( fromProto( from.getVersion() ) );
        return to;
    }

    public static WorkflowDefPb.WorkflowDef toProto(WorkflowDef from) {
        WorkflowDefPb.WorkflowDef.Builder to = WorkflowDefPb.WorkflowDef.newBuilder();
        to.setName( from.getName() );
        to.setDescription( from.getDescription() );
        to.setVersion( from.getVersion() );
        for (WorkflowTask elem : from.getTasks()) {
            to.addTasks( toProto(elem) );
        }
        to.addAllInputParameters( from.getInputParameters() );
        for (Map.Entry<String, Object> pair : from.getOutputParameters().entrySet()) {
            to.putOutputParameters( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setFailureWorkflow( from.getFailureWorkflow() );
        to.setSchemaVersion( from.getSchemaVersion() );
        return to.build();
    }

    public static WorkflowDef fromProto(WorkflowDefPb.WorkflowDef from) {
        WorkflowDef to = new WorkflowDef();
        to.setName( from.getName() );
        to.setDescription( from.getDescription() );
        to.setVersion( from.getVersion() );
        to.setTasks( from.getTasksList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(LinkedList::new)) );
        to.setInputParameters( from.getInputParametersList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        Map<String, Object> outputParametersMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputParametersMap().entrySet()) {
            outputParametersMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutputParameters(outputParametersMap);
        to.setFailureWorkflow( from.getFailureWorkflow() );
        to.setSchemaVersion( from.getSchemaVersion() );
        return to;
    }

    public static WorkflowTaskPb.WorkflowTask toProto(WorkflowTask from) {
        WorkflowTaskPb.WorkflowTask.Builder to = WorkflowTaskPb.WorkflowTask.newBuilder();
        to.setName( from.getName() );
        to.setTaskReferenceName( from.getTaskReferenceName() );
        to.setDescription( from.getDescription() );
        for (Map.Entry<String, Object> pair : from.getInputParameters().entrySet()) {
            to.putInputParameters( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setType( from.getType() );
        to.setDynamicTaskNameParam( from.getDynamicTaskNameParam() );
        to.setCaseValueParam( from.getCaseValueParam() );
        to.setCaseExpression( from.getCaseExpression() );
        for (Map.Entry<String, List<WorkflowTask>> pair : from.getDecisionCases().entrySet()) {
            to.putDecisionCases( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setDynamicForkTasksParam( from.getDynamicForkTasksParam() );
        to.setDynamicForkTasksInputParamName( from.getDynamicForkTasksInputParamName() );
        for (WorkflowTask elem : from.getDefaultCase()) {
            to.addDefaultCase( toProto(elem) );
        }
        for (List<WorkflowTask> elem : from.getForkTasks()) {
            to.addForkTasks( toProto(elem) );
        }
        to.setStartDelay( from.getStartDelay() );
        to.setSubWorkflowParam( toProto( from.getSubWorkflowParam() ) );
        to.addAllJoinOn( from.getJoinOn() );
        to.setSink( from.getSink() );
        to.setOptional( from.isOptional() );
        return to.build();
    }

    public static WorkflowTask fromProto(WorkflowTaskPb.WorkflowTask from) {
        WorkflowTask to = new WorkflowTask();
        to.setName( from.getName() );
        to.setTaskReferenceName( from.getTaskReferenceName() );
        to.setDescription( from.getDescription() );
        Map<String, Object> inputParametersMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputParametersMap().entrySet()) {
            inputParametersMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInputParameters(inputParametersMap);
        to.setType( from.getType() );
        to.setDynamicTaskNameParam( from.getDynamicTaskNameParam() );
        to.setCaseValueParam( from.getCaseValueParam() );
        to.setCaseExpression( from.getCaseExpression() );
        Map<String, List<WorkflowTask>> decisionCasesMap = new HashMap<String, List<WorkflowTask>>();
        for (Map.Entry<String, WorkflowTaskPb.WorkflowTask.WorkflowTaskList> pair : from.getDecisionCasesMap().entrySet()) {
            decisionCasesMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setDecisionCases(decisionCasesMap);
        to.setDynamicForkTasksParam( from.getDynamicForkTasksParam() );
        to.setDynamicForkTasksInputParamName( from.getDynamicForkTasksInputParamName() );
        to.setDefaultCase( from.getDefaultCaseList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setForkTasks( from.getForkTasksList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setStartDelay( from.getStartDelay() );
        to.setSubWorkflowParam( fromProto( from.getSubWorkflowParam() ) );
        to.setJoinOn( from.getJoinOnList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        to.setSink( from.getSink() );
        to.setOptional( from.getOptional() );
        return to;
    }

    public static WorkflowTaskPb.WorkflowTask.Type toProto(WorkflowTask.Type from) {
        WorkflowTaskPb.WorkflowTask.Type to;
        switch (from) {
            case SIMPLE: to = WorkflowTaskPb.WorkflowTask.Type.SIMPLE; break;
            case DYNAMIC: to = WorkflowTaskPb.WorkflowTask.Type.DYNAMIC; break;
            case FORK_JOIN: to = WorkflowTaskPb.WorkflowTask.Type.FORK_JOIN; break;
            case FORK_JOIN_DYNAMIC: to = WorkflowTaskPb.WorkflowTask.Type.FORK_JOIN_DYNAMIC; break;
            case DECISION: to = WorkflowTaskPb.WorkflowTask.Type.DECISION; break;
            case JOIN: to = WorkflowTaskPb.WorkflowTask.Type.JOIN; break;
            case SUB_WORKFLOW: to = WorkflowTaskPb.WorkflowTask.Type.SUB_WORKFLOW; break;
            case EVENT: to = WorkflowTaskPb.WorkflowTask.Type.EVENT; break;
            case WAIT: to = WorkflowTaskPb.WorkflowTask.Type.WAIT; break;
            case USER_DEFINED: to = WorkflowTaskPb.WorkflowTask.Type.USER_DEFINED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static WorkflowTask.Type fromProto(WorkflowTaskPb.WorkflowTask.Type from) {
        WorkflowTask.Type to;
        switch (from) {
            case SIMPLE: to = WorkflowTask.Type.SIMPLE; break;
            case DYNAMIC: to = WorkflowTask.Type.DYNAMIC; break;
            case FORK_JOIN: to = WorkflowTask.Type.FORK_JOIN; break;
            case FORK_JOIN_DYNAMIC: to = WorkflowTask.Type.FORK_JOIN_DYNAMIC; break;
            case DECISION: to = WorkflowTask.Type.DECISION; break;
            case JOIN: to = WorkflowTask.Type.JOIN; break;
            case SUB_WORKFLOW: to = WorkflowTask.Type.SUB_WORKFLOW; break;
            case EVENT: to = WorkflowTask.Type.EVENT; break;
            case WAIT: to = WorkflowTask.Type.WAIT; break;
            case USER_DEFINED: to = WorkflowTask.Type.USER_DEFINED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static TaskSummaryPb.TaskSummary toProto(TaskSummary from) {
        TaskSummaryPb.TaskSummary.Builder to = TaskSummaryPb.TaskSummary.newBuilder();
        to.setWorkflowId( from.getWorkflowId() );
        to.setWorkflowType( from.getWorkflowType() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setScheduledTime( from.getScheduledTime() );
        to.setStartTime( from.getStartTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setEndTime( from.getEndTime() );
        to.setStatus( toProto( from.getStatus() ) );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setExecutionTime( from.getExecutionTime() );
        to.setQueueWaitTime( from.getQueueWaitTime() );
        to.setTaskDefName( from.getTaskDefName() );
        to.setTaskType( from.getTaskType() );
        to.setInput( from.getInput() );
        to.setOutput( from.getOutput() );
        to.setTaskId( from.getTaskId() );
        return to.build();
    }

    public static WorkflowPb.Workflow toProto(Workflow from) {
        WorkflowPb.Workflow.Builder to = WorkflowPb.Workflow.newBuilder();
        to.setStatus( toProto( from.getStatus() ) );
        to.setEndTime( from.getEndTime() );
        to.setWorkflowId( from.getWorkflowId() );
        to.setParentWorkflowId( from.getParentWorkflowId() );
        to.setParentWorkflowTaskId( from.getParentWorkflowTaskId() );
        for (Task elem : from.getTasks()) {
            to.addTasks( toProto(elem) );
        }
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setWorkflowType( from.getWorkflowType() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setSchemaVersion( from.getSchemaVersion() );
        to.setEvent( from.getEvent() );
        to.putAllTaskToDomain( from.getTaskToDomain() );
        to.addAllFailedReferenceTaskNames( from.getFailedReferenceTaskNames() );
        return to.build();
    }

    public static Workflow fromProto(WorkflowPb.Workflow from) {
        Workflow to = new Workflow();
        to.setStatus( fromProto( from.getStatus() ) );
        to.setEndTime( from.getEndTime() );
        to.setWorkflowId( from.getWorkflowId() );
        to.setParentWorkflowId( from.getParentWorkflowId() );
        to.setParentWorkflowTaskId( from.getParentWorkflowTaskId() );
        to.setTasks( from.getTasksList().stream().map(ProtoMapper::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        Map<String, Object> inputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputMap().entrySet()) {
            inputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInput(inputMap);
        Map<String, Object> outputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputMap().entrySet()) {
            outputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutput(outputMap);
        to.setWorkflowType( from.getWorkflowType() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setSchemaVersion( from.getSchemaVersion() );
        to.setEvent( from.getEvent() );
        to.setTaskToDomain( from.getTaskToDomainMap() );
        to.setFailedReferenceTaskNames( from.getFailedReferenceTaskNamesList().stream().collect(Collectors.toCollection(HashSet::new)) );
        return to;
    }

    public static WorkflowPb.Workflow.WorkflowStatus toProto(Workflow.WorkflowStatus from) {
        WorkflowPb.Workflow.WorkflowStatus to;
        switch (from) {
            case RUNNING: to = WorkflowPb.Workflow.WorkflowStatus.RUNNING; break;
            case COMPLETED: to = WorkflowPb.Workflow.WorkflowStatus.COMPLETED; break;
            case FAILED: to = WorkflowPb.Workflow.WorkflowStatus.FAILED; break;
            case TIMED_OUT: to = WorkflowPb.Workflow.WorkflowStatus.TIMED_OUT; break;
            case TERMINATED: to = WorkflowPb.Workflow.WorkflowStatus.TERMINATED; break;
            case PAUSED: to = WorkflowPb.Workflow.WorkflowStatus.PAUSED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static Workflow.WorkflowStatus fromProto(WorkflowPb.Workflow.WorkflowStatus from) {
        Workflow.WorkflowStatus to;
        switch (from) {
            case RUNNING: to = Workflow.WorkflowStatus.RUNNING; break;
            case COMPLETED: to = Workflow.WorkflowStatus.COMPLETED; break;
            case FAILED: to = Workflow.WorkflowStatus.FAILED; break;
            case TIMED_OUT: to = Workflow.WorkflowStatus.TIMED_OUT; break;
            case TERMINATED: to = Workflow.WorkflowStatus.TERMINATED; break;
            case PAUSED: to = Workflow.WorkflowStatus.PAUSED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public static WorkflowSummaryPb.WorkflowSummary toProto(WorkflowSummary from) {
        WorkflowSummaryPb.WorkflowSummary.Builder to = WorkflowSummaryPb.WorkflowSummary.newBuilder();
        to.setWorkflowType( from.getWorkflowType() );
        to.setVersion( from.getVersion() );
        to.setWorkflowId( from.getWorkflowId() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setStartTime( from.getStartTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setEndTime( from.getEndTime() );
        to.setStatus( toProto( from.getStatus() ) );
        to.setInput( from.getInput() );
        to.setOutput( from.getOutput() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setExecutionTime( from.getExecutionTime() );
        to.setEvent( from.getEvent() );
        to.setFailedReferenceTaskNames( from.getFailedReferenceTaskNames() );
        return to.build();
    }
}
