package org.conductoross.conductor.common.metadata.tags;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    private String key;
    private String value;

    @Deprecated(since = "11/21/23")
    private String type;

    public static Tag of(String key, String value) {
        return Tag.builder().key(key).value(value).build();
    }

    public static Tag of(String keyValue) {
        String[] kv = keyValue.split(":");
        if (kv.length < 2) { // should it be strictly 2?
            throw new IllegalArgumentException("Tag must be in the format key:value, got '" + keyValue + "'");
        }
        return Tag.builder().key(kv[0]).value(kv[1]).build();
    }

    @Override
    public String toString() {
        return String.format("%s:%s", key, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return key.equals(tag.key) && value.equals(tag.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }
}
