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
	
	
	private static ExecutorService es = Executors.newFixedThreadPool(1);
	
	static TaskClient client;
	
	static void push(Task task) {
		tl.set(task.getTaskId());
	}
	
	static void remove(Task task) {
		tl.remove();
	}

	public static void log(Object log) {
		String taskId = tl.get();
		es.submit(() -> {
			try {
				client.log(taskId, log.toString());
			}catch(Exception e) {
				logger.error(e.getMessage(), e);
			}
		});
	}
	
}
