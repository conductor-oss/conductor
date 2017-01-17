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
/**
 * 
 */
package com.netflix.conductor.core;


/**
 * @author Viren
 *
 */
public class WorkflowContext {

	public static final ThreadLocal<WorkflowContext> threadLocal = InheritableThreadLocal.withInitial(() -> new WorkflowContext(""));
	
	private String clientApp;
    
	
	public WorkflowContext(String clientApp){
		this.clientApp = clientApp;
	}
	
	public static WorkflowContext get(){
		return threadLocal.get();
	}

	public static void set(WorkflowContext ctx){
		threadLocal.set(ctx);
	}
	
	public static void unset(){
		threadLocal.remove();
	}

	/**
	 * @return the clientApp
	 */
	public String getClientApp() {
		return clientApp;
	}
	
}
