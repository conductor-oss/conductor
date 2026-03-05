/*
 * Copyright 2026 Conductor Authors.
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
package org.conductoross.conductor.ai.video;

import org.springframework.ai.model.MutableResponseMetadata;

/**
 * Metadata about a video generation response.
 *
 * <p>Mirrors Spring AI's {@code ImageResponseMetadata} pattern. Extends {@link
 * MutableResponseMetadata} to provide a key-value metadata store alongside typed convenience
 * accessors for common video generation metadata (job ID, status, error message).
 *
 * <p>Video generation is inherently asynchronous, so the metadata tracks the lifecycle of a
 * generation job: submission (jobId), progress (status), and completion or failure (errorMessage).
 */
public class VideoResponseMetadata extends MutableResponseMetadata {

    /** Key for storing the provider's job/operation identifier. */
    public static final String KEY_JOB_ID = "jobId";

    /** Key for storing the current job status (PENDING, PROCESSING, COMPLETED, FAILED). */
    public static final String KEY_STATUS = "status";

    /** Key for storing an error message when the job fails. */
    public static final String KEY_ERROR_MESSAGE = "errorMessage";

    private final Long created;

    public VideoResponseMetadata() {
        this(System.currentTimeMillis());
    }

    public VideoResponseMetadata(Long created) {
        this.created = created;
    }

    /** Timestamp when this response was created. */
    public Long getCreated() {
        return created;
    }

    /** The provider's job/operation identifier for async polling. */
    public String getJobId() {
        return get(KEY_JOB_ID);
    }

    public void setJobId(String jobId) {
        put(KEY_JOB_ID, jobId);
    }

    /** Current status of the video generation job. */
    public String getStatus() {
        return get(KEY_STATUS);
    }

    public void setStatus(String status) {
        put(KEY_STATUS, status);
    }

    /** Error message when the job has failed. */
    public String getErrorMessage() {
        return get(KEY_ERROR_MESSAGE);
    }

    public void setErrorMessage(String errorMessage) {
        put(KEY_ERROR_MESSAGE, errorMessage);
    }
}
