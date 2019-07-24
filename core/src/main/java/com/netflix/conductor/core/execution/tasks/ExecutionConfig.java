package com.netflix.conductor.core.execution.tasks;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

class ExecutionConfig {

	ExecutorService service;
	LinkedBlockingQueue<Runnable> workerQueue;

	public ExecutionConfig(ExecutorService service, LinkedBlockingQueue<Runnable> workerQueue) {
		this.service = service;
		this.workerQueue = workerQueue;
	}

}