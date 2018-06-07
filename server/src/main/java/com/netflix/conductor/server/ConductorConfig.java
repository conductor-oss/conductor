/**
 * Copyright 2017 Netflix, Inc.
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
/**
 * 
 */
package com.netflix.conductor.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.netflix.conductor.core.config.Configuration;

/**
 * @author Viren
 *
 */
public class ConductorConfig implements Configuration {

	private static Logger logger = LoggerFactory.getLogger(ConductorConfig.class);
	
	@Override
	public int getSweepFrequency() {
		return getIntProperty("decider.sweep.frequency.seconds", 30);
	}

	@Override
	public boolean disableSweep() {
		String disable = getProperty("decider.sweep.disable", "false");
		return Boolean.getBoolean(disable);
	}

	@Override
	public boolean disableAsyncWorkers() {
		String disable = getProperty("conductor.disable.async.workers", "false");
		return Boolean.getBoolean(disable);
	}
	
	@Override
	public String getServerId() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "unknown";
		}
	}

	@Override
	public String getEnvironment() {
		return getProperty("environment", "test");
	}

	@Override
	public String getStack() {
		return getProperty("STACK", "test");
	}

	@Override
	public String getAppId() {
		return getProperty("APP_ID", "conductor");
	}

	@Override
	public String getRegion() {
		return getProperty("EC2_REGION", "us-east-1");
	}

	@Override
	public String getAvailabilityZone() {
		return getProperty("EC2_AVAILABILITY_ZONE", "us-east-1c");
	}

	@Override
	public int getIntProperty(String key, int defaultValue) {
		String val = getProperty(key, Integer.toString(defaultValue));
		try{
			defaultValue = Integer.parseInt(val);
		}catch(NumberFormatException e){}
		return defaultValue;
	}

	@Override
	public long getLongProperty(String key, long defaultValue) {
		String val = getProperty(key, Long.toString(defaultValue));
		try {
			defaultValue = Long.parseLong(val);
		} catch (NumberFormatException e) {
			logger.error("Error parsing the Long value for Key:{} , returning a default value: {}", key, defaultValue);
		}
		return defaultValue;
	}

	@Override
	public String getProperty(String key, String defaultValue) {

		String val = null;
		try{
			val = System.getenv(key.replace('.','_'));
			if (val == null || val.isEmpty()) {
				val = Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
			}
		}catch(Exception e){
			logger.error(e.getMessage(), e);
		}
		return val;
	}

	@Override
	public Map<String, Object> getAll() {
		Map<String, Object> map = new HashMap<>();
		Properties props = System.getProperties();
		props.entrySet().forEach(entry -> map.put(entry.getKey().toString(), entry.getValue()));
		return map;
	}

	@Override
	public List<AbstractModule> getAdditionalModules() {
		
		String additionalModuleClasses = getProperty("conductor.additional.modules", null);
		if(!StringUtils.isEmpty(additionalModuleClasses)) {
			try {
				List<AbstractModule> modules = new LinkedList<>();
				String[] classes = additionalModuleClasses.split(",");
				for(String clazz : classes) {
					Object moduleObj = Class.forName(clazz).newInstance();
					if(moduleObj instanceof AbstractModule) {
						AbstractModule abstractModule = (AbstractModule)moduleObj;
						modules.add(abstractModule);
					} else {
						logger.error(clazz + " does not implement " + AbstractModule.class.getName() + ", skipping...");
					}
				}
				return modules;
			}catch(Exception e) {
				logger.warn(e.getMessage(), e);
			}
		}
		return null;
	}
}
