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
package com.netflix.conductor.contribs.queue.nats;

import com.netflix.conductor.core.events.queue.Message;

/**
 * @author andrey.stelmashenko@gmail.com
 */
public class JsmMessage extends Message {
    private io.nats.client.Message jsmMsg;

    public io.nats.client.Message getJsmMsg() {
        return jsmMsg;
    }

    public void setJsmMsg(io.nats.client.Message jsmMsg) {
        this.jsmMsg = jsmMsg;
    }
}
