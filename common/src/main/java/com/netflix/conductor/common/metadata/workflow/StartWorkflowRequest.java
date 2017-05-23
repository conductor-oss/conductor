package com.netflix.conductor.common.metadata.workflow;

import java.util.HashMap;
import java.util.Map;

public class StartWorkflowRequest {
	private String name;
	private Integer version;
	private String correlationId;
	private Map<String, Object> input = new HashMap<>();
	private Map<String, String> taskToDomain = new HashMap<>();
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public StartWorkflowRequest withName(String name) {
		this.name = name;
		return this;
	}	
	public Integer getVersion() {
		return version;
	}
	public void setVersion(Integer version) {
		this.version = version;
	}
	public StartWorkflowRequest withVersion(Integer version) {
		this.version = version;
		return this;
	}
	public String getCorrelationId() {
		return correlationId;
	}
	public void setCorrelationId(String correlationId) {
		this.correlationId = correlationId;
	}
	public StartWorkflowRequest withCorrelationId(String correlationId) {
		this.correlationId = correlationId;
		return this;
	}
	public Map<String, Object> getInput() {
		return input;
	}
	public void setInput(Map<String, Object> input) {
		this.input = input;
	}
	public StartWorkflowRequest withInput(Map<String, Object> input) {
		this.input = input;
		return this;
	}
	public Map<String, String> getTaskToDomain() {
		return taskToDomain;
	}
	public void setTaskToDomain(Map<String, String> taskToDomain) {
		this.taskToDomain = taskToDomain;
	}
	public StartWorkflowRequest withTaskToDomain(Map<String, String> taskToDomain) {
		this.taskToDomain = taskToDomain;
		return this;
	}
	
	
}
