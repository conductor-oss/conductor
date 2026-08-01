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
package org.conductoross.conductor.ai.agentspan.runtime.controller;

import org.conductoross.conductor.ai.agentspan.runtime.service.AgentExecutionMemoryState;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentFeedbackService;
import org.conductoross.conductor.ai.agentspan.runtime.service.AgentFeedbackState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentControllerFeedbackTest {

    @Test
    void forwardsOnlyBrowserRatingAndReasonToServerSideFeedbackService() {
        RecordingFeedbackService feedbackService = new RecordingFeedbackService();
        AgentController controller = new AgentController(null, null, feedbackService);

        AgentFeedbackState response =
                controller.setExecutionFeedback(
                        "root-execution",
                        new AgentController.AgentFeedbackRequest(
                                "positive", "The answer resolved the issue."));

        assertThat(feedbackService.executionId).isEqualTo("root-execution");
        assertThat(feedbackService.rating).isEqualTo("positive");
        assertThat(feedbackService.reason).isEqualTo("The answer resolved the issue.");
        assertThat(response.enabled()).isTrue();
    }

    @Test
    void forwardsOnlyTheExecutionIdWhenReadingMemory() {
        RecordingFeedbackService feedbackService = new RecordingFeedbackService();
        AgentController controller = new AgentController(null, null, feedbackService);

        assertThat(controller.getExecutionFeedbackMemory("root-execution"))
                .isEqualTo(new AgentExecutionMemoryState("Stored execution summary.", null, null));
        assertThat(feedbackService.memoryExecutionId).isEqualTo("root-execution");
    }

    private static final class RecordingFeedbackService extends AgentFeedbackService {
        private String executionId;
        private String rating;
        private String reason;
        private String memoryExecutionId;

        private RecordingFeedbackService() {
            super(null, null, null);
        }

        @Override
        public AgentFeedbackState set(String executionId, String rating, String reason) {
            this.executionId = executionId;
            this.rating = rating;
            this.reason = reason;
            return new AgentFeedbackState(true, rating, reason, null);
        }

        @Override
        public AgentExecutionMemoryState getMemory(String executionId) {
            this.memoryExecutionId = executionId;
            return new AgentExecutionMemoryState("Stored execution summary.", null, null);
        }
    }
}
