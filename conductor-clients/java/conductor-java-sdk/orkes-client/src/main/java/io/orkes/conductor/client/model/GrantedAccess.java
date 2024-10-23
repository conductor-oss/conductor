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

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class GrantedAccess {
    /**
     * Gets or Sets access
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

    private List<AccessEnum> access = null;

    private TargetRef target = null;

    public GrantedAccess access(List<AccessEnum> access) {
        this.access = access;
        return this;
    }

    public GrantedAccess addAccessItem(AccessEnum accessItem) {
        if (this.access == null) {
            this.access = new ArrayList<>();
        }
        this.access.add(accessItem);
        return this;
    }

    public List<AccessEnum> getAccess() {
        return access;
    }

    public void setAccess(List<AccessEnum> access) {
        this.access = access;
    }

    public GrantedAccess target(TargetRef target) {
        this.target = target;
        return this;
    }

    public TargetRef getTarget() {
        return target;
    }

    public void setTarget(TargetRef target) {
        this.target = target;
    }
}
