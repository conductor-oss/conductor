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

/**
 * Extension of {@link VideoModel} for providers that use asynchronous job submission and polling.
 *
 * <p>Most video generation providers (OpenAI Sora, Google Veo, etc.) operate asynchronously:
 *
 * <ol>
 *   <li>{@link #call(VideoPrompt)} submits the generation job and returns immediately with a job ID
 *   <li>{@link #checkStatus(String)} polls for the job's current status
 *   <li>When status is COMPLETED, the response includes the generated video data
 * </ol>
 */
public interface AsyncVideoModel extends VideoModel {

    /**
     * Check the status of an asynchronous video generation job.
     *
     * @param jobId The job identifier returned from a previous {@link #call(VideoPrompt)} response
     *     metadata
     * @return The current status and result if complete. The response metadata will contain the
     *     status (PENDING, PROCESSING, COMPLETED, FAILED).
     */
    VideoResponse checkStatus(String jobId);
}
