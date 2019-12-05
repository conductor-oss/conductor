/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.conductor.tests.utils;

import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.common.utils.JsonMapperProvider;

public class JsonUtils {

	public static <T> T fromJson(String fileName, Class<T> classObject) throws Exception {

		ObjectMapper objectMapper = new JsonMapperProvider().get();

		InputStream inputStream = ClassLoader.getSystemResourceAsStream(fileName);
		return objectMapper.readValue(inputStream, classObject);

	}
}
