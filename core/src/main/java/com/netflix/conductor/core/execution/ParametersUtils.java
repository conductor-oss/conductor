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

import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.core.config.Configuration;

/**
 * 
 * 
 *
 */
public class ParametersUtils {
	
	private Configuration config;
	
	public enum SystemParameters {
		CPEWF_TASK_ID,
		NETFLIX_ENV,
		NETFLIX_STACK
	}
	
	public ParametersUtils(Configuration config) {
		this.config = config;
	}

	public Object replaceVariables(String paramString, Workflow workflow, String taskId){
		// If the parameter String is "v1 v2 v3" then make sure split it first
		//String[] values = paramString.split("\\s+");
		String[] values = paramString.split("(?=\\$\\{)|(?<=\\s+)|(?<=\\})");
		Object[] convertedValues = new Object[values.length];
		for(int i=0; i < values.length; i++){
			convertedValues[i] = values[i];
			if(values[i].startsWith("${") && values[i].endsWith("}")){
				// First check if it is on of the SystemParameters
				String paramName = values[i].substring(2, values[i].length()-1);
				System.out.println(paramName);
				if(contains(paramName)){
					String sysValue = getSystemParametersValue(paramName, workflow, taskId);
					if(sysValue != null){
						convertedValues[i] = sysValue;
					}
					
				} else {
					String paramPath = values[i];
					Map<String, Object> workflowInput = workflow.getInput();
					Map<String, Object> workflowOutput = workflow.getOutput();
					String[] paramPathComponents = paramPath.split("\\.");
					Preconditions.checkArgument(paramPathComponents.length == 3, "Invalid input expression for " + paramName + ", expression=" + paramPath);
					
					String source = paramPathComponents[0].substring(2);	//workflow, or task reference name
					String type = paramPathComponents[1];	//input/output
					String name = paramPathComponents[2];	//name of the parameter
					name = name.substring(0, name.length()-1);
					if("workflow".equals(source)){
						if("input".equals(type)){
							convertedValues[i] = workflowInput.get(name);	
						}else{
							convertedValues[i] = workflowOutput.get(name);
						}						
					}else{
						Task task = workflow.getTaskByRefName(source);
						if(task != null){
							if("input".equals(type)){
								convertedValues[i] = task.getInputData().get(name);
							}else{
								convertedValues[i] = task.getOutputData().get(name);
							}
						}else{
							convertedValues[i] = null;
						}
					}
				}
			
			}
		}
		
		Object retObj = convertedValues[0];
		// If the parameter String was "v1 v2 v3" then make sure to stitch it back
		if(convertedValues.length > 1){
			for(int i=0; i<convertedValues.length; i++){
				Object val = convertedValues[i];
				if(val == null){
					val = "";
				}
				if(i == 0){
					retObj = val.toString();
				} else {
					retObj = retObj.toString() + "" + val.toString();				
				}
			}
			
		}
		return retObj;
	}
	
	private String getSystemParametersValue(String sysParam, Workflow wf, String taskId){
		if("CPEWF_TASK_ID".equals(sysParam)) {
			return taskId;
		}
		String value = System.getProperty(sysParam);
		if(value == null) {
			value = System.getenv(sysParam);
		}
		return value;
	}
	
	private boolean contains(String test) {
	    for (SystemParameters c : SystemParameters.values()) {
	        if (c.name().equals(test)) {
	            return true;
	        }
	    }
	    String value = Optional.ofNullable(System.getProperty(test)).orElse(Optional.ofNullable(System.getenv(test)).orElse(null));
	    return value != null;
	}
}
