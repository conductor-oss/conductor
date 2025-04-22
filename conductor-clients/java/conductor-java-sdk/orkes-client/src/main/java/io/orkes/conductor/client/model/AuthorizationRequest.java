/*
 * Copyright 2022 Conductor Authors.
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
package io.orkes.conductor.client.model;

import java.util.ArrayList;
import java.util.List;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuthorizationRequest {
    /**
     * The set of access which is granted or removed
     */
    public enum AccessEnum {
        CREATE("CREATE"),
        READ("READ"),
        UPDATE("UPDATE"),
        DELETE("DELETE"),
        EXECUTE("EXECUTE");

        private final String value;

        AccessEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static AccessEnum fromValue(String input) {
            for (AccessEnum b : AccessEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }

    private List<AccessEnum> access = new ArrayList<>();

    private SubjectRef subject = null;

    private TargetRef target = null;


    public AuthorizationRequest access(List<AccessEnum> access) {
        this.access = access;
        return this;
    }

    public AuthorizationRequest addAccessItem(AccessEnum accessItem) {
        this.access.add(accessItem);
        return this;
    }

    public AuthorizationRequest subject(SubjectRef subject) {
        this.subject = subject;
        return this;
    }

    public AuthorizationRequest target(TargetRef target) {
        this.target = target;
        return this;
    }
}