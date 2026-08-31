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
package org.conductoross.conductor.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for engine-level enforcement of the schemas attached to definitions. */
@ConfigurationProperties("conductor.app.schema-validation")
public class SchemaValidationProperties {

    /**
     * Whether the engine validates payloads against the schemas attached to workflow and task
     * definitions.
     *
     * <p>Off by default, so upgrading a deployment changes nothing: a definition that carries a
     * schema today carries it for documentation, and no execution that succeeds now can begin
     * failing. Turning it on is how an operator adopts enforcement deliberately.
     *
     * <p>This switch is necessary but not sufficient. Each validation point is additionally gated
     * on the definition's own {@code enforceSchema} flag and on a schema actually being attached.
     */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
