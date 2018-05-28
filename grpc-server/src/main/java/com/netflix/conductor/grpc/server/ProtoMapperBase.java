package com.netflix.conductor.grpc.server;

import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.proto.WorkflowTaskPb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtoMapperBase {
    public static Value toProto(Object val) {
        Value.Builder builder = Value.newBuilder();

        if (val == null) {
            builder.setNullValue(NullValue.NULL_VALUE);
        } else if (val instanceof Boolean) {
            builder.setBoolValue((Boolean) val);
        } else if (val instanceof Double) {
            builder.setNumberValue((Double) val);
        } else if (val instanceof String) {
            builder.setStringValue((String) val);
        } else if (val instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) val;
            Struct.Builder struct = Struct.newBuilder();
            for (Map.Entry<String, Object> pair : map.entrySet()) {
                struct.putFields(pair.getKey(), toProto(pair.getValue()));
            }
            builder.setStructValue(struct.build());
        } else if (val instanceof List) {
            ListValue.Builder list = ListValue.newBuilder();
            for (Object obj : (List<Object>)val) {
                list.addValues(toProto(obj));
            }
            builder.setListValue(list.build());
        } else {
            throw new ClassCastException("cannot map to Value type: "+val);
        }
        return builder.build();
    }

    public static Object fromProto(Value any) {
        switch (any.getKindCase()) {
            case NULL_VALUE:
                return null;
            case BOOL_VALUE:
                return any.getBoolValue();
            case NUMBER_VALUE:
                return any.getNumberValue();
            case STRING_VALUE:
                return any.getStringValue();
            case STRUCT_VALUE:
                Struct struct = any.getStructValue();
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<String, Value> pair : struct.getFieldsMap().entrySet()) {
                    map.put(pair.getKey(), fromProto(pair.getValue()));
                }
                return map;
            case LIST_VALUE:
                List<Object> list = new ArrayList<>();
                for (Value val : any.getListValue().getValuesList()) {
                    list.add(fromProto(val));
                }
                return list;
            default:
                throw new ClassCastException("unset Value element: "+any);
        }
    }

    public static List<WorkflowTask> fromProto(WorkflowTaskPb.WorkflowTask.WorkflowTaskList list) {
        return null;
    }

    public static WorkflowTaskPb.WorkflowTask.WorkflowTaskList toProto(List<WorkflowTask> list) {
        return null;
    }
}
