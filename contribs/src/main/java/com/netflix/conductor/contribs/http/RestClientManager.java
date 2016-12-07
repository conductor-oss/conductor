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
package com.netflix.conductor.contribs.http;

import javax.inject.Singleton;

import com.netflix.conductor.contribs.http.HttpTask.Input;
import com.sun.jersey.api.client.Client;

/**
 * @author Viren
 * Provider for Jersey Client.  This class provides an 
 */
@Singleton
public class RestClientManager {

	private Client defaultClient = Client.create();
	
	public Client getClient(Input input) {
		return defaultClient;
	}
}
