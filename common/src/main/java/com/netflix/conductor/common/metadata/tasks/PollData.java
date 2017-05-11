package com.netflix.conductor.common.metadata.tasks;

public class PollData {
	String queueName;
	String domain;
	String workerId;
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
	public boolean equals(Object obj) {
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
	
	
}
