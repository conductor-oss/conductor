/**
 * 
 */
package com.netflix.conductor.client.sample;

import com.netflix.conductor.client.worker.Worker;
import com.netflix.conductor.common.metadata.tasks.Task;
import com.netflix.conductor.common.metadata.tasks.TaskResult;
import com.netflix.conductor.common.metadata.tasks.TaskResult.Status;

/**
 * @author Viren
 * 
 */
public class SampleWorker implements Worker {

	private String taskDefName;
	
	public SampleWorker(String taskDefName) {
		this.taskDefName = taskDefName;
	}
	
	@Override
	public String getTaskDefName() {
		return taskDefName;
	}

	@Override
	public TaskResult execute(Task task) {
		
		System.out.printf("Executing %s\n", taskDefName);
		
		TaskResult result = new TaskResult(task);
		result.setStatus(Status.COMPLETED);
		
		//Register the output of the task
		result.getOutputData().put("outputKey1", "value");
		result.getOutputData().put("oddEven", 1);
		result.getOutputData().put("mod", 4);
		
		return result;
	}

}
