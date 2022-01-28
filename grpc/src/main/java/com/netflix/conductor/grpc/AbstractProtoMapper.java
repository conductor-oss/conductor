package com.netflix.conductor.grpc;

import com.google.protobuf.Any;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Generated;

@Generated("com.netflix.conductor.annotationsprocessor.protogen")
public abstract class AbstractProtoMapper {
    public DynamicForkJoinTaskPb.DynamicForkJoinTask toProto(DynamicForkJoinTask from) {
        DynamicForkJoinTaskPb.DynamicForkJoinTask.Builder to = DynamicForkJoinTaskPb.DynamicForkJoinTask.newBuilder();
        if (from.getTaskName() != null) {
            to.setTaskName( from.getTaskName() );
        }
        if (from.getWorkflowName() != null) {
            to.setWorkflowName( from.getWorkflowName() );
        }
        if (from.getReferenceName() != null) {
            to.setReferenceName( from.getReferenceName() );
        }
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getType() != null) {
            to.setType( from.getType() );
        }
        return to.build();
    }

    public DynamicForkJoinTask fromProto(DynamicForkJoinTaskPb.DynamicForkJoinTask from) {
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

    public DynamicForkJoinTaskListPb.DynamicForkJoinTaskList toProto(DynamicForkJoinTaskList from) {
        DynamicForkJoinTaskListPb.DynamicForkJoinTaskList.Builder to = DynamicForkJoinTaskListPb.DynamicForkJoinTaskList.newBuilder();
        for (DynamicForkJoinTask elem : from.getDynamicTasks()) {
            to.addDynamicTasks( toProto(elem) );
        }
        return to.build();
    }

    public DynamicForkJoinTaskList fromProto(
            DynamicForkJoinTaskListPb.DynamicForkJoinTaskList from) {
        DynamicForkJoinTaskList to = new DynamicForkJoinTaskList();
        to.setDynamicTasks( from.getDynamicTasksList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        return to;
    }

    public EventExecutionPb.EventExecution toProto(EventExecution from) {
        EventExecutionPb.EventExecution.Builder to = EventExecutionPb.EventExecution.newBuilder();
        if (from.getId() != null) {
            to.setId( from.getId() );
        }
        if (from.getMessageId() != null) {
            to.setMessageId( from.getMessageId() );
        }
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getEvent() != null) {
            to.setEvent( from.getEvent() );
        }
        to.setCreated( from.getCreated() );
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        if (from.getAction() != null) {
            to.setAction( toProto( from.getAction() ) );
        }
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public EventExecution fromProto(EventExecutionPb.EventExecution from) {
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

    public EventExecutionPb.EventExecution.Status toProto(EventExecution.Status from) {
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

    public EventExecution.Status fromProto(EventExecutionPb.EventExecution.Status from) {
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

    public EventHandlerPb.EventHandler toProto(EventHandler from) {
        EventHandlerPb.EventHandler.Builder to = EventHandlerPb.EventHandler.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getEvent() != null) {
            to.setEvent( from.getEvent() );
        }
        if (from.getCondition() != null) {
            to.setCondition( from.getCondition() );
        }
        for (EventHandler.Action elem : from.getActions()) {
            to.addActions( toProto(elem) );
        }
        to.setActive( from.isActive() );
        if (from.getEvaluatorType() != null) {
            to.setEvaluatorType( from.getEvaluatorType() );
        }
        return to.build();
    }

    public EventHandler fromProto(EventHandlerPb.EventHandler from) {
        EventHandler to = new EventHandler();
        to.setName( from.getName() );
        to.setEvent( from.getEvent() );
        to.setCondition( from.getCondition() );
        to.setActions( from.getActionsList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setActive( from.getActive() );
        to.setEvaluatorType( from.getEvaluatorType() );
        return to;
    }

    public EventHandlerPb.EventHandler.StartWorkflow toProto(EventHandler.StartWorkflow from) {
        EventHandlerPb.EventHandler.StartWorkflow.Builder to = EventHandlerPb.EventHandler.StartWorkflow.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getVersion() != null) {
            to.setVersion( from.getVersion() );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getInputMessage() != null) {
            to.setInputMessage( toProto( from.getInputMessage() ) );
        }
        to.putAllTaskToDomain( from.getTaskToDomain() );
        return to.build();
    }

    public EventHandler.StartWorkflow fromProto(EventHandlerPb.EventHandler.StartWorkflow from) {
        EventHandler.StartWorkflow to = new EventHandler.StartWorkflow();
        to.setName( from.getName() );
        to.setVersion( from.getVersion() );
        to.setCorrelationId( from.getCorrelationId() );
        Map<String, Object> inputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputMap().entrySet()) {
            inputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInput(inputMap);
        if (from.hasInputMessage()) {
            to.setInputMessage( fromProto( from.getInputMessage() ) );
        }
        to.setTaskToDomain( from.getTaskToDomainMap() );
        return to;
    }

    public EventHandlerPb.EventHandler.TaskDetails toProto(EventHandler.TaskDetails from) {
        EventHandlerPb.EventHandler.TaskDetails.Builder to = EventHandlerPb.EventHandler.TaskDetails.newBuilder();
        if (from.getWorkflowId() != null) {
            to.setWorkflowId( from.getWorkflowId() );
        }
        if (from.getTaskRefName() != null) {
            to.setTaskRefName( from.getTaskRefName() );
        }
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getOutputMessage() != null) {
            to.setOutputMessage( toProto( from.getOutputMessage() ) );
        }
        if (from.getTaskId() != null) {
            to.setTaskId( from.getTaskId() );
        }
        return to.build();
    }

    public EventHandler.TaskDetails fromProto(EventHandlerPb.EventHandler.TaskDetails from) {
        EventHandler.TaskDetails to = new EventHandler.TaskDetails();
        to.setWorkflowId( from.getWorkflowId() );
        to.setTaskRefName( from.getTaskRefName() );
        Map<String, Object> outputMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputMap().entrySet()) {
            outputMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutput(outputMap);
        if (from.hasOutputMessage()) {
            to.setOutputMessage( fromProto( from.getOutputMessage() ) );
        }
        to.setTaskId( from.getTaskId() );
        return to;
    }

    public EventHandlerPb.EventHandler.Action toProto(EventHandler.Action from) {
        EventHandlerPb.EventHandler.Action.Builder to = EventHandlerPb.EventHandler.Action.newBuilder();
        if (from.getAction() != null) {
            to.setAction( toProto( from.getAction() ) );
        }
        if (from.getStart_workflow() != null) {
            to.setStartWorkflow( toProto( from.getStart_workflow() ) );
        }
        if (from.getComplete_task() != null) {
            to.setCompleteTask( toProto( from.getComplete_task() ) );
        }
        if (from.getFail_task() != null) {
            to.setFailTask( toProto( from.getFail_task() ) );
        }
        to.setExpandInlineJson( from.isExpandInlineJSON() );
        return to.build();
    }

    public EventHandler.Action fromProto(EventHandlerPb.EventHandler.Action from) {
        EventHandler.Action to = new EventHandler.Action();
        to.setAction( fromProto( from.getAction() ) );
        if (from.hasStartWorkflow()) {
            to.setStart_workflow( fromProto( from.getStartWorkflow() ) );
        }
        if (from.hasCompleteTask()) {
            to.setComplete_task( fromProto( from.getCompleteTask() ) );
        }
        if (from.hasFailTask()) {
            to.setFail_task( fromProto( from.getFailTask() ) );
        }
        to.setExpandInlineJSON( from.getExpandInlineJson() );
        return to;
    }

    public EventHandlerPb.EventHandler.Action.Type toProto(EventHandler.Action.Type from) {
        EventHandlerPb.EventHandler.Action.Type to;
        switch (from) {
            case start_workflow: to = EventHandlerPb.EventHandler.Action.Type.START_WORKFLOW; break;
            case complete_task: to = EventHandlerPb.EventHandler.Action.Type.COMPLETE_TASK; break;
            case fail_task: to = EventHandlerPb.EventHandler.Action.Type.FAIL_TASK; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public EventHandler.Action.Type fromProto(EventHandlerPb.EventHandler.Action.Type from) {
        EventHandler.Action.Type to;
        switch (from) {
            case START_WORKFLOW: to = EventHandler.Action.Type.start_workflow; break;
            case COMPLETE_TASK: to = EventHandler.Action.Type.complete_task; break;
            case FAIL_TASK: to = EventHandler.Action.Type.fail_task; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public PollDataPb.PollData toProto(PollData from) {
        PollDataPb.PollData.Builder to = PollDataPb.PollData.newBuilder();
        if (from.getQueueName() != null) {
            to.setQueueName( from.getQueueName() );
        }
        if (from.getDomain() != null) {
            to.setDomain( from.getDomain() );
        }
        if (from.getWorkerId() != null) {
            to.setWorkerId( from.getWorkerId() );
        }
        to.setLastPollTime( from.getLastPollTime() );
        return to.build();
    }

    public PollData fromProto(PollDataPb.PollData from) {
        PollData to = new PollData();
        to.setQueueName( from.getQueueName() );
        to.setDomain( from.getDomain() );
        to.setWorkerId( from.getWorkerId() );
        to.setLastPollTime( from.getLastPollTime() );
        return to;
    }

    public RerunWorkflowRequestPb.RerunWorkflowRequest toProto(RerunWorkflowRequest from) {
        RerunWorkflowRequestPb.RerunWorkflowRequest.Builder to = RerunWorkflowRequestPb.RerunWorkflowRequest.newBuilder();
        if (from.getReRunFromWorkflowId() != null) {
            to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        }
        for (Map.Entry<String, Object> pair : from.getWorkflowInput().entrySet()) {
            to.putWorkflowInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getReRunFromTaskId() != null) {
            to.setReRunFromTaskId( from.getReRunFromTaskId() );
        }
        for (Map.Entry<String, Object> pair : from.getTaskInput().entrySet()) {
            to.putTaskInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        return to.build();
    }

    public RerunWorkflowRequest fromProto(RerunWorkflowRequestPb.RerunWorkflowRequest from) {
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

    public SkipTaskRequest fromProto(SkipTaskRequestPb.SkipTaskRequest from) {
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
        if (from.hasTaskInputMessage()) {
            to.setTaskInputMessage( fromProto( from.getTaskInputMessage() ) );
        }
        if (from.hasTaskOutputMessage()) {
            to.setTaskOutputMessage( fromProto( from.getTaskOutputMessage() ) );
        }
        return to;
    }

    public StartWorkflowRequestPb.StartWorkflowRequest toProto(StartWorkflowRequest from) {
        StartWorkflowRequestPb.StartWorkflowRequest.Builder to = StartWorkflowRequestPb.StartWorkflowRequest.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getVersion() != null) {
            to.setVersion( from.getVersion() );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.putAllTaskToDomain( from.getTaskToDomain() );
        if (from.getWorkflowDef() != null) {
            to.setWorkflowDef( toProto( from.getWorkflowDef() ) );
        }
        if (from.getExternalInputPayloadStoragePath() != null) {
            to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        }
        if (from.getPriority() != null) {
            to.setPriority( from.getPriority() );
        }
        return to.build();
    }

    public StartWorkflowRequest fromProto(StartWorkflowRequestPb.StartWorkflowRequest from) {
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
        if (from.hasWorkflowDef()) {
            to.setWorkflowDef( fromProto( from.getWorkflowDef() ) );
        }
        to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        to.setPriority( from.getPriority() );
        return to;
    }

    public SubWorkflowParamsPb.SubWorkflowParams toProto(SubWorkflowParams from) {
        SubWorkflowParamsPb.SubWorkflowParams.Builder to = SubWorkflowParamsPb.SubWorkflowParams.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getVersion() != null) {
            to.setVersion( from.getVersion() );
        }
        to.putAllTaskToDomain( from.getTaskToDomain() );
        if (from.getWorkflowDefinition() != null) {
            to.setWorkflowDefinition( toProto( from.getWorkflowDefinition() ) );
        }
        return to.build();
    }

    public SubWorkflowParams fromProto(SubWorkflowParamsPb.SubWorkflowParams from) {
        SubWorkflowParams to = new SubWorkflowParams();
        to.setName( from.getName() );
        to.setVersion( from.getVersion() );
        to.setTaskToDomain( from.getTaskToDomainMap() );
        if (from.hasWorkflowDefinition()) {
            to.setWorkflowDefinition( fromProto( from.getWorkflowDefinition() ) );
        }
        return to;
    }

    public TaskPb.Task toProto(Task from) {
        TaskPb.Task.Builder to = TaskPb.Task.newBuilder();
        if (from.getTaskType() != null) {
            to.setTaskType( from.getTaskType() );
        }
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        for (Map.Entry<String, Object> pair : from.getInputData().entrySet()) {
            to.putInputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getReferenceTaskName() != null) {
            to.setReferenceTaskName( from.getReferenceTaskName() );
        }
        to.setRetryCount( from.getRetryCount() );
        to.setSeq( from.getSeq() );
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        to.setPollCount( from.getPollCount() );
        if (from.getTaskDefName() != null) {
            to.setTaskDefName( from.getTaskDefName() );
        }
        to.setScheduledTime( from.getScheduledTime() );
        to.setStartTime( from.getStartTime() );
        to.setEndTime( from.getEndTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setStartDelayInSeconds( from.getStartDelayInSeconds() );
        if (from.getRetriedTaskId() != null) {
            to.setRetriedTaskId( from.getRetriedTaskId() );
        }
        to.setRetried( from.isRetried() );
        to.setExecuted( from.isExecuted() );
        to.setCallbackFromWorker( from.isCallbackFromWorker() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        if (from.getWorkflowInstanceId() != null) {
            to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        }
        if (from.getWorkflowType() != null) {
            to.setWorkflowType( from.getWorkflowType() );
        }
        if (from.getTaskId() != null) {
            to.setTaskId( from.getTaskId() );
        }
        if (from.getReasonForIncompletion() != null) {
            to.setReasonForIncompletion( from.getReasonForIncompletion() );
        }
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        if (from.getWorkerId() != null) {
            to.setWorkerId( from.getWorkerId() );
        }
        for (Map.Entry<String, Object> pair : from.getOutputData().entrySet()) {
            to.putOutputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getWorkflowTask() != null) {
            to.setWorkflowTask( toProto( from.getWorkflowTask() ) );
        }
        if (from.getDomain() != null) {
            to.setDomain( from.getDomain() );
        }
        if (from.getInputMessage() != null) {
            to.setInputMessage( toProto( from.getInputMessage() ) );
        }
        if (from.getOutputMessage() != null) {
            to.setOutputMessage( toProto( from.getOutputMessage() ) );
        }
        to.setRateLimitPerFrequency( from.getRateLimitPerFrequency() );
        to.setRateLimitFrequencyInSeconds( from.getRateLimitFrequencyInSeconds() );
        if (from.getExternalInputPayloadStoragePath() != null) {
            to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        }
        if (from.getExternalOutputPayloadStoragePath() != null) {
            to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        }
        to.setWorkflowPriority( from.getWorkflowPriority() );
        if (from.getExecutionNameSpace() != null) {
            to.setExecutionNameSpace( from.getExecutionNameSpace() );
        }
        if (from.getIsolationGroupId() != null) {
            to.setIsolationGroupId( from.getIsolationGroupId() );
        }
        to.setIteration( from.getIteration() );
        if (from.getSubWorkflowId() != null) {
            to.setSubWorkflowId( from.getSubWorkflowId() );
        }
        to.setSubworkflowChanged( from.isSubworkflowChanged() );
        return to.build();
    }

    public Task fromProto(TaskPb.Task from) {
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
        to.setExecuted( from.getExecuted() );
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
        if (from.hasWorkflowTask()) {
            to.setWorkflowTask( fromProto( from.getWorkflowTask() ) );
        }
        to.setDomain( from.getDomain() );
        if (from.hasInputMessage()) {
            to.setInputMessage( fromProto( from.getInputMessage() ) );
        }
        if (from.hasOutputMessage()) {
            to.setOutputMessage( fromProto( from.getOutputMessage() ) );
        }
        to.setRateLimitPerFrequency( from.getRateLimitPerFrequency() );
        to.setRateLimitFrequencyInSeconds( from.getRateLimitFrequencyInSeconds() );
        to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        to.setWorkflowPriority( from.getWorkflowPriority() );
        to.setExecutionNameSpace( from.getExecutionNameSpace() );
        to.setIsolationGroupId( from.getIsolationGroupId() );
        to.setIteration( from.getIteration() );
        to.setSubWorkflowId( from.getSubWorkflowId() );
        to.setSubworkflowChanged( from.getSubworkflowChanged() );
        return to;
    }

    public TaskPb.Task.Status toProto(Task.Status from) {
        TaskPb.Task.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskPb.Task.Status.IN_PROGRESS; break;
            case CANCELED: to = TaskPb.Task.Status.CANCELED; break;
            case FAILED: to = TaskPb.Task.Status.FAILED; break;
            case FAILED_WITH_TERMINAL_ERROR: to = TaskPb.Task.Status.FAILED_WITH_TERMINAL_ERROR; break;
            case COMPLETED: to = TaskPb.Task.Status.COMPLETED; break;
            case COMPLETED_WITH_ERRORS: to = TaskPb.Task.Status.COMPLETED_WITH_ERRORS; break;
            case SCHEDULED: to = TaskPb.Task.Status.SCHEDULED; break;
            case TIMED_OUT: to = TaskPb.Task.Status.TIMED_OUT; break;
            case SKIPPED: to = TaskPb.Task.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public Task.Status fromProto(TaskPb.Task.Status from) {
        Task.Status to;
        switch (from) {
            case IN_PROGRESS: to = Task.Status.IN_PROGRESS; break;
            case CANCELED: to = Task.Status.CANCELED; break;
            case FAILED: to = Task.Status.FAILED; break;
            case FAILED_WITH_TERMINAL_ERROR: to = Task.Status.FAILED_WITH_TERMINAL_ERROR; break;
            case COMPLETED: to = Task.Status.COMPLETED; break;
            case COMPLETED_WITH_ERRORS: to = Task.Status.COMPLETED_WITH_ERRORS; break;
            case SCHEDULED: to = Task.Status.SCHEDULED; break;
            case TIMED_OUT: to = Task.Status.TIMED_OUT; break;
            case SKIPPED: to = Task.Status.SKIPPED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskDefPb.TaskDef toProto(TaskDef from) {
        TaskDefPb.TaskDef.Builder to = TaskDefPb.TaskDef.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getDescription() != null) {
            to.setDescription( from.getDescription() );
        }
        to.setRetryCount( from.getRetryCount() );
        to.setTimeoutSeconds( from.getTimeoutSeconds() );
        to.addAllInputKeys( from.getInputKeys() );
        to.addAllOutputKeys( from.getOutputKeys() );
        if (from.getTimeoutPolicy() != null) {
            to.setTimeoutPolicy( toProto( from.getTimeoutPolicy() ) );
        }
        if (from.getRetryLogic() != null) {
            to.setRetryLogic( toProto( from.getRetryLogic() ) );
        }
        to.setRetryDelaySeconds( from.getRetryDelaySeconds() );
        to.setResponseTimeoutSeconds( from.getResponseTimeoutSeconds() );
        if (from.getConcurrentExecLimit() != null) {
            to.setConcurrentExecLimit( from.getConcurrentExecLimit() );
        }
        for (Map.Entry<String, Object> pair : from.getInputTemplate().entrySet()) {
            to.putInputTemplate( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getRateLimitPerFrequency() != null) {
            to.setRateLimitPerFrequency( from.getRateLimitPerFrequency() );
        }
        if (from.getRateLimitFrequencyInSeconds() != null) {
            to.setRateLimitFrequencyInSeconds( from.getRateLimitFrequencyInSeconds() );
        }
        if (from.getIsolationGroupId() != null) {
            to.setIsolationGroupId( from.getIsolationGroupId() );
        }
        if (from.getExecutionNameSpace() != null) {
            to.setExecutionNameSpace( from.getExecutionNameSpace() );
        }
        if (from.getOwnerEmail() != null) {
            to.setOwnerEmail( from.getOwnerEmail() );
        }
        if (from.getPollTimeoutSeconds() != null) {
            to.setPollTimeoutSeconds( from.getPollTimeoutSeconds() );
        }
        if (from.getBackoffScaleFactor() != null) {
            to.setBackoffScaleFactor( from.getBackoffScaleFactor() );
        }
        return to.build();
    }

    public TaskDef fromProto(TaskDefPb.TaskDef from) {
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
        to.setRateLimitPerFrequency( from.getRateLimitPerFrequency() );
        to.setRateLimitFrequencyInSeconds( from.getRateLimitFrequencyInSeconds() );
        to.setIsolationGroupId( from.getIsolationGroupId() );
        to.setExecutionNameSpace( from.getExecutionNameSpace() );
        to.setOwnerEmail( from.getOwnerEmail() );
        to.setPollTimeoutSeconds( from.getPollTimeoutSeconds() );
        to.setBackoffScaleFactor( from.getBackoffScaleFactor() );
        return to;
    }

    public TaskDefPb.TaskDef.RetryLogic toProto(TaskDef.RetryLogic from) {
        TaskDefPb.TaskDef.RetryLogic to;
        switch (from) {
            case FIXED: to = TaskDefPb.TaskDef.RetryLogic.FIXED; break;
            case EXPONENTIAL_BACKOFF: to = TaskDefPb.TaskDef.RetryLogic.EXPONENTIAL_BACKOFF; break;
            case LINEAR_BACKOFF: to = TaskDefPb.TaskDef.RetryLogic.LINEAR_BACKOFF; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskDef.RetryLogic fromProto(TaskDefPb.TaskDef.RetryLogic from) {
        TaskDef.RetryLogic to;
        switch (from) {
            case FIXED: to = TaskDef.RetryLogic.FIXED; break;
            case EXPONENTIAL_BACKOFF: to = TaskDef.RetryLogic.EXPONENTIAL_BACKOFF; break;
            case LINEAR_BACKOFF: to = TaskDef.RetryLogic.LINEAR_BACKOFF; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskDefPb.TaskDef.TimeoutPolicy toProto(TaskDef.TimeoutPolicy from) {
        TaskDefPb.TaskDef.TimeoutPolicy to;
        switch (from) {
            case RETRY: to = TaskDefPb.TaskDef.TimeoutPolicy.RETRY; break;
            case TIME_OUT_WF: to = TaskDefPb.TaskDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = TaskDefPb.TaskDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskDef.TimeoutPolicy fromProto(TaskDefPb.TaskDef.TimeoutPolicy from) {
        TaskDef.TimeoutPolicy to;
        switch (from) {
            case RETRY: to = TaskDef.TimeoutPolicy.RETRY; break;
            case TIME_OUT_WF: to = TaskDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = TaskDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskExecLogPb.TaskExecLog toProto(TaskExecLog from) {
        TaskExecLogPb.TaskExecLog.Builder to = TaskExecLogPb.TaskExecLog.newBuilder();
        if (from.getLog() != null) {
            to.setLog( from.getLog() );
        }
        if (from.getTaskId() != null) {
            to.setTaskId( from.getTaskId() );
        }
        to.setCreatedTime( from.getCreatedTime() );
        return to.build();
    }

    public TaskExecLog fromProto(TaskExecLogPb.TaskExecLog from) {
        TaskExecLog to = new TaskExecLog();
        to.setLog( from.getLog() );
        to.setTaskId( from.getTaskId() );
        to.setCreatedTime( from.getCreatedTime() );
        return to;
    }

    public TaskResultPb.TaskResult toProto(TaskResult from) {
        TaskResultPb.TaskResult.Builder to = TaskResultPb.TaskResult.newBuilder();
        if (from.getWorkflowInstanceId() != null) {
            to.setWorkflowInstanceId( from.getWorkflowInstanceId() );
        }
        if (from.getTaskId() != null) {
            to.setTaskId( from.getTaskId() );
        }
        if (from.getReasonForIncompletion() != null) {
            to.setReasonForIncompletion( from.getReasonForIncompletion() );
        }
        to.setCallbackAfterSeconds( from.getCallbackAfterSeconds() );
        if (from.getWorkerId() != null) {
            to.setWorkerId( from.getWorkerId() );
        }
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        for (Map.Entry<String, Object> pair : from.getOutputData().entrySet()) {
            to.putOutputData( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getOutputMessage() != null) {
            to.setOutputMessage( toProto( from.getOutputMessage() ) );
        }
        return to.build();
    }

    public TaskResult fromProto(TaskResultPb.TaskResult from) {
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
        if (from.hasOutputMessage()) {
            to.setOutputMessage( fromProto( from.getOutputMessage() ) );
        }
        return to;
    }

    public TaskResultPb.TaskResult.Status toProto(TaskResult.Status from) {
        TaskResultPb.TaskResult.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskResultPb.TaskResult.Status.IN_PROGRESS; break;
            case FAILED: to = TaskResultPb.TaskResult.Status.FAILED; break;
            case FAILED_WITH_TERMINAL_ERROR: to = TaskResultPb.TaskResult.Status.FAILED_WITH_TERMINAL_ERROR; break;
            case COMPLETED: to = TaskResultPb.TaskResult.Status.COMPLETED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskResult.Status fromProto(TaskResultPb.TaskResult.Status from) {
        TaskResult.Status to;
        switch (from) {
            case IN_PROGRESS: to = TaskResult.Status.IN_PROGRESS; break;
            case FAILED: to = TaskResult.Status.FAILED; break;
            case FAILED_WITH_TERMINAL_ERROR: to = TaskResult.Status.FAILED_WITH_TERMINAL_ERROR; break;
            case COMPLETED: to = TaskResult.Status.COMPLETED; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public TaskSummaryPb.TaskSummary toProto(TaskSummary from) {
        TaskSummaryPb.TaskSummary.Builder to = TaskSummaryPb.TaskSummary.newBuilder();
        if (from.getWorkflowId() != null) {
            to.setWorkflowId( from.getWorkflowId() );
        }
        if (from.getWorkflowType() != null) {
            to.setWorkflowType( from.getWorkflowType() );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        if (from.getScheduledTime() != null) {
            to.setScheduledTime( from.getScheduledTime() );
        }
        if (from.getStartTime() != null) {
            to.setStartTime( from.getStartTime() );
        }
        if (from.getUpdateTime() != null) {
            to.setUpdateTime( from.getUpdateTime() );
        }
        if (from.getEndTime() != null) {
            to.setEndTime( from.getEndTime() );
        }
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        if (from.getReasonForIncompletion() != null) {
            to.setReasonForIncompletion( from.getReasonForIncompletion() );
        }
        to.setExecutionTime( from.getExecutionTime() );
        to.setQueueWaitTime( from.getQueueWaitTime() );
        if (from.getTaskDefName() != null) {
            to.setTaskDefName( from.getTaskDefName() );
        }
        if (from.getTaskType() != null) {
            to.setTaskType( from.getTaskType() );
        }
        if (from.getInput() != null) {
            to.setInput( from.getInput() );
        }
        if (from.getOutput() != null) {
            to.setOutput( from.getOutput() );
        }
        if (from.getTaskId() != null) {
            to.setTaskId( from.getTaskId() );
        }
        if (from.getExternalInputPayloadStoragePath() != null) {
            to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        }
        if (from.getExternalOutputPayloadStoragePath() != null) {
            to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        }
        to.setWorkflowPriority( from.getWorkflowPriority() );
        return to.build();
    }

    public TaskSummary fromProto(TaskSummaryPb.TaskSummary from) {
        TaskSummary to = new TaskSummary();
        to.setWorkflowId( from.getWorkflowId() );
        to.setWorkflowType( from.getWorkflowType() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setScheduledTime( from.getScheduledTime() );
        to.setStartTime( from.getStartTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setEndTime( from.getEndTime() );
        to.setStatus( fromProto( from.getStatus() ) );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setExecutionTime( from.getExecutionTime() );
        to.setQueueWaitTime( from.getQueueWaitTime() );
        to.setTaskDefName( from.getTaskDefName() );
        to.setTaskType( from.getTaskType() );
        to.setInput( from.getInput() );
        to.setOutput( from.getOutput() );
        to.setTaskId( from.getTaskId() );
        to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        to.setWorkflowPriority( from.getWorkflowPriority() );
        return to;
    }

    public WorkflowPb.Workflow toProto(Workflow from) {
        WorkflowPb.Workflow.Builder to = WorkflowPb.Workflow.newBuilder();
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        to.setEndTime( from.getEndTime() );
        if (from.getWorkflowId() != null) {
            to.setWorkflowId( from.getWorkflowId() );
        }
        if (from.getParentWorkflowId() != null) {
            to.setParentWorkflowId( from.getParentWorkflowId() );
        }
        if (from.getParentWorkflowTaskId() != null) {
            to.setParentWorkflowTaskId( from.getParentWorkflowTaskId() );
        }
        for (Task elem : from.getTasks()) {
            to.addTasks( toProto(elem) );
        }
        for (Map.Entry<String, Object> pair : from.getInput().entrySet()) {
            to.putInput( pair.getKey(), toProto( pair.getValue() ) );
        }
        for (Map.Entry<String, Object> pair : from.getOutput().entrySet()) {
            to.putOutput( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        if (from.getReRunFromWorkflowId() != null) {
            to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        }
        if (from.getReasonForIncompletion() != null) {
            to.setReasonForIncompletion( from.getReasonForIncompletion() );
        }
        if (from.getEvent() != null) {
            to.setEvent( from.getEvent() );
        }
        to.putAllTaskToDomain( from.getTaskToDomain() );
        to.addAllFailedReferenceTaskNames( from.getFailedReferenceTaskNames() );
        if (from.getWorkflowDefinition() != null) {
            to.setWorkflowDefinition( toProto( from.getWorkflowDefinition() ) );
        }
        if (from.getExternalInputPayloadStoragePath() != null) {
            to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        }
        if (from.getExternalOutputPayloadStoragePath() != null) {
            to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        }
        to.setPriority( from.getPriority() );
        for (Map.Entry<String, Object> pair : from.getVariables().entrySet()) {
            to.putVariables( pair.getKey(), toProto( pair.getValue() ) );
        }
        to.setLastRetriedTime( from.getLastRetriedTime() );
        return to.build();
    }

    public Workflow fromProto(WorkflowPb.Workflow from) {
        Workflow to = new Workflow();
        to.setStatus( fromProto( from.getStatus() ) );
        to.setEndTime( from.getEndTime() );
        to.setWorkflowId( from.getWorkflowId() );
        to.setParentWorkflowId( from.getParentWorkflowId() );
        to.setParentWorkflowTaskId( from.getParentWorkflowTaskId() );
        to.setTasks( from.getTasksList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
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
        to.setCorrelationId( from.getCorrelationId() );
        to.setReRunFromWorkflowId( from.getReRunFromWorkflowId() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setEvent( from.getEvent() );
        to.setTaskToDomain( from.getTaskToDomainMap() );
        to.setFailedReferenceTaskNames( from.getFailedReferenceTaskNamesList().stream().collect(Collectors.toCollection(HashSet::new)) );
        if (from.hasWorkflowDefinition()) {
            to.setWorkflowDefinition( fromProto( from.getWorkflowDefinition() ) );
        }
        to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        to.setPriority( from.getPriority() );
        Map<String, Object> variablesMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getVariablesMap().entrySet()) {
            variablesMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setVariables(variablesMap);
        to.setLastRetriedTime( from.getLastRetriedTime() );
        return to;
    }

    public WorkflowPb.Workflow.WorkflowStatus toProto(Workflow.WorkflowStatus from) {
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

    public Workflow.WorkflowStatus fromProto(WorkflowPb.Workflow.WorkflowStatus from) {
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

    public WorkflowDefPb.WorkflowDef toProto(WorkflowDef from) {
        WorkflowDefPb.WorkflowDef.Builder to = WorkflowDefPb.WorkflowDef.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getDescription() != null) {
            to.setDescription( from.getDescription() );
        }
        to.setVersion( from.getVersion() );
        for (WorkflowTask elem : from.getTasks()) {
            to.addTasks( toProto(elem) );
        }
        to.addAllInputParameters( from.getInputParameters() );
        for (Map.Entry<String, Object> pair : from.getOutputParameters().entrySet()) {
            to.putOutputParameters( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getFailureWorkflow() != null) {
            to.setFailureWorkflow( from.getFailureWorkflow() );
        }
        to.setSchemaVersion( from.getSchemaVersion() );
        to.setRestartable( from.isRestartable() );
        to.setWorkflowStatusListenerEnabled( from.isWorkflowStatusListenerEnabled() );
        if (from.getOwnerEmail() != null) {
            to.setOwnerEmail( from.getOwnerEmail() );
        }
        if (from.getTimeoutPolicy() != null) {
            to.setTimeoutPolicy( toProto( from.getTimeoutPolicy() ) );
        }
        to.setTimeoutSeconds( from.getTimeoutSeconds() );
        for (Map.Entry<String, Object> pair : from.getVariables().entrySet()) {
            to.putVariables( pair.getKey(), toProto( pair.getValue() ) );
        }
        for (Map.Entry<String, Object> pair : from.getInputTemplate().entrySet()) {
            to.putInputTemplate( pair.getKey(), toProto( pair.getValue() ) );
        }
        return to.build();
    }

    public WorkflowDef fromProto(WorkflowDefPb.WorkflowDef from) {
        WorkflowDef to = new WorkflowDef();
        to.setName( from.getName() );
        to.setDescription( from.getDescription() );
        to.setVersion( from.getVersion() );
        to.setTasks( from.getTasksList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setInputParameters( from.getInputParametersList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        Map<String, Object> outputParametersMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getOutputParametersMap().entrySet()) {
            outputParametersMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setOutputParameters(outputParametersMap);
        to.setFailureWorkflow( from.getFailureWorkflow() );
        to.setSchemaVersion( from.getSchemaVersion() );
        to.setRestartable( from.getRestartable() );
        to.setWorkflowStatusListenerEnabled( from.getWorkflowStatusListenerEnabled() );
        to.setOwnerEmail( from.getOwnerEmail() );
        to.setTimeoutPolicy( fromProto( from.getTimeoutPolicy() ) );
        to.setTimeoutSeconds( from.getTimeoutSeconds() );
        Map<String, Object> variablesMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getVariablesMap().entrySet()) {
            variablesMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setVariables(variablesMap);
        Map<String, Object> inputTemplateMap = new HashMap<String, Object>();
        for (Map.Entry<String, Value> pair : from.getInputTemplateMap().entrySet()) {
            inputTemplateMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setInputTemplate(inputTemplateMap);
        return to;
    }

    public WorkflowDefPb.WorkflowDef.TimeoutPolicy toProto(WorkflowDef.TimeoutPolicy from) {
        WorkflowDefPb.WorkflowDef.TimeoutPolicy to;
        switch (from) {
            case TIME_OUT_WF: to = WorkflowDefPb.WorkflowDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = WorkflowDefPb.WorkflowDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public WorkflowDef.TimeoutPolicy fromProto(WorkflowDefPb.WorkflowDef.TimeoutPolicy from) {
        WorkflowDef.TimeoutPolicy to;
        switch (from) {
            case TIME_OUT_WF: to = WorkflowDef.TimeoutPolicy.TIME_OUT_WF; break;
            case ALERT_ONLY: to = WorkflowDef.TimeoutPolicy.ALERT_ONLY; break;
            default: throw new IllegalArgumentException("Unexpected enum constant: " + from);
        }
        return to;
    }

    public WorkflowSummaryPb.WorkflowSummary toProto(WorkflowSummary from) {
        WorkflowSummaryPb.WorkflowSummary.Builder to = WorkflowSummaryPb.WorkflowSummary.newBuilder();
        if (from.getWorkflowType() != null) {
            to.setWorkflowType( from.getWorkflowType() );
        }
        to.setVersion( from.getVersion() );
        if (from.getWorkflowId() != null) {
            to.setWorkflowId( from.getWorkflowId() );
        }
        if (from.getCorrelationId() != null) {
            to.setCorrelationId( from.getCorrelationId() );
        }
        if (from.getStartTime() != null) {
            to.setStartTime( from.getStartTime() );
        }
        if (from.getUpdateTime() != null) {
            to.setUpdateTime( from.getUpdateTime() );
        }
        if (from.getEndTime() != null) {
            to.setEndTime( from.getEndTime() );
        }
        if (from.getStatus() != null) {
            to.setStatus( toProto( from.getStatus() ) );
        }
        if (from.getInput() != null) {
            to.setInput( from.getInput() );
        }
        if (from.getOutput() != null) {
            to.setOutput( from.getOutput() );
        }
        if (from.getReasonForIncompletion() != null) {
            to.setReasonForIncompletion( from.getReasonForIncompletion() );
        }
        to.setExecutionTime( from.getExecutionTime() );
        if (from.getEvent() != null) {
            to.setEvent( from.getEvent() );
        }
        if (from.getFailedReferenceTaskNames() != null) {
            to.setFailedReferenceTaskNames( from.getFailedReferenceTaskNames() );
        }
        if (from.getExternalInputPayloadStoragePath() != null) {
            to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        }
        if (from.getExternalOutputPayloadStoragePath() != null) {
            to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        }
        to.setPriority( from.getPriority() );
        return to.build();
    }

    public WorkflowSummary fromProto(WorkflowSummaryPb.WorkflowSummary from) {
        WorkflowSummary to = new WorkflowSummary();
        to.setWorkflowType( from.getWorkflowType() );
        to.setVersion( from.getVersion() );
        to.setWorkflowId( from.getWorkflowId() );
        to.setCorrelationId( from.getCorrelationId() );
        to.setStartTime( from.getStartTime() );
        to.setUpdateTime( from.getUpdateTime() );
        to.setEndTime( from.getEndTime() );
        to.setStatus( fromProto( from.getStatus() ) );
        to.setInput( from.getInput() );
        to.setOutput( from.getOutput() );
        to.setReasonForIncompletion( from.getReasonForIncompletion() );
        to.setExecutionTime( from.getExecutionTime() );
        to.setEvent( from.getEvent() );
        to.setFailedReferenceTaskNames( from.getFailedReferenceTaskNames() );
        to.setExternalInputPayloadStoragePath( from.getExternalInputPayloadStoragePath() );
        to.setExternalOutputPayloadStoragePath( from.getExternalOutputPayloadStoragePath() );
        to.setPriority( from.getPriority() );
        return to;
    }

    public WorkflowTaskPb.WorkflowTask toProto(WorkflowTask from) {
        WorkflowTaskPb.WorkflowTask.Builder to = WorkflowTaskPb.WorkflowTask.newBuilder();
        if (from.getName() != null) {
            to.setName( from.getName() );
        }
        if (from.getTaskReferenceName() != null) {
            to.setTaskReferenceName( from.getTaskReferenceName() );
        }
        if (from.getDescription() != null) {
            to.setDescription( from.getDescription() );
        }
        for (Map.Entry<String, Object> pair : from.getInputParameters().entrySet()) {
            to.putInputParameters( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getType() != null) {
            to.setType( from.getType() );
        }
        if (from.getDynamicTaskNameParam() != null) {
            to.setDynamicTaskNameParam( from.getDynamicTaskNameParam() );
        }
        if (from.getCaseValueParam() != null) {
            to.setCaseValueParam( from.getCaseValueParam() );
        }
        if (from.getCaseExpression() != null) {
            to.setCaseExpression( from.getCaseExpression() );
        }
        if (from.getScriptExpression() != null) {
            to.setScriptExpression( from.getScriptExpression() );
        }
        for (Map.Entry<String, List<WorkflowTask>> pair : from.getDecisionCases().entrySet()) {
            to.putDecisionCases( pair.getKey(), toProto( pair.getValue() ) );
        }
        if (from.getDynamicForkTasksParam() != null) {
            to.setDynamicForkTasksParam( from.getDynamicForkTasksParam() );
        }
        if (from.getDynamicForkTasksInputParamName() != null) {
            to.setDynamicForkTasksInputParamName( from.getDynamicForkTasksInputParamName() );
        }
        for (WorkflowTask elem : from.getDefaultCase()) {
            to.addDefaultCase( toProto(elem) );
        }
        for (List<WorkflowTask> elem : from.getForkTasks()) {
            to.addForkTasks( toProto(elem) );
        }
        to.setStartDelay( from.getStartDelay() );
        if (from.getSubWorkflowParam() != null) {
            to.setSubWorkflowParam( toProto( from.getSubWorkflowParam() ) );
        }
        to.addAllJoinOn( from.getJoinOn() );
        if (from.getSink() != null) {
            to.setSink( from.getSink() );
        }
        to.setOptional( from.isOptional() );
        if (from.getTaskDefinition() != null) {
            to.setTaskDefinition( toProto( from.getTaskDefinition() ) );
        }
        if (from.isRateLimited() != null) {
            to.setRateLimited( from.isRateLimited() );
        }
        to.addAllDefaultExclusiveJoinTask( from.getDefaultExclusiveJoinTask() );
        if (from.isAsyncComplete() != null) {
            to.setAsyncComplete( from.isAsyncComplete() );
        }
        if (from.getLoopCondition() != null) {
            to.setLoopCondition( from.getLoopCondition() );
        }
        for (WorkflowTask elem : from.getLoopOver()) {
            to.addLoopOver( toProto(elem) );
        }
        if (from.getRetryCount() != null) {
            to.setRetryCount( from.getRetryCount() );
        }
        if (from.getEvaluatorType() != null) {
            to.setEvaluatorType( from.getEvaluatorType() );
        }
        if (from.getExpression() != null) {
            to.setExpression( from.getExpression() );
        }
        return to.build();
    }

    public WorkflowTask fromProto(WorkflowTaskPb.WorkflowTask from) {
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
        to.setScriptExpression( from.getScriptExpression() );
        Map<String, List<WorkflowTask>> decisionCasesMap = new HashMap<String, List<WorkflowTask>>();
        for (Map.Entry<String, WorkflowTaskPb.WorkflowTask.WorkflowTaskList> pair : from.getDecisionCasesMap().entrySet()) {
            decisionCasesMap.put( pair.getKey(), fromProto( pair.getValue() ) );
        }
        to.setDecisionCases(decisionCasesMap);
        to.setDynamicForkTasksParam( from.getDynamicForkTasksParam() );
        to.setDynamicForkTasksInputParamName( from.getDynamicForkTasksInputParamName() );
        to.setDefaultCase( from.getDefaultCaseList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setForkTasks( from.getForkTasksList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setStartDelay( from.getStartDelay() );
        if (from.hasSubWorkflowParam()) {
            to.setSubWorkflowParam( fromProto( from.getSubWorkflowParam() ) );
        }
        to.setJoinOn( from.getJoinOnList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        to.setSink( from.getSink() );
        to.setOptional( from.getOptional() );
        if (from.hasTaskDefinition()) {
            to.setTaskDefinition( fromProto( from.getTaskDefinition() ) );
        }
        to.setRateLimited( from.getRateLimited() );
        to.setDefaultExclusiveJoinTask( from.getDefaultExclusiveJoinTaskList().stream().collect(Collectors.toCollection(ArrayList::new)) );
        to.setAsyncComplete( from.getAsyncComplete() );
        to.setLoopCondition( from.getLoopCondition() );
        to.setLoopOver( from.getLoopOverList().stream().map(this::fromProto).collect(Collectors.toCollection(ArrayList::new)) );
        to.setRetryCount( from.getRetryCount() );
        to.setEvaluatorType( from.getEvaluatorType() );
        to.setExpression( from.getExpression() );
        return to;
    }

    public abstract WorkflowTaskPb.WorkflowTask.WorkflowTaskList toProto(List<WorkflowTask> in);

    public abstract List<WorkflowTask> fromProto(WorkflowTaskPb.WorkflowTask.WorkflowTaskList in);

    public abstract Value toProto(Object in);

    public abstract Object fromProto(Value in);

    public abstract Any toProto(Any in);

    public abstract Any fromProto(Any in);
}
