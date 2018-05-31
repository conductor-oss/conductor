package com.netflix.conductor.protogen;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.FileTemplateLoader;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.squareup.javapoet.*;

import javax.annotation.Generated;
import javax.lang.model.element.Modifier;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class ProtoGen {
    public static String GENERATED_PROTO_PACKAGE = "com.netflix.conductor.proto";
    public static String GENERATED_MAPPER_PACKAGE = "com.netflix.conductor.grpc.server";
    public static String GENERATOR_NAME = "com.netflix.conductor.protogen.ProtoGen";
    public static String GENERATED_GO_PACKAGE = "github.com/netflix/conductor/client/gogrpc/conductor/model";

    private List<File> files = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        ProtoGen generator = new ProtoGen();

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
        generator.writeMapper("grpc-server/src/main/java/com/netflix/conductor/grpc/server");
    }

    public ProtoGen() {
    }

    public void writeMapper(String root) throws Exception {
        TypeSpec.Builder protoMapper = TypeSpec.classBuilder("AbstractProtoMapper")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(AnnotationSpec.builder(Generated.class)
                        .addMember("value", "$S", GENERATOR_NAME).build());

        Set<MethodSpec> abstractMethods = new HashSet<>();

        for (File file : files) {
            Element elem = file.getMessage();
            elem.generateJavaMapper(protoMapper);
            elem.generateAbstractMethods(abstractMethods);
        }

        protoMapper.addMethods(abstractMethods);

        JavaFile javaFile = JavaFile.builder(GENERATED_MAPPER_PACKAGE, protoMapper.build())
                .indent("    ").build();
        Path filename = Paths.get(root, "AbstractProtoMapper.java");
        try (Writer writer = new FileWriter(filename.toString())) {
            javaFile.writeTo(writer);
        }
    }

    public void writeProtos(String root) throws Exception {
        TemplateLoader loader = new FileTemplateLoader("protogen/templates", ".proto");
        Handlebars handlebars = new Handlebars(loader)
                .infiniteLoops(true)
                .prettyPrint(true)
                .with(EscapingStrategy.NOOP);

        Template protoFile = handlebars.compile("file");

        for (File file : files) {
            Path filename = Paths.get(root, file.getFilePath());
            try (Writer writer = new FileWriter(filename.toString())) {
                protoFile.apply(file, writer);
            }
        }
    }

    public void process(Class obj) throws Exception {
        files.add(new File(obj));
    }
}
