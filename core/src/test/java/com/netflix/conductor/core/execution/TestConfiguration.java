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
package com.netflix.conductor.core.execution;
/**
 * 
 */


import java.util.Map;

import com.netflix.conductor.core.config.Configuration;

/**
 * @author Viren
 *
 */
public class TestConfiguration implements Configuration {

	@Override
	public int getSweepFrequency() {
		return 1;
	}

	@Override
	public boolean disableSweep() {
		return false;
	}
	
	@Override
	public boolean disableAsyncWorkers() {
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
	public String getProperty(String string, String def) {
		return "dummy";
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

	@Override
	public long getLongProperty(String name, long defaultValue) {
		return 1000000l;
	}
}
