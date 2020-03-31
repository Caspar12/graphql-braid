package com.atlassian.braid.source;

import graphql.language.Field;

import java.util.List;

import static java.util.Collections.emptyList;

public class FieldWithContext {

    private final Field field;
    private final List<Field> missingFields;


    public FieldWithContext(Field field) {
        this(field, emptyList());
    }
    public FieldWithContext(Field field, List<Field> missingFields) {
        this.field = field;
        this.missingFields = missingFields;
    }

    public Field getField() {
        return field;
    }

    public List<Field> getMissingFields() {
        return missingFields;
    }
}
