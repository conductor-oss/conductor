package io.orkes.conductor.client.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.*;

@EqualsAndHashCode
@ToString
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    public Group putDefaultAccessItem(String key, List<String> defaultAccessItem) {
        if (this.defaultAccess == null) {
            this.defaultAccess = new HashMap<>();
        }
        this.defaultAccess.put(key, defaultAccessItem);
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