package com.atlassian.braid;

import java.util.Objects;

// The unique key of a id that is being requested
public class FieldKey {
    private final String value;

    public FieldKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FieldKey fieldKey = (FieldKey) o;
        return Objects.equals(value, fieldKey.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
