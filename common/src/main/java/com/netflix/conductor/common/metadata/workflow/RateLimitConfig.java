/*
 * Copyright 2023 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.common.metadata.workflow;

import com.netflix.conductor.annotations.protogen.ProtoEnum;
import com.netflix.conductor.annotations.protogen.ProtoField;
import com.netflix.conductor.annotations.protogen.ProtoMessage;

/** Rate limit configuration for workflows */
@ProtoMessage
public class RateLimitConfig {

    /** Rate limit policy defining how to handle requests exceeding the limit */
    @ProtoEnum
    public enum RateLimitPolicy {
        /** Queue the request until capacity is available */
        QUEUE,
        /** Reject the request immediately */
        REJECT
    }

    /**
     * Key that defines the rate limit. Rate limit key is a combination of workflow payload such as
     * name, or correlationId etc.
     */
    @ProtoField(id = 1)
    private String rateLimitKey;

    /** Number of concurrently running workflows that are allowed per key */
    @ProtoField(id = 2)
    private int concurrentExecLimit;

    /** Policy to apply when rate limit is exceeded */
    @ProtoField(id = 3)
    private RateLimitPolicy policy = RateLimitPolicy.QUEUE;

    public String getRateLimitKey() {
        return rateLimitKey;
    }

    public void setRateLimitKey(String rateLimitKey) {
        this.rateLimitKey = rateLimitKey;
    }

    public int getConcurrentExecLimit() {
        return concurrentExecLimit;
    }

    public void setConcurrentExecLimit(int concurrentExecLimit) {
        this.concurrentExecLimit = concurrentExecLimit;
    }

    public RateLimitPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(RateLimitPolicy policy) {
        this.policy = policy;
    }
}
