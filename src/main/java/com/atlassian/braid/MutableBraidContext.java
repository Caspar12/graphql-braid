package com.atlassian.braid;

import graphql.language.Field;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MutableBraidContext<C> implements BraidContext<C> {
    private final Map<String, List<Field>> missingFieldsByType;

    @Nullable
    private final C context;

    MutableBraidContext(@Nullable C context) {
        this(null, context);
    }

    MutableBraidContext(@Nullable BraidContext braidContext, @Nullable C context) {
        HashMap<String, List<Field>> tmp = new HashMap<>();
        if (braidContext != null) {
            tmp.putAll(braidContext.getAllMissingFields());
        }
        this.missingFieldsByType = tmp;
        this.context = context;
    }

    @Override
    public void addMissingFields(String typeName, List<Field> fields) {
        this.missingFieldsByType.put(typeName, fields);
    }

    @Override
    public List<Field> getMissingFields(String typeName) {
        return missingFieldsByType.get(typeName);
    }

    @Override
    public Map<String, List<Field>> getAllMissingFields() {
        return missingFieldsByType;
    }

    @Override
    public C getContext() {
        return context;
    }
}
