package com.netflix.conductor.common.metadata.tasks;

import com.github.vmg.protogen.annotations.*;

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
@ProtoMessage
public class PollData {
	@ProtoField(id = 1)
	String queueName;

	@ProtoField(id = 2)
	String domain;

	@ProtoField(id = 3)
	String workerId;

	@ProtoField(id = 4)
	long lastPollTime;
	
	public PollData() {
		super();
	}

	public PollData(String queueName, String domain, String workerId, long lastPollTime) {
		super();
		this.queueName = queueName;
		this.domain = domain;
		this.workerId = workerId;
		this.lastPollTime = lastPollTime;
	}
	
	public String getQueueName() {
		return queueName;
	}
	public void setQueueName(String queueName) {
		this.queueName = queueName;
	}
	public String getDomain() {
		return domain;
	}
	public void setDomain(String domain) {
		this.domain = domain;
	}
	public String getWorkerId() {
		return workerId;
	}
	public void setWorkerId(String workerId) {
		this.workerId = workerId;
	}
	public long getLastPollTime() {
		return lastPollTime;
	}
	public void setLastPollTime(long lastPollTime) {
		this.lastPollTime = lastPollTime;
	}

	
	@Override
	public synchronized int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((domain == null) ? 0 : domain.hashCode());
		result = prime * result + (int) (lastPollTime ^ (lastPollTime >>> 32));
		result = prime * result + ((queueName == null) ? 0 : queueName.hashCode());
		result = prime * result + ((workerId == null) ? 0 : workerId.hashCode());
		return result;
	}

	@Override
	public synchronized boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PollData other = (PollData) obj;
		if (domain == null) {
			if (other.domain != null)
				return false;
		} else if (!domain.equals(other.domain))
			return false;
		if (lastPollTime != other.lastPollTime)
			return false;
		if (queueName == null) {
			if (other.queueName != null)
				return false;
		} else if (!queueName.equals(other.queueName))
			return false;
		if (workerId == null) {
			if (other.workerId != null)
				return false;
		} else if (!workerId.equals(other.workerId))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "PollData{" +
				"queueName='" + queueName + '\'' +
				", domain='" + domain + '\'' +
				", workerId='" + workerId + '\'' +
				", lastPollTime=" + lastPollTime +
				'}';
	}
}
