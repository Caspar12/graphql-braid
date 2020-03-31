package com.atlassian.braid.source;

import graphql.language.VariableReference;

public class NamespacedVariableReference extends VariableReference {
    public NamespacedVariableReference(String name) {
        super(name);
    }

    public static NamespacedVariableReference namespacedVariableReference(String name) {
        return new NamespacedVariableReference(name);
    }
}
