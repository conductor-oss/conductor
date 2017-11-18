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
package com.netflix.conductor.client.worker;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Function;

import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;

/**
 * 
 * @author visingh
 *
 */
public interface Worker {
		
	public String getTaskDefName();
	
	/**
	 * Executes a task and returns the updated task.
	 * @param task Task to be executed.
	 * @return
	 * If the task is not completed yet, return with the status as IN_PROGRESS. 
	 */
	public TaskResult execute(Task task);
	
	/**
	 * Callback used by the WorkflowTaskCoordinator before a task is acke'ed.  
	 * Workers can implement the callback to get notified before the task is ack'ed.
	 * @param task Task to be ack'ed before execution
	 * @return True, if the task should be accepted and acknowledged.  execute() method is called ONLY when this method returns true.  Return false if the task cannot be accepted for whatever reason.  
	 */
	public default boolean preAck(Task task) {
		return true;
	}
	
	/**
	 * Called when the task coordinator fails to update the task to the server.
	 * Client should store the task id (in a database) and retry the update later
	 * @param task Task which cannot be updated back to the server.
	 * 
	 */
	public default void onErrorUpdate(Task task){
		
	}

	/**
	 * Override this method to pause the worker from polling. 
	 * @return true if the worker is paused and no more tasks should be polled from server.
	 */
	public default boolean paused() {
		return PropertyFactory.getBoolean(getTaskDefName(), "paused", false);
	}
	
	/**
	 * 
	 * @return returns NetflixConfiguration.getServerId().  Override this method to app specific rules
	 */
	public default String getIdentity() {
		String serverId;
		try {
			serverId = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			serverId = System.getenv("HOSTNAME");
		}
		if(serverId == null){
			serverId = System.getProperty("user.name");
		}
		return serverId;
	}
	
	/**
	 * 
	 * @return Number of tasks to be polled for.
	 */
	public default int getPollCount() {
		return PropertyFactory.getInteger(getTaskDefName(), "pollCount", 1);
	}
	
	/**
	 * 
	 * @return Interval in millisecond at which the server should be polled for worker tasks.
	 */
	public default int getPollingInterval() {
		return PropertyFactory.getInteger(getTaskDefName(), "pollInterval", 1000);
	}

	/**
	 * 
	 * @return Time to wait when making a poll to workflow server for tasks.  The client will wait for at-least specified seconds for task queue to be "filled".  
	 * Use a higher number here as opposed to more frequent polls.  Helps reduce the excessive calls. 
	 */
	public default int getLongPollTimeoutInMS() {
		return PropertyFactory.getInteger(getTaskDefName(), "longPollTimeout", 100);
	}
	
	public static Worker create(String taskType, Function<Task, TaskResult> executor){
		return new Worker() {
			
			@Override
			public String getTaskDefName() {
				return taskType;
			}
			
			@Override
			public TaskResult execute(Task task) {
				return executor.apply(task);
			}
			
			@Override
			public boolean paused() {
				return Worker.super.paused();
			}
		};
	}
	
}
