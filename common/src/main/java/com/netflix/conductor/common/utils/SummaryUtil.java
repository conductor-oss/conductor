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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SummaryUtil {
	private static final Logger logger = LoggerFactory.getLogger(SummaryUtil.class);
	private static final ObjectMapper objectMapper = new JsonMapperProvider().get();
	private static boolean isSummaryInputOutputJsonSerializationEnabled = false;
  
  /**
	 * Serializes the Workflow or Task's Input/Output object by Java's toString (default), or by
	 * a Json ObjectMapper (@see Configuration.isSummaryInputOutputJsonSerializationEnabled)
	 * @param object the Input or Output Object to serialize
	 * @return the serialized string of the Input or Output object
	 */
	public static String serializeInputOutput(Map<String, Object> object) {
		if (isSummaryInputOutputJsonSerializationEnabled == false) {
			return object.toString();
		}

		try {
			return objectMapper.writeValueAsString(object);
		} catch (JsonProcessingException e) {
			logger.error("The provided value ({}) could not be serialized as Json", object.toString(), e);
			throw new RuntimeException(e);
		}
	}

  /**
   * @param isEnabled (default) false for Java toString, true for Json serialized string
   */
  public static void setSummaryInputOutputJsonSerializationEnabled(boolean isEnabled) {
    isSummaryInputOutputJsonSerializationEnabled = isEnabled;
  }
}
