/*
 * Copyright 2022 Orkes, Inc.
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

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * The object over which access is being granted or removed
 */
@EqualsAndHashCode
@ToString
public class TargetRef {

    private String id = null;

    public enum TypeEnum {
        WORKFLOW_DEF("WORKFLOW_DEF"),
        TASK_DEF("TASK_DEF"),
        APPLICATION("APPLICATION"),
        USER("USER"),
        SECRET("SECRET_NAME"),
        TAG("TAG"),
        DOMAIN("DOMAIN");

        private final String value;

        TypeEnum(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }

        public static TypeEnum fromValue(String input) {
            for (TypeEnum b : TypeEnum.values()) {
                if (b.value.equals(input)) {
                    return b;
                }
            }
            return null;
        }
    }


    private TypeEnum type = null;

    public TargetRef id(String id) {
        this.id = id;
        return this;
    }

    /**
     * Get id
     * 
     * @return id
     **/
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TargetRef type(TypeEnum type) {
        this.type = type;
        return this;
    }

    /**
     * Get type
     * 
     * @return type
     **/
    
    public TypeEnum getType() {
        return type;
    }

    public void setType(TypeEnum type) {
        this.type = type;
    }
}
