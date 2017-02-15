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
package com.netflix.conductor.common.metadata.events;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Viren
 * Defines an event handler
 */
public class EventHandler {

	private String name;
	
	private String event;
	
	private String condition;
	
	private List<Action> actions = new LinkedList<>();
	
	private boolean active;
	
	public EventHandler() {
		
	}

	/**
	 * @return the name MUST be unique within a conductor instance
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
	 * @return the condition
	 */
	public String getCondition() {
		return condition;
	}

	/**
	 * @param condition the condition to set
	 * 
	 */
	public void setCondition(String condition) {
		this.condition = condition;
	}

	/**
	 * @return the actions
	 */
	public List<Action> getActions() {
		return actions;
	}

	/**
	 * @param actions the actions to set
	 * 
	 */
	public void setActions(List<Action> actions) {
		this.actions = actions;
	}

	/**
	 * @return the active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * @param active if set to false, the event handler is deactivated
	 * 
	 */
	public void setActive(boolean active) {
		this.active = active;
	}


	public static class Action {
		
		public enum Type { start_workflow }
		
		private Type action;
		
		private StartWorkflow start_workflow;
		
		private String handlerName;
		
		private String event;

		/**
		 * @return the action
		 */
		public Type getAction() {
			return action;
		}

		/**
		 * @param action the action to set
		 * 
		 */
		public void setAction(Type action) {
			this.action = action;
		}

		/**
		 * @return the start_workflow
		 */
		public StartWorkflow getStart_workflow() {
			return start_workflow;
		}

		/**
		 * @param start_workflow the start_workflow to set
		 * 
		 */
		public void setStart_workflow(StartWorkflow start_workflow) {
			this.start_workflow = start_workflow;
		}

		/**
		 * @return the handlerName
		 */
		public String getHandlerName() {
			return handlerName;
		}

		/**
		 * @param handlerName the handlerName to set
		 * 
		 */
		public void setHandlerName(String handlerName) {
			this.handlerName = handlerName;
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
		
		
		
	}
	
	public static class StartWorkflow {
		
		private String name;
		
		private Integer version;
		
		private String correlationId;
		
		private Map<String, Object> input = new HashMap<>();

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
		 * @return the version
		 */
		public Integer getVersion() {
			return version;
		}

		/**
		 * @param version the version to set
		 * 
		 */
		public void setVersion(Integer version) {
			this.version = version;
		}

		
		/**
		 * @return the correlationId
		 */
		public String getCorrelationId() {
			return correlationId;
		}

		/**
		 * @param correlationId the correlationId to set
		 * 
		 */
		public void setCorrelationId(String correlationId) {
			this.correlationId = correlationId;
		}

		/**
		 * @return the input
		 */
		public Map<String, Object> getInput() {
			return input;
		}

		/**
		 * @param input the input to set
		 * 
		 */
		public void setInput(Map<String, Object> input) {
			this.input = input;
		}
		
		
	}
	
}
