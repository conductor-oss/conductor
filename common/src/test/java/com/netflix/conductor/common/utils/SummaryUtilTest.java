/**
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.netflix.conductor.common.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SummaryUtilTest {
    private Map<String, Object> testObject;

    public SummaryUtilTest() {
        Map<String, Object> child = new HashMap<String, Object>();
        child.put("testStr", "childTestStr");
        
        Map<String, Object> obj = new HashMap<String, Object>();
        obj.put("testStr", "stringValue");
        obj.put("testArray", new ArrayList<Integer>(Arrays.asList(1,2,3)));
        obj.put("testObj", child);
        obj.put("testNull", null);
      
        this.testObject = obj;
    }

    @Test
    public void testSerializeInputOutput_defaultToString() throws Exception {
        SummaryUtil.setSummaryInputOutputJsonSerializationEnabled(false);
        String serialized = SummaryUtil.serializeInputOutput(this.testObject);
        
        assertEquals("The Java.toString() Serialization should match the serialized Test Object", 
            this.testObject.toString(), serialized);
    }

    @Test
    public void testSerializeInputOutput_jsonSerializationEnabled() throws Exception {
        ObjectMapper objectMapper = new JsonMapperProvider().get();
        SummaryUtil.setSummaryInputOutputJsonSerializationEnabled(true);
        String serialized = SummaryUtil.serializeInputOutput(this.testObject);
        
        assertEquals("The ObjectMapper Json Serialization should match the serialized Test Object", 
            objectMapper.writeValueAsString(this.testObject), serialized);
    }

}
