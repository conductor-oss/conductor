/**
 * 
 */
package com.netflix.conductor.client.sample;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.client.task.WorkflowTaskCoordinator;
import com.netflix.conductor.client.worker.Worker;
import com.netflix.discovery.EurekaClient;

/**
 * @author Viren
 *
 */
public class Main {

	public static void main(String[] args) {
		
		EurekaClient eurekaClient = null;			//Optional and can be null
		
		TaskClient taskClient = new TaskClient();
		taskClient.setRootURI("http://localhost:8080/api/");		//Point this to the server API
		
		int threadCount = 2;			//number of threads used to execute workers.  To avoid starvation, should be same or more than number of workers
		
		Worker worker1 = new SampleWorker("task_1");
		Worker worker2 = new SampleWorker("task_5");
		
		//Initialize the task coordinator
		WorkflowTaskCoordinator coordinator = new WorkflowTaskCoordinator(eurekaClient , taskClient, threadCount, worker1, worker2);
		
		//Start for polling and execution of the tasks
		coordinator.init();

	}

}
