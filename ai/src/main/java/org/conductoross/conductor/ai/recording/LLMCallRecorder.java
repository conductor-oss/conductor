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
package org.conductoross.conductor.ai.recording;

import org.conductoross.conductor.ai.AIModel;
import org.conductoross.conductor.ai.model.ChatCompletion;
import org.springframework.ai.chat.model.ChatModel;

/** Records calls at the model boundary, before Conductor validates the returned response. */
public interface LLMCallRecorder {
    ChatModel wrap(AIModel provider, ChatCompletion input, ChatModel delegate);
}
