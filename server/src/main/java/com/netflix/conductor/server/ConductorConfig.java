/**
 * 
 */
package com.netflix.conductor.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import com.netflix.conductor.core.config.Configuration;

/**
 * @author Viren
 *
 */
public class ConductorConfig implements Configuration {

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
		return getProperty("APP_ID", "");
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
		String val = System.getProperty(key);
		int value  = defaultValue;
		try{
			value = Integer.parseInt(val);
		}catch(Exception e){}
		return value;
	}

	@Override
	public String getProperty(String key, String defaultValue) {
		return Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
	}

	@Override
	public Map<String, Object> getAll() {
		Map<String, Object> map = new HashMap<>();
		Properties props = System.getProperties();
		props.entrySet().forEach(entry -> map.put(entry.getKey().toString(), entry.getValue()));
		return map;
	}

}
