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
package com.netflix.conductor.tests.utils;
/**
 * 
 */


import java.util.Map;
import java.util.Optional;

import com.netflix.conductor.core.config.Configuration;

/**
 * @author Viren
 *
 */
public class TestConfiguration implements Configuration {
	
	static {
		System.setProperty("workflow.elasticsearch.url", "localhost:9300");
		System.setProperty("workflow.elasticsearch.index.name", "conductor");
	}

	@Override
	public int getSweepFrequency() {
		return 1;
	}

	@Override
	public boolean disableSweep() {
		return false;
	}

	@Override
	public String getServerId() {
		return "server_id";
	}

	@Override
	public String getEnvironment() {
		return "test";
	}

	@Override
	public String getStack() {
		return "junit";
	}

	@Override
	public String getAppId() {
		return "workflow";
	}

	@Override
	public String getProperty(String key, String defaultValue) {
		return Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
	}
	
	@Override
	public String getAvailabilityZone() {
		return "us-east-1a";
	}
	
	@Override
	public int getIntProperty(String string, int def) {
		return 100;
	}
	
	@Override
	public String getRegion() {
		return "us-east-1";
	}
	
	@Override
	public Map<String, Object> getAll() {
		return null;
	}
}
