package com.netflix.conductor.core.utils;

import com.netflix.conductor.common.metadata.tasks.Task;

public class QueueUtils {
	public static final String DOMAIN_SEPARATOR = ":";
	
	public static String getQueueName(Task task){
		return getQueueName(task.getTaskType(), task.getDomain());
	}
	public static String getQueueName(String taskType, String domain){
		String queueName = null;
		if(domain == null){
			queueName = taskType;
		} else {
			queueName = domain + DOMAIN_SEPARATOR + taskType;
		}
		return queueName;
	}

}
