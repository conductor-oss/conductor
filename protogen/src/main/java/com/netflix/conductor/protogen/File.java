package com.netflix.conductor.protogen;

import com.netflix.conductor.protogen.types.AbstractType;
import com.squareup.javapoet.ClassName;

import java.util.*;

public class File {
    public static String PROTO_SUFFIX = "Pb";

    private ClassName baseClass;
    private Element message;

    public File(Class object) {
        String className = object.getSimpleName() + PROTO_SUFFIX;
        baseClass = ClassName.get(ProtoGen.GENERATED_PROTO_PACKAGE, className);
        this.message = new Message(object,  AbstractType.baseClass(baseClass, this));
    }

    public String getJavaClassName() {
        return baseClass.simpleName();
    }

    public String getFilePath() {
        return "model/" + message.getName().toLowerCase() + ".proto";
    }

    public String getPackageName() {
        return ProtoGen.GENERATED_PROTO_PACKAGE;
    }

    public String getGoPackage() {
        return ProtoGen.GENERATED_GO_PACKAGE;
    }

    public Element getMessage() {
        return message;
    }

    public Set<String> getIncludes() {
        Set<String> includes = new HashSet<>();
        message.findDependencies(includes);
        includes.remove(this.getFilePath());
        return includes;
    }
}
