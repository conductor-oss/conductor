/*
 * Copyright 2023 Conductor authors
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.grpc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.google.protobuf.Value;
import com.netflix.conductor.common.metadata.workflow.WorkflowTask;
import com.netflix.conductor.proto.WorkflowTaskPb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestProtoMapper {
    private final ProtoMapper mapper = ProtoMapper.INSTANCE;

    /** Convert a Java object to a protobuf Value and back to its Java form. */
    private Object roundTrip(Object value) {
        return mapper.fromProto(mapper.toProto(value));
    }

    @Test
    public void workflowTaskToProto() {
        final WorkflowTask taskWithDefaultRetryCount = new WorkflowTask();
        final WorkflowTask taskWith1RetryCount = new WorkflowTask();
        taskWith1RetryCount.setRetryCount(1);
        final WorkflowTask taskWithNoRetryCount = new WorkflowTask();
        taskWithNoRetryCount.setRetryCount(0);
        assertEquals(-1, mapper.toProto(taskWithDefaultRetryCount).getRetryCount());
        assertEquals(1, mapper.toProto(taskWith1RetryCount).getRetryCount());
        assertEquals(0, mapper.toProto(taskWithNoRetryCount).getRetryCount());
    }

    @Test
    public void workflowTaskFromProto() {
        final WorkflowTaskPb.WorkflowTask taskWithDefaultRetryCount =
                WorkflowTaskPb.WorkflowTask.newBuilder().build();
        final WorkflowTaskPb.WorkflowTask taskWith1RetryCount =
                WorkflowTaskPb.WorkflowTask.newBuilder().setRetryCount(1).build();
        final WorkflowTaskPb.WorkflowTask taskWithNoRetryCount =
                WorkflowTaskPb.WorkflowTask.newBuilder().setRetryCount(-1).build();
        assertEquals(
                Integer.valueOf(0), mapper.fromProto(taskWithDefaultRetryCount).getRetryCount());
        assertEquals(1, mapper.fromProto(taskWith1RetryCount).getRetryCount().intValue());
        assertNull(mapper.fromProto(taskWithNoRetryCount).getRetryCount());
    }

    // -------------------------------------------------------------------------
    // Scalar Value conversion
    // -------------------------------------------------------------------------

    @Test
    public void scalarNull() {
        assertNull(roundTrip(null));
    }

    @Test
    public void scalarBoolean() {
        assertEquals(true, roundTrip(true));
        assertEquals(false, roundTrip(false));
    }

    @Test
    public void scalarString() {
        assertEquals("hello", roundTrip("hello"));
        assertEquals("", roundTrip(""));
    }

    /**
     * Every numeric type must serialize (protobuf Struct only has a double number_value, so they
     * all come back as Double). Before the fix, only Double was accepted and Integer/Long/Float/
     * BigDecimal/BigInteger threw ClassCastException — the common case since Jackson deserializes
     * JSON integers in a Map&lt;String,Object&gt; as Integer/Long.
     */
    @Test
    public void scalarNumericTypesAllConvert() {
        assertEquals(3.0, roundTrip(3));                       // Integer
        assertEquals(3.0, roundTrip(3L));                      // Long
        assertEquals(3.0, roundTrip((short) 3));               // Short
        assertEquals(3.0, roundTrip((byte) 3));                // Byte
        assertEquals(3.5, roundTrip(3.5f));                    // Float
        assertEquals(3.5, roundTrip(3.5d));                    // Double
        assertEquals(3.5, roundTrip(new BigDecimal("3.5")));   // BigDecimal
        assertEquals(10.0, roundTrip(BigInteger.TEN));         // BigInteger
    }

    @Test
    public void scalarNumberAlwaysReturnsDouble() {
        // documents the (lossy, asymmetric) round-trip: Integer in -> Double out
        Object back = roundTrip(42);
        assertTrue("expected Double, got " + back.getClass(), back instanceof Double);
        assertEquals(42.0, back);
    }

    // -------------------------------------------------------------------------
    // Map / Struct conversion
    // -------------------------------------------------------------------------

    @Test
    public void mapWithMixedScalarTypes() {
        Map<String, Object> input = new HashMap<>();
        input.put("int", 1);
        input.put("long", 2L);
        input.put("double", 3.5);
        input.put("bool", true);
        input.put("str", "x");
        input.put("nil", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(input);

        assertEquals(1.0, out.get("int"));
        assertEquals(2.0, out.get("long"));
        assertEquals(3.5, out.get("double"));
        assertEquals(true, out.get("bool"));
        assertEquals("x", out.get("str"));
        assertNull(out.get("nil"));
        assertTrue(out.containsKey("nil"));
    }

    @Test
    public void emptyMapRoundTrips() {
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(new HashMap<String, Object>());
        assertTrue(out.isEmpty());
    }

    @Test
    public void emptyListRoundTrips() {
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) roundTrip(new ArrayList<>());
        assertTrue(out.isEmpty());
    }

    /** Deeply nested Map -> List -> Map -> scalar, exercising recursion. */
    @Test
    public void deeplyNestedStructure() {
        Map<String, Object> leaf = new HashMap<>();
        leaf.put("count", 7);
        leaf.put("enabled", false);

        List<Object> list = new ArrayList<>(Arrays.asList(1, "two", leaf, Arrays.asList(8, 9)));

        Map<String, Object> root = new HashMap<>();
        root.put("items", list);
        root.put("name", "root");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(root);

        assertEquals("root", out.get("name"));
        @SuppressWarnings("unchecked")
        List<Object> outList = (List<Object>) out.get("items");
        assertEquals(1.0, outList.get(0));
        assertEquals("two", outList.get(1));
        @SuppressWarnings("unchecked")
        Map<String, Object> outLeaf = (Map<String, Object>) outList.get(2);
        assertEquals(7.0, outLeaf.get("count"));
        assertEquals(false, outLeaf.get("enabled"));
        assertEquals(Arrays.asList(8.0, 9.0), outList.get(3));
    }

    /** Non-String keys are coerced via String.valueOf (JSON object keys are always strings). */
    @Test
    public void nonStringMapKeyIsCoercedToString() {
        Map<Object, Object> input = new HashMap<>();
        input.put(42, "answer");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(input);
        assertEquals("answer", out.get("42"));
    }

    /** A null key must not throw; it is coerced to the string "null". */
    @Test
    public void nullMapKeyIsCoercedAndDoesNotThrow() {
        Map<Object, Object> input = new HashMap<>();
        input.put(null, "v");

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(input);
        assertEquals("v", out.get("null"));
    }

    @Test
    public void listOfMixedTypes() {
        List<Object> input = Arrays.asList(1, 2L, 3.5, "s", true, null);
        @SuppressWarnings("unchecked")
        List<Object> out = (List<Object>) roundTrip(input);
        assertEquals(Arrays.asList(1.0, 2.0, 3.5, "s", true, null), out);
    }

    @Test
    public void linkedHashMapIsSupported() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("a", 1);
        input.put("b", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) roundTrip(input);
        assertEquals(1.0, out.get("a"));
        assertEquals(2.0, out.get("b"));
    }

    /** Genuinely non-JSON types are still rejected with a clear error. */
    @Test
    public void nonJsonTypeIsRejected() {
        try {
            mapper.toProto((Object) new Date());
            fail("expected ClassCastException for a non-JSON-native type");
        } catch (ClassCastException expected) {
            assertTrue(expected.getMessage().contains("cannot map to Value type"));
        }
    }

    @Test
    public void nonJsonTypeNestedInMapIsRejected() {
        Map<String, Object> input = new HashMap<>();
        input.put("when", new Date());
        try {
            mapper.toProto((Object) input);
            fail("expected ClassCastException for a non-JSON-native nested value");
        } catch (ClassCastException expected) {
            assertTrue(expected.getMessage().contains("cannot map to Value type"));
        }
    }

    @Test
    public void numberValueIsTypedAsNumberInProto() {
        Value v = mapper.toProto((Object) 5);
        assertEquals(Value.KindCase.NUMBER_VALUE, v.getKindCase());
        assertEquals(5.0, v.getNumberValue(), 0.0);
    }
}
