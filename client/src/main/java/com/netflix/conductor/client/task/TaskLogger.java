/**
 * 
 */
package com.netflix.conductor.client.task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.conductor.client.http.TaskClient;
import com.netflix.conductor.common.metadata.tasks.Task;

/**
 * @author Viren
 *
 */
public class TaskLogger {

	private static final Logger logger = LoggerFactory.getLogger(TaskLogger.class);
	
	private static final ThreadLocal<String> tl = new InheritableThreadLocal<>();
	
	private static final ThreadLocal<TaskLogger> instance = new InheritableThreadLocal<>();
	
	private ExecutorService es;
	
	private TaskClient client;
	
	public TaskLogger(TaskClient client) {
		this.es = Executors.newFixedThreadPool(1);
		this.client = client;
	}
	
	public void push(Task task) {
		tl.set(task.getTaskId());
		instance.set(this);
	}
	
	public void remove(Task task) {
		tl.remove();
		instance.remove();
	}

	public static void log(Object log) {
		String taskId = tl.get();
		TaskLogger taskLogger = instance.get();		
		taskLogger.es.submit(() -> {
			try {
				taskLogger.client.log(taskId, log.toString());
			}catch(Exception e) {
				logger.error(e.getMessage(), e);
			}
		});
	}
	
}
