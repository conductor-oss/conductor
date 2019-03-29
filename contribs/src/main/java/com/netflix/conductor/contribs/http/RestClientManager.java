/*
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
package com.netflix.conductor.contribs.http;

import com.netflix.conductor.contribs.http.HttpTask.Input;
import com.netflix.conductor.core.config.Configuration;
import com.sun.jersey.api.client.Client;

import javax.inject.Singleton;

/**
 * @author Viren
 * Provider for Jersey Client.
 * This class provides a default {@link Client} which can be configured or extended as needed.
 */
@Singleton
public class RestClientManager {

	static final int DEFAULT_READ_TIMEOUT = 150;
	static final int DEFAULT_CONNECT_TIMEOUT = 100;
	static final String HTTP_TASK_READ_TIMEOUT = "http.task.read.timeout";
	static final String HTTP_TASK_CONNECT_TIMEOUT = "http.task.connect.timeout";


	private final ThreadLocal<Client> threadLocalClient;
	private final int defaultReadTimeout;
	private final int defaultConnectTimeout;

	public RestClientManager(Configuration config) {
		this.threadLocalClient = ThreadLocal.withInitial(Client::create);
		this.defaultReadTimeout =config.getIntProperty(HTTP_TASK_READ_TIMEOUT, DEFAULT_READ_TIMEOUT);
		this.defaultConnectTimeout = config.getIntProperty(HTTP_TASK_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
	}

	public Client getClient(Input input) {
		Client client = threadLocalClient.get();
		client.setReadTimeout(defaultReadTimeout);
		client.setConnectTimeout(defaultConnectTimeout);
		return client;
	}
}
