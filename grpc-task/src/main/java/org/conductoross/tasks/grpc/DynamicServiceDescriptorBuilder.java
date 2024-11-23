package org.conductoross.tasks.grpc;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DynamicServiceDescriptorBuilder {

    private final Map<String, Descriptors.FileDescriptor> fileDescriptorMap = new HashMap<>();

    /**
     * Loads a descriptor set file and initializes the FileDescriptors.
     *
     * @param descriptorSetPath Path to the .desc file.
     * @throws IOException If the file cannot be read.
     * @throws Descriptors.DescriptorValidationException If descriptor validation fails.
     */
    public void loadDescriptorSet(String descriptorSetPath) throws IOException, Descriptors.DescriptorValidationException {
        DescriptorProtos.FileDescriptorSet descriptorSet =
                DescriptorProtos.FileDescriptorSet.parseFrom(new FileInputStream(descriptorSetPath));

        // Add well-known types to the descriptor cache
        addWellKnownTypes();

        // Build each FileDescriptor and cache it in the map
        for (DescriptorProtos.FileDescriptorProto fileDescriptorProto : descriptorSet.getFileList()) {
            // Resolve dependencies
            Descriptors.FileDescriptor[] dependencies = fileDescriptorProto.getDependencyList().stream()
                    .map(fileDescriptorMap::get)
                    .toArray(Descriptors.FileDescriptor[]::new);

            // Build the FileDescriptor
            Descriptors.FileDescriptor fileDescriptor =
                    Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);

            // Cache the FileDescriptor for resolving future imports
            fileDescriptorMap.put(fileDescriptor.getName(), fileDescriptor);
        }
    }

    /**
     * Adds well-known Protobuf types (e.g., Timestamp, Any) to the descriptor map.
     */
    private void addWellKnownTypes() {
        // Add each well-known type to the file descriptor map
        fileDescriptorMap.put("google/protobuf/timestamp.proto", com.google.protobuf.TimestampProto.getDescriptor());
        fileDescriptorMap.put("google/protobuf/any.proto", com.google.protobuf.AnyProto.getDescriptor());
        fileDescriptorMap.put("google/protobuf/struct.proto", com.google.protobuf.StructProto.getDescriptor());
        fileDescriptorMap.put("google/protobuf/duration.proto", com.google.protobuf.DurationProto.getDescriptor());
        fileDescriptorMap.put("google/protobuf/empty.proto", com.google.protobuf.EmptyProto.getDescriptor());
        fileDescriptorMap.put("google/protobuf/field_mask.proto", com.google.protobuf.FieldMaskProto.getDescriptor());
    }

    public Descriptors.Descriptor getMessageType(String name) {
        for (Descriptors.FileDescriptor fd : fileDescriptorMap.values()) {
            for (Descriptors.Descriptor messageType : fd.getMessageTypes()) {
                String messageTypeName = messageType.getName();
                String fullName = messageType.getFullName();
                System.out.println(messageTypeName + " == " + fullName);
                if(fullName.equals(name)) {
                    return messageType;
                }
            }
        }
        return null;
    }

    public String getMessageProto(String name) {
        var descriptor = getMessageType(name);
        return getProtoDefinition(descriptor, "\t");
    }

    private String getProtoDefinition(Descriptors.Descriptor descriptor, String indent) {
        StringBuilder protoDef = new StringBuilder();
        protoDef.append(indent).append("message ").append(descriptor.getName()).append(" {\n");

        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            protoDef.append(indent).append("  ");
            protoDef.append(getFieldDefinition(field)).append("\n");
        }

        for (Descriptors.Descriptor nestedDescriptor : descriptor.getNestedTypes()) {
            protoDef.append(getProtoDefinition(nestedDescriptor, indent + "  "));
        }

        protoDef.append(indent).append("}\n");
        return protoDef.toString();
    }

    private String getFieldDefinition(Descriptors.FieldDescriptor field) {
        StringBuilder fieldDef = new StringBuilder();

        // Add field type
        if (field.isRepeated()) {
            fieldDef.append("repeated ");
        }

        switch (field.getType()) {
            case MESSAGE:
                fieldDef.append(field.getMessageType().getName());
                break;
            case ENUM:
                fieldDef.append(field.getEnumType().getName());
                break;
            default:
                fieldDef.append(field.getType().name().toLowerCase());
        }

        // Add field name and number
        fieldDef.append(" ").append(field.getName()).append(" = ").append(field.getNumber()).append(";");

        return fieldDef.toString();
    }
}

