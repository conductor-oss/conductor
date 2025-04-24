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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Group {
    /** Gets or Sets inner */
    public enum InnerEnum {
        CREATE("CREATE"),
        READ("READ"),
        UPDATE("UPDATE"),
        DELETE("DELETE"),
        EXECUTE("EXECUTE");

        private final String value;

        InnerEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static InnerEnum fromValue(String input) {
            for (InnerEnum b : InnerEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }

    private Map<String, List<String>> defaultAccess = null;

    private String description = null;

    private String id = null;

    private List<Role> roles = null;

    public Group defaultAccess(Map<String, List<String>> defaultAccess) {
        this.defaultAccess = defaultAccess;
        return this;
    }

    public Group putDefaultAccessItem(String key, List<String> defaultAccessItem) {
        if (this.defaultAccess == null) {
            this.defaultAccess = new HashMap<>();
        }
        this.defaultAccess.put(key, defaultAccessItem);
        return this;
    }

    public Group description(String description) {
        this.description = description;
        return this;
    }

    public Group id(String id) {
        this.id = id;
        return this;
    }

    public Group roles(List<Role> roles) {
        this.roles = roles;
        return this;
    }
    
    public Group addRolesItem(Role rolesItem) {
        if (this.roles == null) {
            this.roles = new ArrayList<>();
        }
        this.roles.add(rolesItem);
        return this;
    }
}