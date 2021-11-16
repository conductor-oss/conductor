package com.example;

import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

@ProtoMessage
public class Example {
    @ProtoField(id = 1)
    public String name;
    @ProtoField(id = 2)
    public Long count;
}