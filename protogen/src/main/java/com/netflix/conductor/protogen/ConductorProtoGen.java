package com.netflix.conductor.protogen;

import com.github.vmg.protogen.ProtoGen;

public class ConductorProtoGen {
    private final static String PROTO_PACKAGE_NAME = "conductor.proto";
    private final static String JAVA_PACKAGE_NAME = "com.netflix.conductor.proto";
    private final static String GO_PACKAGE_NAME = "github.com/netflix/conductor/client/gogrpc/conductor/model";
    private final static String MAPPER_PACKAGE_NAME = "com.netflix.conductor.grpc";

    public static void main(String[] args) throws Exception {
        ProtoGen generator = new ProtoGen(
                PROTO_PACKAGE_NAME, JAVA_PACKAGE_NAME, GO_PACKAGE_NAME
        );

        generator.process(com.netflix.conductor.common.metadata.events.EventExecution.class);
        generator.process(com.netflix.conductor.common.metadata.events.EventHandler.class);

        generator.process(com.netflix.conductor.common.metadata.tasks.PollData.class);
        generator.process(com.netflix.conductor.common.metadata.tasks.Task.class);
        generator.process(com.netflix.conductor.common.metadata.tasks.TaskDef.class);
        generator.process(com.netflix.conductor.common.metadata.tasks.TaskExecLog.class);
        generator.process(com.netflix.conductor.common.metadata.tasks.TaskResult.class);

        generator.process(com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTask.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.DynamicForkJoinTaskList.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.RerunWorkflowRequest.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.SkipTaskRequest.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.SubWorkflowParams.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.WorkflowDef.class);
        generator.process(com.netflix.conductor.common.metadata.workflow.WorkflowTask.class);

        generator.process(com.netflix.conductor.common.run.TaskSummary.class);
        generator.process(com.netflix.conductor.common.run.Workflow.class);
        generator.process(com.netflix.conductor.common.run.WorkflowSummary.class);

        generator.writeProtos("grpc/src/main/proto");
        generator.writeMapper(MAPPER_PACKAGE_NAME,"grpc/src/main/java/com/netflix/conductor/grpc/");
    }
}
