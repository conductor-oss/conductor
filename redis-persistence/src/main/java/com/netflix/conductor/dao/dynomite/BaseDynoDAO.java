/**
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
package com.netflix.conductor.dao.dynomite;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.conductor.core.config.Configuration;

public class BaseDynoDAO {

	private static final String NAMESPACE_SEP = ".";

	protected DynoProxy dynoClient;

	protected ObjectMapper om;

	protected String domain;

	private Configuration config;

	protected Logger logger = LoggerFactory.getLogger(getClass());

	protected BaseDynoDAO(DynoProxy dynoClient, ObjectMapper om, Configuration config) {
		this.dynoClient = dynoClient;
		this.om = om;
		this.config = config;
		this.domain = config.getProperty("workflow.dyno.keyspace.domain", null);
	}

	public String nsKey(String... nsValues) {
		String rootNamespace = config.getProperty("workflow.namespace.prefix", null);
		String namespacedKey = rootNamespace + NAMESPACE_SEP;
		String stack = config.getStack();
		if (stack != null && !stack.isEmpty()) {
			namespacedKey = namespacedKey + stack + NAMESPACE_SEP;
		}
		if (domain != null && !domain.isEmpty()) {
			namespacedKey = namespacedKey + domain + NAMESPACE_SEP;
		}
		for (int i = 0; i < nsValues.length; i++) {
			namespacedKey = namespacedKey + nsValues[i];
			if (i < nsValues.length - 1) {
				namespacedKey = namespacedKey + NAMESPACE_SEP;
			}
		}
		//QUES cpewf.devint.test.WORKFLOW.UUID isSystemTask the stack in here same as the NETFLIX_STACK ? and what about the domain?
		//Looking at the data saved in dynomite cpewf.WORKFLOW.UUID
		return namespacedKey;
	}

	public DynoProxy getDyno() {
		return dynoClient;
	}
	
	protected String toJson(Object value) {
		try {
			return om.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected <T>T readValue(String json, Class<T> clazz) {
		try {
			return om.readValue(json, clazz);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
