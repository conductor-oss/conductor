/**
 * 
 */
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.Map;

import com.netflix.conductor.common.metadata.events.EventHandler.Action;

/**
 * @author Viren
 *
 */
public class EventExecution {

	public enum Status {
		IN_PROGRESS, COMPLETED, FAILED
	}
	
	private String name;
	
	private String event;
	
	private long created;
	
	private Status status;
	
	private Action.Type action;
	
	private Map<String, Object> output = new HashMap<>();

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 * 
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the event
	 */
	public String getEvent() {
		return event;
	}

	/**
	 * @param event the event to set
	 * 
	 */
	public void setEvent(String event) {
		this.event = event;
	}

	/**
	 * @return the created
	 */
	public long getCreated() {
		return created;
	}

	/**
	 * @param created the created to set
	 * 
	 */
	public void setCreated(long created) {
		this.created = created;
	}

	/**
	 * @return the status
	 */
	public Status getStatus() {
		return status;
	}

	/**
	 * @param status the status to set
	 * 
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	/**
	 * @return the action
	 */
	public Action.Type getAction() {
		return action;
	}

	/**
	 * @param action the action to set
	 * 
	 */
	public void setAction(Action.Type action) {
		this.action = action;
	}

	/**
	 * @return the output
	 */
	public Map<String, Object> getOutput() {
		return output;
	}

	/**
	 * @param output the output to set
	 * 
	 */
	public void setOutput(Map<String, Object> output) {
		this.output = output;
	}
	
	
	
}
