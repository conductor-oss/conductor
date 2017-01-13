/**
 * 
 */
package com.netflix.conductor.server;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Optional;

import com.netflix.conductor.core.config.Configuration;

/**
 * @author Viren
 *
 */
public class ConductorConfig implements Configuration {

	@Override
	public int getSweepFrequency() {
		return 30;
	}

	@Override
	public boolean disableSweep() {
		return false;
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
		return "us-east-1";
	}

	@Override
	public String getAvailabilityZone() {
		return "us-east-1c";
	}

	@Override
	public int getIntProperty(String name, int defaultValue) {
		return defaultValue;
	}

	@Override
	public String getProperty(String key, String defaultValue) {
		return Optional.ofNullable(System.getProperty(key)).orElse(defaultValue);
	}

	@Override
	public Map<String, Object> getAll() {
		return null;
	}

}
