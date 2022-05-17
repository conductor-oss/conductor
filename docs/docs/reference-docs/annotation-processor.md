# Annotation Processor

- Original Author: Vicent Martí - https://github.com/vmg
- Original Repo: https://github.com/vmg/protogen

This module is strictly for code generation tasks during builds based on annotations.
Currently supports `protogen`

### Usage

See example below

### Example

This is an actual example of this module which is implemented in common/build.gradle

```groovy
task protogen(dependsOn: jar, type: JavaExec) {
    classpath configurations.annotationsProcessorCodegen
    main = 'com.netflix.conductor.annotationsprocessor.protogen.ProtoGenTask'
    args(
            "conductor.proto",
            "com.netflix.conductor.proto",
            "github.com/netflix/conductor/client/gogrpc/conductor/model",
            "${rootDir}/grpc/src/main/proto",
            "${rootDir}/grpc/src/main/java/com/netflix/conductor/grpc",
            "com.netflix.conductor.grpc",
            jar.archivePath,
            "com.netflix.conductor.common",
    )
}
```

